package com.svalaskevicius.account_transfers.usecase

import java.util.UUID

import cats.Monad
import cats.syntax.all._
import com.svalaskevicius.account_transfers.model.Account.AccountId
import com.svalaskevicius.account_transfers.model.AccountEvent.TransferStarted
import com.svalaskevicius.account_transfers.model.{AccountEvent, CreditError, DebitError, PositiveNumber}
import com.svalaskevicius.account_transfers.service.AccountService
import com.svalaskevicius.account_transfers.usecase.TransferBetweenAccountsError.{CreditFailed, DebitFailed}

sealed trait TransferBetweenAccountsError
object TransferBetweenAccountsError {
  case class DebitFailed(debitError: DebitError) extends TransferBetweenAccountsError
  case class CreditFailed(creditError: CreditError) extends TransferBetweenAccountsError
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
        case Left(err) => ???
        case Right(_) => Right(())
      }

    def refundFailedTransfer(transactionId: UUID, creditError: CreditError): F[TransferBetweenAccountsError Either Unit] =
      accountService.refundFailedTransfer(accountFrom, transactionId).flatMap {
        case Left(err) => ???
        case Right(_) => processFailed(CreditFailed(creditError))
      }

    def creditForTransfer(transactionId: UUID): F[TransferBetweenAccountsError Either Unit] =
      accountService.creditForTransfer(accountTo, transactionId, amount).flatMap {
        case Left(err) => refundFailedTransfer(transactionId, err)
        case Right(_) => completeTransaction(transactionId)
      }

    def accountDebited(events: List[AccountEvent]): F[TransferBetweenAccountsError Either Unit] =
      findTransactionId(events) match {
        case None => ???
        case Some(transactionId) => creditForTransfer(transactionId)
      }

    accountService.debitForTransfer(accountFrom, accountTo, amount).flatMap {
      case Left(err) => processFailed(DebitFailed(err))
      case Right(events) => accountDebited(events)
    }
  }

  private def processFailed(err: TransferBetweenAccountsError): F[TransferBetweenAccountsError Either Unit] =
    fMonad.pure(Left(err))

  private def findTransactionId(events: List[AccountEvent]) = events.collectFirst {
    case TransferStarted(id, _, _) => id
  }
}
