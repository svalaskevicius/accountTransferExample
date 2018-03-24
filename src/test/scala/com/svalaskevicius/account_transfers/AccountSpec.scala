package com.svalaskevicius.account_transfers

import com.svalaskevicius.account_transfers.AccountEvent.Registered
import org.scalatest.{FlatSpec, Matchers}

class AccountSpec extends FlatSpec with Matchers {
  "A new account" should "be allowed to register" in {
    UnregisteredAccount.register("accountId") should be (Right(List(Registered("accountId"))))
  }

  it should "fail to return balance" in {
    UnregisteredAccount.currentBalance should be (Left(AccountReadError.AccountHasNotBeenRegistered))
  }
}
