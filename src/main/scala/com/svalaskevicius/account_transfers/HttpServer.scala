package com.svalaskevicius.account_transfers

import cats.Id
import cats.effect.IO
import com.svalaskevicius.account_transfers.model.{Account, AccountReadError, PositiveNumber, RegisterError}
import com.svalaskevicius.account_transfers.service.{AccountService, InMemoryEventStorage}
import com.svalaskevicius.account_transfers.usecase.TransferBetweenAccounts
import fs2.StreamApp
import io.circe.{Decoder, Json}
import org.http4s.circe._
import org.http4s.HttpService
import org.http4s.dsl.Http4sDsl
import org.http4s.server.blaze.BlazeBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import io.circe.generic.semiauto._

case class RegisterAccountRequest(initialBalance: Long)
case class TransferAmountRequest(accountTo: String, amount: PositiveNumber)

object HttpServer extends StreamApp[IO] with Http4sDsl[IO] {

  implicit def positiveNumberDecoder(implicit asLong: Decoder[Long]): Decoder[PositiveNumber] = asLong.emap(l => PositiveNumber(l).toRight("Not a positive number"))
  implicit val registerAccountRequestDecoder: Decoder[RegisterAccountRequest] = deriveDecoder[RegisterAccountRequest]
  implicit val transferAmountRequestDecoder: Decoder[TransferAmountRequest] = deriveDecoder[TransferAmountRequest]

  def service(accountService: AccountService[Id]): HttpService[IO] = {
    val transferBetweenAccounts = new TransferBetweenAccounts(accountService)

    HttpService[IO] {
      case req @ POST -> Root / accountId / "register" =>
        req.attemptAs[Json].map(_.as[RegisterAccountRequest]).value.flatMap {
          case Right(Right(RegisterAccountRequest(initialBalance))) =>
            accountService.register(accountId, initialBalance) match {
              case Right(_) => Ok(Json.obj("message" -> Json.fromString(s"Account registered")))
              case Left(RegisterError.AccountHasAlreadyBeenRegistered) => Conflict("Account has already been registered")
            }
          case Right(Left(decodeFailure)) => BadRequest(s"Could not read register request: ${decodeFailure.message}")
          case Left(decodeFailure) => BadRequest(s"Could not read register request as Json: ${decodeFailure.message}")
        }

      case GET -> Root / accountId / "balance" =>
        accountService.currentBalance(accountId) match {
          case Left(AccountReadError.AccountHasNotBeenRegistered) => NotFound("Account could not be found")
          case Right(balance) => Ok(Json.obj("balance" -> Json.fromLong(balance)))
        }

      case req @ POST -> Root / accountId / "transfer" =>
        req.attemptAs[Json].map(_.as[TransferAmountRequest]).value.flatMap {
          case Right(Right(TransferAmountRequest(accountTo, amount))) =>
            transferBetweenAccounts(accountId, accountTo, amount) match {
              case Right(_) => Ok(Json.obj("message" -> Json.fromString(s"Transfer completed")))
              case Left(error) => BadRequest(s"Could not complete transfer: $error")
            }
          case Right(Left(decodeFailure)) => BadRequest(s"Could not read transfer money request: ${decodeFailure.message}")
          case Left(decodeFailure) => BadRequest(s"Could not read transfer money request as Json: ${decodeFailure.message}")
        }
    }
  }

  def stream(args: List[String], requestShutdown: IO[Unit]) = {

    val storage = new InMemoryEventStorage(Account.loader)
    val accountService = new AccountService(storage)

    BlazeBuilder[IO]
      .bindHttp(8080, "0.0.0.0")
      .mountService(service(accountService), "/")
      .serve
  }
}
