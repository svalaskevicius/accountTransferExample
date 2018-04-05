package com.svalaskevicius.account_transfers.usecase

import java.util.UUID
import java.util.concurrent.Executors

import com.svalaskevicius.account_transfers.model.CreditError.AccountHasNotBeenRegistered
import com.svalaskevicius.account_transfers.model.DebitError.InsufficientFunds
import com.svalaskevicius.account_transfers.model.{Account, PositiveNumber}
import com.svalaskevicius.account_transfers.service.{AccountService, InMemoryEventStorage}
import com.svalaskevicius.account_transfers.usecase.TransferBetweenAccountsError.{CreditFailed, DebitFailed}
import org.scalacheck.Gen
import org.scalatest._
import org.scalatest.prop.PropertyChecks

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import monix.execution.Scheduler.Implicits.global

class TransferBetweenAccountsSpec extends FlatSpec with Matchers with PropertyChecks {
  "TransferBetweenAccounts" should "transfer requested amount" in forAll (Gen.choose(1, 10000)) { amount =>
    val storage = new InMemoryEventStorage(Account.loader)
    val accountService = new AccountService(storage)
    val transferBetweenAccounts = new TransferBetweenAccounts(accountService)

    accountService.register("account_1", 10000).runSyncUnsafe(Duration.Inf)
    accountService.register("account_2", 20000).runSyncUnsafe(Duration.Inf)

    transferBetweenAccounts("account_1", "account_2", PositiveNumber(amount).get).runSyncUnsafe(Duration.Inf) should be(Right(()))

    accountService.currentBalance("account_1").runSyncUnsafe(Duration.Inf) should be(Right(10000 - amount))
    accountService.currentBalance("account_2").runSyncUnsafe(Duration.Inf) should be(Right(20000 + amount))
  }

  it should "fail when debit fails" in {
    val storage = new InMemoryEventStorage(Account.loader)
    val accountService = new AccountService(storage)
    val transferBetweenAccounts = new TransferBetweenAccounts(accountService)

    accountService.register("account_1", 10000).runSyncUnsafe(Duration.Inf)
    accountService.register("account_2", 10000).runSyncUnsafe(Duration.Inf)

    transferBetweenAccounts("account_1", "account_2", PositiveNumber(5000000).get).runSyncUnsafe(Duration.Inf) should be(Left(DebitFailed("account_1", 5000000, InsufficientFunds)))

    accountService.currentBalance("account_1").runSyncUnsafe(Duration.Inf) should be(Right(10000))
    accountService.currentBalance("account_2").runSyncUnsafe(Duration.Inf) should be(Right(10000))
  }

  it should "fail when credit fails" in {
    val storage = new InMemoryEventStorage(Account.loader)
    val accountService = new AccountService(storage)
    val transferBetweenAccounts = new TransferBetweenAccounts(accountService)

    accountService.register("account_1", 10000).runSyncUnsafe(Duration.Inf)

    transferBetweenAccounts("account_1", "account_2", PositiveNumber(500).get).runSyncUnsafe(Duration.Inf) should matchPattern {
      case Left(CreditFailed("account_2", 500, AccountHasNotBeenRegistered, _: UUID)) =>
    }

    accountService.currentBalance("account_1").runSyncUnsafe(Duration.Inf) should be(Right(10000))
  }

  it should "be thread safe" in {
    val storage = new InMemoryEventStorage(Account.loader)
    val accountService = new AccountService(storage)
    val transferBetweenAccounts = new TransferBetweenAccounts(accountService)

    accountService.register("account_1", 10000).runSyncUnsafe(Duration.Inf)
    accountService.register("account_2", 10000).runSyncUnsafe(Duration.Inf)

    val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(20))

    val futures = for (_ <- 1 to 200) yield Future {
      transferBetweenAccounts("account_1", "account_2", PositiveNumber(1).get).runSyncUnsafe(Duration.Inf) should be(Right(()))
      transferBetweenAccounts("account_2", "account_1", PositiveNumber(1).get).runSyncUnsafe(Duration.Inf) should be(Right(()))
    } (ec)

    Await.result(Future.sequence(futures), Duration.Inf) should be (Vector.fill(200)(Succeeded))

    accountService.currentBalance("account_1").runSyncUnsafe(Duration.Inf) should be(Right(10000))
    accountService.currentBalance("account_2").runSyncUnsafe(Duration.Inf) should be(Right(10000))
  }
}
