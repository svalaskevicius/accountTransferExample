package com.svalaskevicius.account_transfers

import java.util.UUID

import com.svalaskevicius.account_transfers.Account.AccountId
import com.svalaskevicius.account_transfers.AccountEvent._


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
  case class TransferFailed(transactionId: UUID, accountTo: AccountId, amount: PositiveNumber) extends AccountEvent
}

object Account {
  type AccountId = String

  def applyEvent(account: Account, event: AccountEvent): Account = (account, event) match {
    case (acc: RegisteredAccount, tr: TransferStarted) => acc.copy(currentTransfers = tr :: acc.currentTransfers)
    case (acc: RegisteredAccount, Debited(_, amount)) => acc.copy(balance = acc.balance - amount.value)
    case (acc: RegisteredAccount, Credited(_, amount)) => acc.copy(balance = acc.balance + amount.value)
    case (acc: RegisteredAccount, tr: TransferCompleted) => acc.copy(currentTransfers = acc.currentTransfers.filterNot(_.transactionId == tr.transactionId))
    case (acc: RegisteredAccount, tr: TransferFailed) => acc.copy(
      currentTransfers = acc.currentTransfers.filterNot(_.transactionId == tr.transactionId),
      balance = acc.balance + tr.amount.value
    )
  }

  def applyEvents(account: Account, events: List[AccountEvent]): Account =
    events.foldLeft(account)(applyEvent)
}

sealed trait Account {
  def currentBalance: AccountReadError Either Long

  def register(accountId: AccountId): RegisterError Either List[AccountEvent]
  def debitForTransfer(accountTo: AccountId, amount: PositiveNumber): DebitError Either List[AccountEvent]
  def creditForTransfer(transactionId: UUID, amount: PositiveNumber): CreditError Either List[AccountEvent]
  def completeTransfer(transactionId: UUID): CompleteTransferError Either List[AccountEvent]
  def revertFailedTransfer(transactionId: UUID): CompleteTransferError Either List[AccountEvent]
}

case object UnregisteredAccount extends Account {
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


final case class RegisteredAccount(id: AccountId, balance: Long, currentTransfers: List[TransferStarted]) extends Account {
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

  override def creditForTransfer(transactionId: UUID, amount: PositiveNumber): CreditError Either List[AccountEvent] =
    Right(List(Credited(transactionId, amount)))

  override def completeTransfer(transactionId: UUID): CompleteTransferError Either List[AccountEvent] =
    currentTransfers.find(_.transactionId == transactionId) match {
      case Some(transfer) => Right(List(TransferCompleted(transactionId, transfer.accountTo, transfer.amount)))
      case None => Left(CompleteTransferError.InvalidTransactionId)
    }

  override def revertFailedTransfer(transactionId: UUID): CompleteTransferError Either List[AccountEvent] =
    currentTransfers.find(_.transactionId == transactionId) match {
      case Some(transfer) => Right(List(TransferFailed(transactionId, transfer.accountTo, transfer.amount)))
      case None => Left(CompleteTransferError.InvalidTransactionId)
    }

}