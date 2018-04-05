package com.svalaskevicius.account_transfers.usecase

import java.util.concurrent.Executors

import com.svalaskevicius.account_transfers.model.CreditError.AccountHasNotBeenRegistered
import com.svalaskevicius.account_transfers.model.DebitError.InsufficientFunds
import com.svalaskevicius.account_transfers.model.{Account, PositiveNumber}
import com.svalaskevicius.account_transfers.service.{AccountService, InMemoryEventStorage}
import org.scalacheck.Gen
import org.scalatest._
import org.scalatest.prop.PropertyChecks

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

import com.svalaskevicius.account_transfers.TestUtils._

class TransferBetweenAccountsSpec extends FlatSpec with Matchers with PropertyChecks {
  "TransferBetweenAccounts" should "transfer requested amount" in forAll (Gen.choose(1, 10000)) { amount =>
    val storage = new InMemoryEventStorage(Account.loader)
    val accountService = new AccountService(storage)
    val transferBetweenAccounts = new TransferBetweenAccounts(accountService)

    runTask(accountService.register("account_1", 10000))
    runTask(accountService.register("account_2", 20000))

    runTask(transferBetweenAccounts("account_1", "account_2", PositiveNumber(amount).get))

    runTask(accountService.currentBalance("account_1")) should be(10000 - amount)
    runTask(accountService.currentBalance("account_2")) should be(20000 + amount)
  }

  it should "fail when debit fails" in {
    val storage = new InMemoryEventStorage(Account.loader)
    val accountService = new AccountService(storage)
    val transferBetweenAccounts = new TransferBetweenAccounts(accountService)

    runTask(accountService.register("account_1", 10000))
    runTask(accountService.register("account_2", 10000))

    runFailingTask(transferBetweenAccounts("account_1", "account_2", PositiveNumber(5000000).get)) should be(InsufficientFunds())

    runTask(accountService.currentBalance("account_1")) should be(10000)
    runTask(accountService.currentBalance("account_2")) should be(10000)
  }

  it should "fail when credit fails" in {
    val storage = new InMemoryEventStorage(Account.loader)
    val accountService = new AccountService(storage)
    val transferBetweenAccounts = new TransferBetweenAccounts(accountService)

    runTask(accountService.register("account_1", 10000))

    runFailingTask(transferBetweenAccounts("account_1", "account_2", PositiveNumber(500).get)) should be(AccountHasNotBeenRegistered())

    runTask(accountService.currentBalance("account_1")) should be(10000)
  }

  it should "be thread safe" in {
    val storage = new InMemoryEventStorage(Account.loader)
    val accountService = new AccountService(storage)
    val transferBetweenAccounts = new TransferBetweenAccounts(accountService)

    runTask(accountService.register("account_1", 10000))
    runTask(accountService.register("account_2", 10000))

    implicit val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(20))

    val futures = for (_ <- 1 to 200) yield Future {
      runTask(transferBetweenAccounts("account_1", "account_2", PositiveNumber(1).get))
      runTask(transferBetweenAccounts("account_2", "account_1", PositiveNumber(1).get))
    } (ec)

    Await.result(Future.sequence(futures), Duration.Inf) should be (Vector.fill(200)(()))

    runTask(accountService.currentBalance("account_1")) should be(10000)
    runTask(accountService.currentBalance("account_2")) should be(10000)
  }
}
