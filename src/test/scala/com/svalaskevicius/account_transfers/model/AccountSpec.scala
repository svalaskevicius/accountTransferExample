package com.svalaskevicius.account_transfers.model

import java.util.UUID

import com.svalaskevicius.account_transfers.model.Account.AccountId
import com.svalaskevicius.account_transfers.model.AccountEvent._
import org.scalatest.{FlatSpec, Matchers}

class AccountSpec extends FlatSpec with Matchers {
  "An unregistered account" should "be allowed to register" in {
    UnregisteredAccount.register("accountId", 9) should be (Right(List(Registered("accountId", 9))))
    Account.loader.applyEvent(UnregisteredAccount, Registered("accountId", 9)) should matchPattern {
      case RegisteredAccount("accountId", 9, Nil) =>
    }
  }

  it should "fail to return balance" in {
    UnregisteredAccount.currentBalance should be (Left(AccountReadError.AccountHasNotBeenRegistered))
  }

  it should "fail to debit" in {
    UnregisteredAccount.debitForTransfer("toAcc", PositiveNumber(1).get) should be (Left(DebitError.AccountHasNotBeenRegistered))
  }

  it should "fail to credit" in {
    UnregisteredAccount.creditForTransfer(UUID.randomUUID(), PositiveNumber(1).get) should be (Left(CreditError.AccountHasNotBeenRegistered))
  }

  it should "fail to complete transfer" in {
    UnregisteredAccount.completeTransfer(UUID.randomUUID()) should be (Left(CompleteTransferError.AccountHasNotBeenRegistered))
  }

  it should "fail to revert failed transfer" in {
    UnregisteredAccount.revertFailedTransfer(UUID.randomUUID()) should be(Left(CompleteTransferError.AccountHasNotBeenRegistered))
  }

  "A registered account" should "not be allowed to register again" in {
    accountWithBalance("id", 0).register("id2", 12) should be(Left(RegisterError.AccountHasAlreadyBeenRegistered))
  }

  it should "return current account balance" in {
    accountWithBalance("id", 999).currentBalance should be(Right(999))
  }

  it should "allow debit operation" in {
    val account = accountWithBalance("id", 999)
    val result = account.debitForTransfer("accTo", PositiveNumber(999).get)
    result.isRight should be(true)
    val events = result.getOrElse(List.empty)
    events should matchPattern {
      case TransferStarted(transactionId1, accountTo, amount1) ::
        Debited(transactionId2, amount2) ::
        Nil if transactionId1 == transactionId2 &&
        accountTo == "accTo" &&
        amount1.value == 999 &&
        amount2.value == 999 =>
    }

    applyEvents(account, events).currentBalance should be(Right(0))
  }

  it should "fail to debit if insufficient funds" in {
    accountWithBalance("id", 999).debitForTransfer("accTo", PositiveNumber(1000).get) should be(Left(DebitError.InsufficientFunds))
  }

  it should "allow to be credited" in {
    val account = accountWithBalance("id", 999)
    val transactionId = UUID.randomUUID()
    val result = account.creditForTransfer(transactionId, PositiveNumber(1000).get)
    result should be(Right(List(Credited(transactionId, PositiveNumber(1000).get))))

    applyEvents(account, result.getOrElse(List.empty)).currentBalance should be(Right(1999))
  }

  it should "fail to complete transaction for unknown transaction id" in {
    val transactionId = UUID.randomUUID()
    accountWithBalance("id", 999).completeTransfer(transactionId) should be(Left(CompleteTransferError.InvalidTransactionId))
  }

  it should "allow to complete valid transaction once" in {
    val account1 = accountWithBalance("id", 999)
    val transactionId = UUID.randomUUID()
    val account2 = Account.loader.applyEvent(account1, TransferStarted(transactionId, "accTo", PositiveNumber(999).get))
    val result = account2.completeTransfer(transactionId)
    result should be(Right(List(TransferCompleted(transactionId, "accTo", PositiveNumber(999).get))))

    applyEvents(account2, result.getOrElse(List.empty)).completeTransfer(transactionId) should be(Left(CompleteTransferError.InvalidTransactionId))
  }

  it should "fail to revert failed transaction for unknown transaction id" in {
    val transactionId = UUID.randomUUID()
    accountWithBalance("id", 999).revertFailedTransfer(transactionId) should be(Left(CompleteTransferError.InvalidTransactionId))
  }

  it should "allow to revert failed valid transaction" in {
    val account1 = accountWithBalance("id", 999)
    val transactionId = UUID.randomUUID()
    val account2 = applyEvents(account1, List(
      TransferStarted(transactionId, "accTo", PositiveNumber(999).get),
      Debited(transactionId, PositiveNumber(999).get)
    ))
    val result = account2.revertFailedTransfer(transactionId)
    result should be(Right(List(TransferFailed(transactionId, "accTo", PositiveNumber(999).get))))

    val account3 = applyEvents(account2, result.getOrElse(List.empty))
    account3.currentBalance should be(Right(999))
    account3.revertFailedTransfer(transactionId) should be(Left(CompleteTransferError.InvalidTransactionId))
  }

  it should "throw when receiving registered event for a registered account" in {
    a [RuntimeException] should be thrownBy {
      Account.loader.applyEvent(accountWithBalance("id", 0), Registered("id2", 1))
    }
  }

  it should "throw when receiving any non registered event for an unregistered account" in {
    val uuid = UUID.randomUUID()
    a [RuntimeException] should be thrownBy {
      Account.loader.applyEvent(UnregisteredAccount, TransferStarted(uuid, "to", PositiveNumber(1).get))
    }
    a [RuntimeException] should be thrownBy {
      Account.loader.applyEvent(UnregisteredAccount, Debited(uuid, PositiveNumber(1).get))
    }
    a [RuntimeException] should be thrownBy {
      Account.loader.applyEvent(UnregisteredAccount, Credited(uuid, PositiveNumber(1).get))
    }
    a [RuntimeException] should be thrownBy {
      Account.loader.applyEvent(UnregisteredAccount, TransferCompleted(uuid, "to", PositiveNumber(1).get))
    }
    a [RuntimeException] should be thrownBy {
      Account.loader.applyEvent(UnregisteredAccount, TransferFailed(uuid, "to", PositiveNumber(1).get))
    }
  }

  private def accountWithBalance(id: AccountId, balance: Long): Account = RegisteredAccount(id, balance, List.empty)

  private def applyEvents(account: Account, events: List[AccountEvent]): Account =
    events.foldLeft(account)(Account.loader.applyEvent)

}
