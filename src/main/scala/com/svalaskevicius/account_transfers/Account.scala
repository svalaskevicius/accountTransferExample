package com.svalaskevicius.account_transfers

import java.util.UUID

import com.svalaskevicius.account_transfers.Account.AccountId
import com.svalaskevicius.account_transfers.AccountEvent.{Debited, Registered, TransferStarted}


sealed trait AccountReadError
object AccountReadError {
  case object AccountHasNotBeenRegistered extends AccountReadError
}

sealed trait RegisterError
object RegisterError {
  case object AccountHasAlreadyBeenRegistered extends RegisterError
}

sealed trait DebitError
object DebitError {
  case object AccountHasNotBeenRegistered extends DebitError
  case object InsufficientFunds extends DebitError
}

sealed trait CreditError
object CreditError {
  case object AccountHasNotBeenRegistered extends CreditError
}

sealed trait CompleteTransferError
object CompleteTransferError {
  case object AccountHasNotBeenRegistered extends CompleteTransferError
  case object InvalidTransactionId extends CompleteTransferError
}

sealed trait AccountEvent
object AccountEvent {
  case class Registered(id: AccountId) extends AccountEvent
  case class TransferStarted(transactionId: UUID, accountTo: AccountId, amount: PositiveNumber) extends AccountEvent
  case class Debited(transactionId: UUID, amount: PositiveNumber) extends AccountEvent
  case class Credited(transactionId: UUID, amount: PositiveNumber) extends AccountEvent
  case class TransferCompleted(transactionId: UUID, accountTo: AccountId, amount: PositiveNumber) extends AccountEvent
}

object Account {
  type AccountId = String
  type Snapshot

}

sealed trait Account {
  def currentBalance: AccountReadError Either Long

  def register(accountId: AccountId): RegisterError Either List[AccountEvent]
  def debitForTransfer(accountTo: AccountId, amount: PositiveNumber): DebitError Either List[AccountEvent]
  def creditForTransfer(transactionId: UUID, amount: PositiveNumber): CreditError Either List[AccountEvent]
  def completeTransfer(transactionId: UUID): CompleteTransferError Either List[AccountEvent]
  def revertFailedTransfer(transactionId: UUID): CompleteTransferError Either List[AccountEvent]
}

object UnregisteredAccount extends Account {
  override def currentBalance: AccountReadError Either Long =
    Left(AccountReadError.AccountHasNotBeenRegistered)

  override def register(accountId: AccountId): RegisterError Either List[AccountEvent] =
    Right(List(Registered(accountId)))

  override def debitForTransfer(accountTo: AccountId, amount: PositiveNumber): DebitError Either List[AccountEvent] =
    Left(DebitError.AccountHasNotBeenRegistered)

  override def creditForTransfer(transactionId: UUID, amount: PositiveNumber): CreditError Either List[AccountEvent] =
    Left(CreditError.AccountHasNotBeenRegistered)

  override def completeTransfer(transactionId: UUID): CompleteTransferError Either List[AccountEvent] =
    Left(CompleteTransferError.AccountHasNotBeenRegistered)

  override def revertFailedTransfer(transactionId: UUID): CompleteTransferError Either List[AccountEvent] =
    Left(CompleteTransferError.AccountHasNotBeenRegistered)
}

final class RegisteredAccount(id: AccountId, balance: Long) extends Account {
  override def currentBalance: AccountReadError Either Long =
    Right(balance)

  override def register(accountId: AccountId): RegisterError Either List[AccountEvent] =
    Left(RegisterError.AccountHasAlreadyBeenRegistered)

  override def debitForTransfer(accountTo: AccountId, amount: PositiveNumber): DebitError Either List[AccountEvent] =
    if (amount.value > balance) {
      Left(DebitError.InsufficientFunds)
    } else {
      val newTransactionId = UUID.randomUUID()
      Right(List(TransferStarted(newTransactionId, accountTo, amount), Debited(newTransactionId, amount)))
    }

  override def creditForTransfer(transactionId: UUID, amount: PositiveNumber): CreditError Either List[AccountEvent] = ???
  override def completeTransfer(transactionId: UUID): CompleteTransferError Either List[AccountEvent] = ???
  override def revertFailedTransfer(transactionId: UUID): CompleteTransferError Either List[AccountEvent] = ???
}