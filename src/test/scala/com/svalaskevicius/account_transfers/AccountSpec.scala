package com.svalaskevicius.account_transfers

import java.util.UUID

import com.svalaskevicius.account_transfers.Account.AccountId
import com.svalaskevicius.account_transfers.AccountEvent._
import org.scalatest.{FlatSpec, Matchers}

class AccountSpec extends FlatSpec with Matchers {
  "An unregistered account" should "be allowed to register" in {
    UnregisteredAccount.register("accountId") should be (Right(List(Registered("accountId"))))
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
    accountWithBalance("id", 0).register("id2") should be(Left(RegisterError.AccountHasAlreadyBeenRegistered))
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

    events.foldLeft(account)(Account.applyEvent).currentBalance should be(Right(0))
  }

  it should "fail to debit if insufficient funds" in {
    accountWithBalance("id", 999).debitForTransfer("accTo", PositiveNumber(1000).get) should be(Left(DebitError.InsufficientFunds))
  }

  it should "allow to be credited" in {
    val transactionId = UUID.randomUUID()
    accountWithBalance("id", 999).creditForTransfer(transactionId, PositiveNumber(1000).get) should be(Right(List(Credited(transactionId, PositiveNumber(1000).get))))
  }

  it should "fail to complete transaction for unknown transaction id" in {
    val transactionId = UUID.randomUUID()
    accountWithBalance("id", 999).completeTransfer(transactionId) should be(Left(CompleteTransferError.InvalidTransactionId))
  }

  it should "allow to complete valid transaction" in {
    val transactionId = UUID.randomUUID()
    val account = Account.applyEvent(accountWithBalance("id", 999), TransferStarted(transactionId, "accTo", PositiveNumber(999).get))
    account.completeTransfer(transactionId) should be(Right(List(TransferCompleted(transactionId, "accTo", PositiveNumber(999).get))))
  }

  it should "fail to revert failed transaction for unknown transaction id" in {
    val transactionId = UUID.randomUUID()
    accountWithBalance("id", 999).revertFailedTransfer(transactionId) should be(Left(CompleteTransferError.InvalidTransactionId))
  }

  it should "allow to revert failed valid transaction" in {
    val transactionId = UUID.randomUUID()
    val account = Account.applyEvent(accountWithBalance("id", 999), TransferStarted(transactionId, "accTo", PositiveNumber(999).get))
    account.revertFailedTransfer(transactionId) should be(Right(List(TransferFailed(transactionId, "accTo", PositiveNumber(999).get))))
  }


  private def accountWithBalance(id: AccountId, balance: Long): Account = RegisteredAccount(id, balance, List.empty)
}
