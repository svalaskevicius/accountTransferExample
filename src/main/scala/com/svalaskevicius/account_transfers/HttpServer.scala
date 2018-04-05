package com.svalaskevicius.account_transfers

import com.svalaskevicius.account_transfers.http.HttpAccountService
import com.svalaskevicius.account_transfers.model.Account
import com.svalaskevicius.account_transfers.service.{AccountService, InMemoryEventStorage}
import fs2.StreamApp
import monix.eval.Task
import org.http4s.server.blaze.BlazeBuilder

import monix.execution.Scheduler.Implicits.global


/**
  * The main class for serving HTTP.
  */
object HttpServer extends StreamApp[Task] {

  def stream(args: List[String], requestShutdown: Task[Unit]) = {

    val storage = new InMemoryEventStorage(Account.loader)
    val accountService = new AccountService(storage)
    val httpAccountService = new HttpAccountService(accountService)

    BlazeBuilder[Task]
      .bindHttp(8080, "0.0.0.0")
      .mountService(httpAccountService.service, "/")
      .serve
  }
}
