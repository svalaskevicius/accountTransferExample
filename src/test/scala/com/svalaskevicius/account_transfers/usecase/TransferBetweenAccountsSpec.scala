package com.svalaskevicius.account_transfers.usecase

import java.util.UUID

import com.svalaskevicius.account_transfers.model.CreditError.AccountHasNotBeenRegistered
import com.svalaskevicius.account_transfers.model.DebitError.InsufficientFunds
import com.svalaskevicius.account_transfers.model.{Account, PositiveNumber}
import com.svalaskevicius.account_transfers.service.{AccountService, InMemoryEventStorage}
import com.svalaskevicius.account_transfers.usecase.TransferBetweenAccountsError.{CreditFailed, DebitFailed}
import org.scalatest._

class TransferBetweenAccountsSpec extends FlatSpec with Matchers {
  "TransferBetweenAccounts" should "transfer requested amount" in {
    val storage = new InMemoryEventStorage(Account.loader)
    val accountService = new AccountService(storage)
    val transferBetweenAccounts = new TransferBetweenAccounts(accountService)

    accountService.register("account_1", 10000)
    accountService.register("account_2", 10000)

    transferBetweenAccounts("account_1", "account_2", PositiveNumber(500).get) should be(Right(()))

    accountService.currentBalance("account_1") should be(Right(10000 - 500))
    accountService.currentBalance("account_2") should be(Right(10000 + 500))
  }

  it should "fail when debit fails" in {
    val storage = new InMemoryEventStorage(Account.loader)
    val accountService = new AccountService(storage)
    val transferBetweenAccounts = new TransferBetweenAccounts(accountService)

    accountService.register("account_1", 10000)
    accountService.register("account_2", 10000)

    transferBetweenAccounts("account_1", "account_2", PositiveNumber(5000000).get) should be(Left(DebitFailed("account_1", PositiveNumber(5000000).get, InsufficientFunds)))

    accountService.currentBalance("account_1") should be(Right(10000))
    accountService.currentBalance("account_2") should be(Right(10000))
  }

  it should "fail when credit fails" in {
    val storage = new InMemoryEventStorage(Account.loader)
    val accountService = new AccountService(storage)
    val transferBetweenAccounts = new TransferBetweenAccounts(accountService)

    accountService.register("account_1", 10000)

    transferBetweenAccounts("account_1", "account_2", PositiveNumber(500).get) should matchPattern {
      case Left(CreditFailed("account_2", amount, AccountHasNotBeenRegistered, _: UUID)) if amount.value == 500 =>
    }

    accountService.currentBalance("account_1") should be(Right(10000))
  }
}
