package com.svalaskevicius.account_transfers

import java.util.UUID

import com.svalaskevicius.account_transfers.AccountEvent.{Credited, Debited, Registered, TransferStarted}
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
    new RegisteredAccount("id", 0).register("id2") should be(Left(RegisterError.AccountHasAlreadyBeenRegistered))
  }

  it should "return current account balance" in {
    new RegisteredAccount("id", 999).currentBalance should be(Right(999))
  }

  it should "allow debit operation" in {
    val result = new RegisteredAccount("id", 999).debitForTransfer("accTo", PositiveNumber(999).get)
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
  }

  it should "fail to debit if insufficient funds" in {
    new RegisteredAccount("id", 999).debitForTransfer("accTo", PositiveNumber(1000).get) should be(Left(DebitError.InsufficientFunds))
  }

  it should "allow to be credited" in {
    val transactionId = UUID.randomUUID()
    new RegisteredAccount("id", 999).creditForTransfer(transactionId, PositiveNumber(1000).get) should be(Right(List(Credited(transactionId, PositiveNumber(1000).get))))
  }
}
