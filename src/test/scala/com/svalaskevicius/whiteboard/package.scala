package com.svalaskevicius

import java.util.UUID

import cats.Monad
import cats.syntax.all._

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

  object Account {
    type Snapshot
  }

  sealed trait Account {
    def currentBalance: AccountReadError Either Long

    def register(accountId: AccountId): RegisterError Either List[AccountEvent]
    def debitForTransfer(accountTo: AccountId, amount: PositiveNumber): DebitError Either List[AccountEvent]
    def creditForTransfer(transactionId: UUID, amount: PositiveNumber): CreditError Either List[AccountEvent]
    def completeTransfer(transactionId: UUID): CompleteTransferError Either List[AccountEvent]
  }


  trait AggregateLoader[Aggregate, Snapshot, Event] {
    def takeSnapshot(aggregate: Aggregate): Snapshot
    def fromSnapshot(snapshot: Snapshot): Aggregate
    def applyEvent(aggregate: Aggregate, event: Event): Aggregate
  }

  trait EventStorage[F[_], Aggregate, Snapshot, Event] {

    def aggregateLoader: AggregateLoader[Aggregate, Snapshot, Event]

    /**
      * Read aggregate
      *
      * @param aggregateId
      * @return
      */
    def readAggregate[A](aggregateId: String): F[Aggregate]

    /**
      * Run a transaction for a given aggregate, store the changes, and return their result
      *
      * @param aggregateId
      * @param f
      * @tparam Err
      * @return
      */
    def runTransaction[Err](aggregateId: String)(f: Aggregate => Err Either List[Event]): F[Err Either List[Event]]
  }


  class AccountService[F[_] : Monad] (storage: EventStorage[F, Account, Account.Snapshot, AccountEvent]) {

    def currentBalance(accountId: AccountId): F[AccountReadError Either Long] =
      storage.readAggregate(accountId).map(_.currentBalance)

    def register(accountId: AccountId): F[RegisterError Either List[AccountEvent]] =
      storage.runTransaction(accountId)(_.register(accountId))

    def debitForTransfer(accountId: AccountId, accountTo: AccountId, amount: PositiveNumber): F[DebitError Either List[AccountEvent]] =
      storage.runTransaction(accountId)(_.debitForTransfer(accountTo: AccountId, amount))

    def creditForTransfer(accountId: AccountId, transactionId: UUID, amount: PositiveNumber): F[CreditError Either List[AccountEvent]] =
      storage.runTransaction(accountId)(_.creditForTransfer(transactionId, amount))

    def completeTransfer(accountId: AccountId, transactionId: UUID): F[CompleteTransferError Either List[AccountEvent]] =
      storage.runTransaction(accountId)(_.completeTransfer(transactionId))
  }



  sealed trait TransferProcessFailure

  /**
    * While in a distributed system a process would be listening for events and emitting commands instead of
    * invoking and controlling the flow directly, in this example such behaviour is a useful approximation - as
    * there is no event subscription/notification mechanism.
    *
    * @param accountService
    * @tparam F
    */
  class TransferProcess[F[_] : Monad] (accountService: AccountService[F]) {
    def apply(accountFrom: AccountId, accountTo: AccountId, amount: PositiveNumber): F[TransferProcessFailure Either Unit] = ???
  }
}