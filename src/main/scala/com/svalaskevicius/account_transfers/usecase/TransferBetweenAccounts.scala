package com.svalaskevicius.account_transfers.usecase

import java.util.UUID

import cats.Monad
import cats.syntax.all._
import com.svalaskevicius.account_transfers.model.Account.AccountId
import com.svalaskevicius.account_transfers.model.AccountEvent.TransferStarted
import com.svalaskevicius.account_transfers.model._
import com.svalaskevicius.account_transfers.service.AccountService
import com.svalaskevicius.account_transfers.usecase.TransferBetweenAccountsError._
import org.log4s.getLogger

sealed trait TransferBetweenAccountsError
object TransferBetweenAccountsError {
  case class DebitFailed(accountFrom: AccountId, amount: Long, debitError: DebitError) extends TransferBetweenAccountsError
  case class CreditFailed(accountTo: AccountId, amount: Long, creditError: CreditError, transactionId: UUID) extends TransferBetweenAccountsError
  case class NoTransactionIdAfterTransferStart(accountFrom: AccountId, accountTo: AccountId, amount: Long) extends TransferBetweenAccountsError
  case class FailedToCompleteTransferAfterDebitAndCredit(accountFrom: AccountId, accountTo: AccountId, amount: Long, transactionId: UUID, completeTransferError: CompleteTransferError) extends TransferBetweenAccountsError
  case class FailedToRefundTransferAfterCreditFailure(accountFrom: AccountId, accountTo: AccountId, amount: Long, transactionId: UUID, creditError: CreditError, completeTransferError: CompleteTransferError) extends TransferBetweenAccountsError
}

/**
  * Execute a transfer money between two accounts operation.
  *
  * While in a distributed system a process would be listening for events and emitting commands instead of
  * invoking and controlling the flow directly, in this example a specific usecase class is a useful, and much
  * simpler approximation. A downside of this approach is its (non-)resilience to system failures - e.g. if the
  * processing node halts in the middle of the transaction, the whole process currently stops without means to
  * resume it. Of course, even if this process fails, because we're storing all domain events, the account operations
  * can be reconciled by reviewing the event history.
  *
  * @param accountService
  * @tparam F             Wrapper type (see "Tagless Final" pattern). Examples could be a `Future`, `Task` or even `Id`
  */
class TransferBetweenAccounts[F[_]] (accountService: AccountService[F])(implicit fMonad: Monad[F]) {

  private val logger = getLogger

  private case class Request(accountFrom: AccountId, accountTo: AccountId, amount: Long)

  /**
    * Execute transfer operation.
    *
    * 1. Debit the `amount` from `accountFrom`
    * 2. Credit the `amount` to the account `accountTo`
    * 3. Complete the transaction
    *
    * On failure to credit - fail the transaction and refund the debited amount.
    * On any other failure - return the failure for manual reconciliation.
    *
    * @param accountFrom
    * @param accountTo
    * @param amount
    * @return
    */
  def apply(accountFrom: AccountId, accountTo: AccountId, amount: PositiveNumber): F[TransferBetweenAccountsError Either Unit] = {

    lazy val request = Request(accountFrom, accountTo, amount.value)

    def completeTransaction(transactionId: UUID): F[TransferBetweenAccountsError Either Unit] =
      accountService.completeTransfer(accountFrom, transactionId).map {
        case Left(err) => logAndReturnFailure(request, FailedToCompleteTransferAfterDebitAndCredit(accountFrom, accountTo, amount.value, transactionId, err))
        case Right(_) => Right(())
      }

    def refundFailedTransfer(transactionId: UUID, creditError: CreditError): F[TransferBetweenAccountsError Either Unit] =
      accountService.refundFailedTransfer(accountFrom, transactionId).flatMap {
        case Left(err) => processFailed(request, FailedToRefundTransferAfterCreditFailure(accountFrom, accountTo, amount.value, transactionId, creditError, err))
        case Right(_) => processFailed(request, CreditFailed(accountTo, amount.value, creditError, transactionId))
      }

    def creditForTransfer(transactionId: UUID): F[TransferBetweenAccountsError Either Unit] =
      accountService.creditForTransfer(accountTo, transactionId, amount).flatMap {
        case Left(err) => refundFailedTransfer(transactionId, err)
        case Right(_) => completeTransaction(transactionId)
      }

    def accountDebited(events: List[AccountEvent]): F[TransferBetweenAccountsError Either Unit] =
      findTransactionId(events) match {
        case None => processFailed(request, NoTransactionIdAfterTransferStart(accountFrom, accountTo, amount.value))
        case Some(transactionId) => creditForTransfer(transactionId)
      }

    accountService.debitForTransfer(accountFrom, accountTo, amount).flatMap {
      case Left(err) => processFailed(request, DebitFailed(accountFrom, amount.value, err))
      case Right(events) => accountDebited(events)
    }
  }

  private def logAndReturnFailure(request: Request, err: TransferBetweenAccountsError): TransferBetweenAccountsError Either Unit = {
    logger.error(s"Failed transfer for $request: $err")
    Left(err)
  }

  private def processFailed(request: Request, err: TransferBetweenAccountsError): F[TransferBetweenAccountsError Either Unit] =
    fMonad.pure(logAndReturnFailure(request, err))

  private def findTransactionId(events: List[AccountEvent]) = events.collectFirst {
    case TransferStarted(id, _, _) => id
  }
}
