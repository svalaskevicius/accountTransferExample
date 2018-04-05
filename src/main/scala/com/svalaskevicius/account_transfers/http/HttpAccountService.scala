package com.svalaskevicius.account_transfers.http

import com.svalaskevicius.account_transfers.model.{AccountReadError, PositiveNumber, RegisterError}
import com.svalaskevicius.account_transfers.service.AccountService
import com.svalaskevicius.account_transfers.usecase.TransferBetweenAccounts
import io.circe.{Decoder, Json}
import org.http4s.circe._
import org.http4s.{HttpService, Request, Response}
import org.http4s.dsl.Http4sDsl
import io.circe.generic.semiauto._
import monix.eval.Task

case class RegisterAccountRequest(initialBalance: Long)

case class TransferAmountRequest(accountTo: String, amount: PositiveNumber)

object HttpAccountService {
  implicit def positiveNumberDecoder(implicit asLong: Decoder[Long]): Decoder[PositiveNumber] =
    asLong.emap(l => PositiveNumber(l).toRight("Not a positive number"))

  implicit val registerAccountRequestDecoder: Decoder[RegisterAccountRequest] = deriveDecoder[RegisterAccountRequest]
  implicit val transferAmountRequestDecoder: Decoder[TransferAmountRequest] = deriveDecoder[TransferAmountRequest]
}

/**
  * Http service exposing REST endpoints for a given accountService
  *
  * @param accountService
  */
class HttpAccountService(accountService: AccountService) extends Http4sDsl[Task] {

  import HttpAccountService._

  private val transferBetweenAccounts = new TransferBetweenAccounts(accountService)

  val service: HttpService[Task] = HttpService[Task] {
    case req@POST -> Root / accountId / "register" => registerAccount(accountId, req)
    case GET -> Root / accountId / "balance" => retrieveAccountBalance(accountId)
    case req@POST -> Root / accountId / "transfer" => transferMoney(accountId, req)
  }

  private def registerAccount(accountId: String, req: Request[Task]) =
    withJsonRequestAs[RegisterAccountRequest](req) { requestData =>
      accountService.register(accountId, requestData.initialBalance).flatMap {
        case Right(_) => Ok(Json.obj("message" -> Json.fromString(s"Account registered")))
        case Left(RegisterError.AccountHasAlreadyBeenRegistered) => Conflict("Account has already been registered")
      }
    }

  private def retrieveAccountBalance(accountId: String) =
    accountService.currentBalance(accountId).flatMap {
      case Left(AccountReadError.AccountHasNotBeenRegistered) => NotFound("Account could not be found")
      case Right(balance) => Ok(Json.obj("balance" -> Json.fromLong(balance)))
    }

  private def transferMoney(accountId: String, req: Request[Task]) =
    withJsonRequestAs[TransferAmountRequest](req) { requestData =>
      transferBetweenAccounts(accountId, requestData.accountTo, requestData.amount).flatMap {
        case Right(_) => Ok(Json.obj("message" -> Json.fromString(s"Transfer completed")))
        case Left(error) => BadRequest(s"Could not complete transfer: $error")
      }
    }

  private def withJsonRequestAs[A: Decoder](req: Request[Task])(f: A => Task[Response[Task]]): Task[Response[Task]] =
    req.attemptAs[Json].map(_.as[A]).value.flatMap {
      case Right(Right(requestData)) => f(requestData)
      case Right(Left(decodeFailure)) => BadRequest(s"Could not read request: ${decodeFailure.message}")
      case Left(decodeFailure) => BadRequest(s"Could not read request as Json: ${decodeFailure.message}")
    }
}
