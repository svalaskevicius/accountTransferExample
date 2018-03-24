package com.svalaskevicius.account_transfers.service

import java.util.UUID

import cats.Monad
import cats.syntax.all._

import com.svalaskevicius.account_transfers.model.Account.AccountId
import com.svalaskevicius.account_transfers.model._

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