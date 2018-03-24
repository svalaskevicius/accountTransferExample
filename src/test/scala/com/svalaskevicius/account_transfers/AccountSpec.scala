package com.svalaskevicius.account_transfers

import java.util.UUID

import com.svalaskevicius.account_transfers.AccountEvent.Registered
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
}
