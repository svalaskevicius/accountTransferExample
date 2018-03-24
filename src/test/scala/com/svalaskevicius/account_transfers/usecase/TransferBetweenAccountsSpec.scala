package com.svalaskevicius.account_transfers.usecase

import com.svalaskevicius.account_transfers.model.DebitError.InsufficientFunds
import com.svalaskevicius.account_transfers.model.{Account, AccountEvent, PositiveNumber}
import com.svalaskevicius.account_transfers.service.{AccountService, InMemoryEventStorage}
import com.svalaskevicius.account_transfers.usecase.TransferBetweenAccountsError.DebitFailed
import org.scalatest._

class TransferBetweenAccountsSpec extends FlatSpec with Matchers {
  "TransferBetweenAccounts" should "transfer requested amount" in {
    val storage = new InMemoryEventStorage(Account.loader)
    val accountService = new AccountService(storage)
    val transferBetweenAccounts = new TransferBetweenAccounts(accountService)

    accountService.register("account_1", 10000)
    accountService.register("account_2", 10000)

    transferBetweenAccounts("account_1", "account_2", PositiveNumber(500).get) should be (Right(()))

    accountService.currentBalance("account_1") should be(Right(10000 - 500))
    accountService.currentBalance("account_2") should be(Right(10000 + 500))
  }

  it should "fail when debit fails" in {
    val storage = new InMemoryEventStorage(Account.loader)
    val accountService = new AccountService(storage)
    val transferBetweenAccounts = new TransferBetweenAccounts(accountService)

    accountService.register("account_1", 10000)
    accountService.register("account_2", 10000)

    transferBetweenAccounts("account_1", "account_2", PositiveNumber(5000000).get) should be (Left(DebitFailed(InsufficientFunds)))

    accountService.currentBalance("account_1") should be(Right(10000))
    accountService.currentBalance("account_2") should be(Right(10000))
  }
}
