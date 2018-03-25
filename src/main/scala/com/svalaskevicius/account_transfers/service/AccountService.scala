package com.svalaskevicius.account_transfers.service

import java.util.UUID

import cats.Monad
import cats.syntax.all._

import com.svalaskevicius.account_transfers.model.Account.AccountId
import com.svalaskevicius.account_transfers.model._

/**
  * Wraps account aggregate with calls to the provided `EventStorage`.
  *
  * Account service reads/updates Aggregates on its operations.
  *
  * @param storage EventStorage backend
  * @param ev$1    (implicit) proof that the `F` type is monadic (i.e. we're able to flatMap / map and it has a unit value)
  * @tparam F      Wrapper type (see "Tagless Final" pattern). Examples could be a `Future`, `Task` or even `Id`
  */
class AccountService[F[_] : Monad] (storage: EventStorage[F, Account, AccountEvent]) {

  def currentBalance(accountId: AccountId): F[AccountReadError Either Long] =
    storage.readAggregate(accountId).map(_.currentBalance)

  def register(accountId: AccountId, initialBalance: Long): F[RegisterError Either List[AccountEvent]] =
    storage.runTransaction(accountId)(_.register(accountId, initialBalance))

  def debitForTransfer(accountId: AccountId, accountTo: AccountId, amount: PositiveNumber): F[DebitError Either List[AccountEvent]] =
    storage.runTransaction(accountId)(_.debitForTransfer(accountTo: AccountId, amount))

  def creditForTransfer(accountId: AccountId, transactionId: UUID, amount: PositiveNumber): F[CreditError Either List[AccountEvent]] =
    storage.runTransaction(accountId)(_.creditForTransfer(transactionId, amount))

  def completeTransfer(accountId: AccountId, transactionId: UUID): F[CompleteTransferError Either List[AccountEvent]] =
    storage.runTransaction(accountId)(_.completeTransfer(transactionId))

  def refundFailedTransfer(accountId: AccountId, transactionId: UUID): F[CompleteTransferError Either List[AccountEvent]] =
    storage.runTransaction(accountId)(_.refundFailedTransfer(transactionId))
}
