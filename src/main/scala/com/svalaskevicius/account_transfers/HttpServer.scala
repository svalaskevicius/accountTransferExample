package com.svalaskevicius.account_transfers

import cats.effect.IO
import com.svalaskevicius.account_transfers.http.HttpAccountService
import com.svalaskevicius.account_transfers.model.Account
import com.svalaskevicius.account_transfers.service.{AccountService, InMemoryEventStorage}
import fs2.StreamApp
import org.http4s.dsl.Http4sDsl
import org.http4s.server.blaze.BlazeBuilder

import scala.concurrent.ExecutionContext.Implicits.global

object HttpServer extends StreamApp[IO] with Http4sDsl[IO] {

  def stream(args: List[String], requestShutdown: IO[Unit]) = {

    val storage = new InMemoryEventStorage(Account.loader)
    val accountService = new AccountService(storage)
    val httpAccountService = new HttpAccountService(accountService)

    BlazeBuilder[IO]
      .bindHttp(8080, "0.0.0.0")
      .mountService(httpAccountService.service, "/")
      .serve
  }
}
