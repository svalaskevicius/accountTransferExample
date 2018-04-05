package com.svalaskevicius.account_transfers.model

import java.util.UUID

import com.svalaskevicius.account_transfers.model.Account.AccountId
import com.svalaskevicius.account_transfers.model.AccountEvent._


sealed trait AccountReadError extends Throwable
object AccountReadError {
  case class AccountHasNotBeenRegistered() extends AccountReadError
}

sealed trait RegisterError extends Throwable
object RegisterError {
  case class AccountHasAlreadyBeenRegistered() extends RegisterError
}

sealed trait DebitError extends Throwable
object DebitError {
  case class AccountHasNotBeenRegistered() extends DebitError
  case class InsufficientFunds() extends DebitError
}

sealed trait CreditError extends Throwable
object CreditError {
  case class AccountHasNotBeenRegistered() extends CreditError
}

sealed trait CompleteTransferError extends Throwable
object CompleteTransferError {
  case class AccountHasNotBeenRegistered() extends CompleteTransferError
  case class InvalidTransactionId() extends CompleteTransferError
}

sealed trait AccountEvent
object AccountEvent {
  case class Registered(id: AccountId, initialBalance: Long) extends AccountEvent
  case class TransferStarted(transactionId: UUID, accountTo: AccountId, amount: PositiveNumber) extends AccountEvent
  case class Debited(transactionId: UUID, amount: PositiveNumber) extends AccountEvent
  case class Credited(transactionId: UUID, amount: PositiveNumber) extends AccountEvent
  case class TransferCompleted(transactionId: UUID, accountTo: AccountId, amount: PositiveNumber) extends AccountEvent
  case class TransferFailed(transactionId: UUID, accountTo: AccountId, amount: PositiveNumber) extends AccountEvent
}

object Account {
  type AccountId = String

  val loader = new AggregateLoader[Account, AccountEvent] {
    def empty: Account = UnregisteredAccount
    def applyEvent(account: Account, event: AccountEvent): Account = (account, event) match {
      case (UnregisteredAccount, Registered(id, initialBalance)) => RegisteredAccount(id, initialBalance, List.empty)
      case (acc: RegisteredAccount, tr: TransferStarted) => acc.copy(currentTransfers = tr :: acc.currentTransfers)
      case (acc: RegisteredAccount, Debited(_, amount)) => acc.copy(balance = acc.balance - amount.value)
      case (acc: RegisteredAccount, Credited(_, amount)) => acc.copy(balance = acc.balance + amount.value)
      case (acc: RegisteredAccount, tr: TransferCompleted) => acc.copy(currentTransfers = acc.currentTransfers.filterNot(_.transactionId == tr.transactionId))
      case (acc: RegisteredAccount, tr: TransferFailed) => acc.copy(
        currentTransfers = acc.currentTransfers.filterNot(_.transactionId == tr.transactionId),
        balance = acc.balance + tr.amount.value
      )
      case (acc: RegisteredAccount, Registered(_, _)) => throw new RuntimeException(s"Unexpected registration event for account ${acc.id}")
      case (UnregisteredAccount, event) => throw new RuntimeException(s"Unexpected event ($event) for an unregistered account")
    }
  }
}

/**
  * Account aggregate.
  */
sealed trait Account {
  def currentBalance: AccountReadError Either Long

  def register(accountId: AccountId, initialBalance: Long): RegisterError Either List[AccountEvent]
  def debitForTransfer(accountTo: AccountId, amount: PositiveNumber): DebitError Either List[AccountEvent]
  def creditForTransfer(transactionId: UUID, amount: PositiveNumber): CreditError Either List[AccountEvent]
  def completeTransfer(transactionId: UUID): CompleteTransferError Either List[AccountEvent]
  def refundFailedTransfer(transactionId: UUID): CompleteTransferError Either List[AccountEvent]
}


case object UnregisteredAccount extends Account {
  override def currentBalance: AccountReadError Either Long =
    Left(AccountReadError.AccountHasNotBeenRegistered())

  override def register(accountId: AccountId, initialBalance: Long): RegisterError Either List[AccountEvent] =
    Right(List(Registered(accountId, initialBalance)))

  override def debitForTransfer(accountTo: AccountId, amount: PositiveNumber): DebitError Either List[AccountEvent] =
    Left(DebitError.AccountHasNotBeenRegistered())

  override def creditForTransfer(transactionId: UUID, amount: PositiveNumber): CreditError Either List[AccountEvent] =
    Left(CreditError.AccountHasNotBeenRegistered())

  override def completeTransfer(transactionId: UUID): CompleteTransferError Either List[AccountEvent] =
    Left(CompleteTransferError.AccountHasNotBeenRegistered())

  override def refundFailedTransfer(transactionId: UUID): CompleteTransferError Either List[AccountEvent] =
    Left(CompleteTransferError.AccountHasNotBeenRegistered())
}


final case class RegisteredAccount(id: AccountId, balance: Long, currentTransfers: List[TransferStarted]) extends Account {
  override def currentBalance: AccountReadError Either Long =
    Right(balance)

  override def register(accountId: AccountId, initialBalance: Long): RegisterError Either List[AccountEvent] =
    Left(RegisterError.AccountHasAlreadyBeenRegistered())

  override def debitForTransfer(accountTo: AccountId, amount: PositiveNumber): DebitError Either List[AccountEvent] =
    if (amount.value > balance) {
      Left(DebitError.InsufficientFunds())
    } else {
      val newTransactionId = UUID.randomUUID()
      Right(List(TransferStarted(newTransactionId, accountTo, amount), Debited(newTransactionId, amount)))
    }

  override def creditForTransfer(transactionId: UUID, amount: PositiveNumber): CreditError Either List[AccountEvent] =
    Right(List(Credited(transactionId, amount)))

  override def completeTransfer(transactionId: UUID): CompleteTransferError Either List[AccountEvent] =
    currentTransfers.find(_.transactionId == transactionId) match {
      case Some(transfer) => Right(List(TransferCompleted(transactionId, transfer.accountTo, transfer.amount)))
      case None => Left(CompleteTransferError.InvalidTransactionId())
    }

  override def refundFailedTransfer(transactionId: UUID): CompleteTransferError Either List[AccountEvent] =
    currentTransfers.find(_.transactionId == transactionId) match {
      case Some(transfer) => Right(List(TransferFailed(transactionId, transfer.accountTo, transfer.amount)))
      case None => Left(CompleteTransferError.InvalidTransactionId())
    }
}