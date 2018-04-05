package com.svalaskevicius.account_transfers.usecase

import java.util.UUID

import com.svalaskevicius.account_transfers.model.Account.AccountId
import com.svalaskevicius.account_transfers.model.AccountEvent.TransferStarted
import com.svalaskevicius.account_transfers.model.AccountOperationError.AccountHasNotBeenRegistered
import com.svalaskevicius.account_transfers.model._
import com.svalaskevicius.account_transfers.service.AccountService
import com.svalaskevicius.account_transfers.usecase.TransferBetweenAccountsError._
import monix.eval.Task

sealed trait TransferBetweenAccountsError extends Throwable
object TransferBetweenAccountsError {
  case class NoTransactionIdAfterTransferStart(accountFrom: AccountId, accountTo: AccountId, amount: Long) extends Throwable
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
  */
class TransferBetweenAccounts (accountService: AccountService) {

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
  def apply(accountFrom: AccountId, accountTo: AccountId, amount: PositiveNumber): Task[Unit] = {

    lazy val request = Request(accountFrom, accountTo, amount.value)

    def findTransactionId(events: List[AccountEvent]) = Task {
      events.collectFirst {
        case TransferStarted(id, _, _) => id
      }
    }.flatMap {
      case Some(transactionId) => Task.now(transactionId)
      case None => Task.raiseError(NoTransactionIdAfterTransferStart(accountFrom, accountTo, amount.value))
    }

    def creditForTransfer(transactionId: UUID) =
      accountService.creditForTransfer(accountTo, transactionId, amount).onErrorRecoverWith {
        case err: AccountHasNotBeenRegistered =>
          accountService.refundFailedTransfer(accountFrom, transactionId).flatMap { _ =>
            Task.raiseError(err)
          }
      }

    for {
      debitEvents <- accountService.debitForTransfer(accountFrom, accountTo, amount)
      transactionId <- findTransactionId(debitEvents)
      _ <- creditForTransfer(transactionId)
      _ <- accountService.completeTransfer(accountFrom, transactionId)
    } yield ()
  }
}
