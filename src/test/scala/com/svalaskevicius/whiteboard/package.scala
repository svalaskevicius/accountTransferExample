package com.svalaskevicius

import java.util.UUID

package object whiteboard {

  type AccountId = String

  trait PositiveNumber

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

  sealed trait Account {
    def currentBalance: AccountReadError Either Long

    def register(accountId: AccountId): RegisterError Either List[AccountEvent]
    def debitForTransfer(accountTo: AccountId, amount: PositiveNumber): DebitError Either List[AccountEvent]
    def creditForTransfer(transactionId: UUID, amount: PositiveNumber): CreditError Either List[AccountEvent]
    def completeTransfer(transactionId: UUID): CompleteTransferError Either List[AccountEvent]
  }
}