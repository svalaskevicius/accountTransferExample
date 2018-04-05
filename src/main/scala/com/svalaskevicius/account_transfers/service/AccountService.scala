package com.svalaskevicius.account_transfers.service

import java.util.UUID

import com.svalaskevicius.account_transfers.model.Account.AccountId
import com.svalaskevicius.account_transfers.model._
import monix.eval.Task

/**
  * Wraps account aggregate with calls to the provided `EventStorage`.
  *
  * Account service reads/updates Aggregates on its operations.
  *
  * @param storage EventStorage backend
  */
class AccountService (storage: EventStorage[Account, AccountEvent]) {

  def currentBalance(accountId: AccountId): Task[AccountReadError Either Long] =
    storage.readOperation(accountId)(_.currentBalance)

  def register(accountId: AccountId, initialBalance: Long): Task[RegisterError Either List[AccountEvent]] =
    storage.runTransaction(accountId)(_.register(accountId, initialBalance))

  def debitForTransfer(accountId: AccountId, accountTo: AccountId, amount: PositiveNumber): Task[DebitError Either List[AccountEvent]] =
    storage.runTransaction(accountId)(_.debitForTransfer(accountTo: AccountId, amount))

  def creditForTransfer(accountId: AccountId, transactionId: UUID, amount: PositiveNumber): Task[CreditError Either List[AccountEvent]] =
    storage.runTransaction(accountId)(_.creditForTransfer(transactionId, amount))

  def completeTransfer(accountId: AccountId, transactionId: UUID): Task[CompleteTransferError Either List[AccountEvent]] =
    storage.runTransaction(accountId)(_.completeTransfer(transactionId))

  def refundFailedTransfer(accountId: AccountId, transactionId: UUID): Task[CompleteTransferError Either List[AccountEvent]] =
    storage.runTransaction(accountId)(_.refundFailedTransfer(transactionId))
}
