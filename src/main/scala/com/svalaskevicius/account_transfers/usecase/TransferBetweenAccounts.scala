package com.svalaskevicius.account_transfers.usecase

import java.util.UUID

import cats.Monad
import cats.syntax.all._
import com.svalaskevicius.account_transfers.model.Account.AccountId
import com.svalaskevicius.account_transfers.model.AccountEvent.TransferStarted
import com.svalaskevicius.account_transfers.model._
import com.svalaskevicius.account_transfers.service.AccountService
import com.svalaskevicius.account_transfers.usecase.TransferBetweenAccountsError._

sealed trait TransferBetweenAccountsError
object TransferBetweenAccountsError {
  case class DebitFailed(accountFrom: AccountId, amount: Long, debitError: DebitError) extends TransferBetweenAccountsError
  case class CreditFailed(accountTo: AccountId, amount: Long, creditError: CreditError, transactionId: UUID) extends TransferBetweenAccountsError
  case class NoTransactionIdAfterTransferStart(accountFrom: AccountId, accountTo: AccountId, amount: Long) extends TransferBetweenAccountsError
  case class FailedToCompleteTransferAfterDebitAndCredit(accountFrom: AccountId, accountTo: AccountId, amount: Long, transactionId: UUID, completeTransferError: CompleteTransferError) extends TransferBetweenAccountsError
  case class FailedToRefundTransferAfterCreditFailure(accountFrom: AccountId, accountTo: AccountId, amount: Long, transactionId: UUID, creditError: CreditError, completeTransferError: CompleteTransferError) extends TransferBetweenAccountsError
}

/**
  * While in a distributed system a process would be listening for events and emitting commands instead of
  * invoking and controlling the flow directly, in this example such behaviour is a useful approximation - as
  * there is no event subscription/notification mechanism.
  *
  * @param accountService
  * @tparam F
  */
class TransferBetweenAccounts[F[_]] (accountService: AccountService[F])(implicit fMonad: Monad[F]) {

  /**
    * Debit the from account, then credit the to account and complete transaction.
    * On failure to credit - fail the transaction and refund the debited amount.
    *
    * @param accountFrom
    * @param accountTo
    * @param amount
    * @return
    */
  def apply(accountFrom: AccountId, accountTo: AccountId, amount: PositiveNumber): F[TransferBetweenAccountsError Either Unit] = {

    def completeTransaction(transactionId: UUID): F[TransferBetweenAccountsError Either Unit] =
      accountService.completeTransfer(accountFrom, transactionId).map {
        case Left(err) => Left(FailedToCompleteTransferAfterDebitAndCredit(accountFrom, accountTo, amount.value, transactionId, err))
        case Right(_) => Right(())
      }

    def refundFailedTransfer(transactionId: UUID, creditError: CreditError): F[TransferBetweenAccountsError Either Unit] =
      accountService.refundFailedTransfer(accountFrom, transactionId).flatMap {
        case Left(err) => processFailed(FailedToRefundTransferAfterCreditFailure(accountFrom, accountTo, amount.value, transactionId, creditError, err))
        case Right(_) => processFailed(CreditFailed(accountTo, amount.value, creditError, transactionId))
      }

    def creditForTransfer(transactionId: UUID): F[TransferBetweenAccountsError Either Unit] =
      accountService.creditForTransfer(accountTo, transactionId, amount).flatMap {
        case Left(err) => refundFailedTransfer(transactionId, err)
        case Right(_) => completeTransaction(transactionId)
      }

    def accountDebited(events: List[AccountEvent]): F[TransferBetweenAccountsError Either Unit] =
      findTransactionId(events) match {
        case None => processFailed(NoTransactionIdAfterTransferStart(accountFrom, accountTo, amount.value))
        case Some(transactionId) => creditForTransfer(transactionId)
      }

    accountService.debitForTransfer(accountFrom, accountTo, amount).flatMap {
      case Left(err) => processFailed(DebitFailed(accountFrom, amount.value, err))
      case Right(events) => accountDebited(events)
    }
  }

  private def processFailed(err: TransferBetweenAccountsError): F[TransferBetweenAccountsError Either Unit] =
    fMonad.pure(Left(err))

  private def findTransactionId(events: List[AccountEvent]) = events.collectFirst {
    case TransferStarted(id, _, _) => id
  }
}
