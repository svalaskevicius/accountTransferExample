package com.svalaskevicius.account_transfers.usecase

import java.util.UUID

import cats.Monad
import cats.syntax.all._
import com.svalaskevicius.account_transfers.model.Account.AccountId
import com.svalaskevicius.account_transfers.model.AccountEvent.TransferStarted
import com.svalaskevicius.account_transfers.model.{AccountEvent, PositiveNumber}
import com.svalaskevicius.account_transfers.service.AccountService

/**
  * While in a distributed system a process would be listening for events and emitting commands instead of
  * invoking and controlling the flow directly, in this example such behaviour is a useful approximation - as
  * there is no event subscription/notification mechanism.
  *
  * @param accountService
  * @tparam F
  */
class TransferBetweenAccounts[F[_]: Monad] (accountService: AccountService[F]) {

  /**
    * Debit the from account, then credit the to account and complete transaction.
    * On failure to credit - fail the transaction and refund the debited amount.
    *
    * @param accountFrom
    * @param accountTo
    * @param amount
    * @return
    */
  def apply(accountFrom: AccountId, accountTo: AccountId, amount: PositiveNumber) = {

    def completeTransaction(transactionId: UUID) = accountService.completeTransfer(accountFrom, transactionId).map {
      case Left(err) => ???
      case Right(_) => Right(())
    }

    def creditForTransfer(transactionId: UUID) = accountService.creditForTransfer(accountTo, transactionId, amount).flatMap {
      case Left(err) => ???
      case Right(_) => completeTransaction(transactionId)
    }

    def accountDebited(events: List[AccountEvent]) = findTransactionId(events) match {
      case None => ???
      case Some(transactionId) => creditForTransfer(transactionId)
    }

    accountService.debitForTransfer(accountFrom, accountTo, amount).flatMap {
      case Left(err) => ???
      case Right(events) => accountDebited(events)
    }
  }

  private def findTransactionId(events: List[AccountEvent]) = events.collectFirst {
    case TransferStarted(id, _, _) => id
  }
}
