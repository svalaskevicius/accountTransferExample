package com.svalaskevicius.account_transfers.http

import com.svalaskevicius.account_transfers.model.Account
import com.svalaskevicius.account_transfers.service.{AccountService, InMemoryEventStorage}
import monix.eval.Task
import org.http4s.{HttpService, Method, Request, Uri}
import org.scalatest.{FlatSpec, Matchers}

import com.svalaskevicius.account_transfers.TestUtils._

class HttpAccountServiceSpec extends FlatSpec with Matchers {
  "Http server" should "allow transfer between accounts" in {
    val service = newHttpService
    runRequest(service, requestToRegisterAccount("account1", 100)) should be((200, """{"message":"Account registered"}"""))
    runRequest(service, requestToRegisterAccount("account2", 100)) should be((200, """{"message":"Account registered"}"""))
    runRequest(service, requestToTransferAmount("account1", "account2", 50)) should be((200, """{"message":"Transfer completed"}"""))
    runRequest(service, requestBalance("account1")) should be((200, """{"balance":50}"""))
    runRequest(service, requestBalance("account2")) should be((200, """{"balance":150}"""))
  }

  it should "return error on invalid input" in {
    val service = newHttpService
    runRequest(service, requestToRegisterAccount("account1", 100)) should be((200, """{"message":"Account registered"}"""))
    runRequest(service, requestToRegisterAccount("account1", 100)) should be((409, """Account has already been registered"""))
    val creditErrorCheck = """Could not complete transfer: CreditFailed\(account3,50,AccountHasNotBeenRegistered,[a-f0-9-]*\)""".r
    runRequest(service, requestToTransferAmount("account1", "account3", 50)) should matchPattern {
      case (400, err: String) if creditErrorCheck.findFirstIn(err).isDefined =>
    }
    runRequest(service, requestToTransferAmount("account3", "account3", 50)) should be((400, """Could not complete transfer: DebitFailed(account3,50,AccountHasNotBeenRegistered)"""))
    runRequest(service, requestToTransferAmount("account1", "account1", -50)) should be((400, """Could not read request: Not a positive number"""))
    runRequest(service, requestBalance("account3")) should be((404, """Account could not be found"""))
    runRequest(service, requestToRegisterAccountInvalidJson("account1", 100)) should be((400, """Could not read request as Json: Malformed message body: Invalid JSON"""))
    runRequest(service, requestToTransferAmountInvalidJson("account1", "account1", -50)) should be((400, """Could not read request as Json: Malformed message body: Invalid JSON"""))
  }

  private def requestToRegisterAccount(id: String, balance: Long) = Request[Task](
    Method.POST,
    Uri.fromString(s"/$id/register").toOption.get,
    body = fs2.Stream.emits(s"""{"initialBalance": $balance}""".getBytes)
  )

  private def requestToRegisterAccountInvalidJson(id: String, balance: Long) = Request[Task](
    Method.POST,
    Uri.fromString(s"/$id/register").toOption.get,
    body = fs2.Stream.emits(s"""{nce}""".getBytes)
  )

  private def requestToTransferAmount(id1: String, id2: String, balance: Long) = Request[Task](
    Method.POST,
    Uri.fromString(s"/$id1/transfer").toOption.get,
    body = fs2.Stream.emits(s"""{"accountTo": "$id2", "amount": $balance}""".getBytes)
  )

  private def requestToTransferAmountInvalidJson(id1: String, id2: String, balance: Long) = Request[Task](
    Method.POST,
    Uri.fromString(s"/$id1/transfer").toOption.get,
    body = fs2.Stream.emits(s"""",,,""".getBytes)
  )

  private def requestBalance(id: String) = Request[Task](
    Method.GET,
    Uri.fromString(s"/$id/balance").toOption.get
  )

  private def newHttpService = {
    val storage = new InMemoryEventStorage(Account.loader)
    val accountService = new AccountService(storage)
    new HttpAccountService(accountService).service
  }

  private def runRequest(service: HttpService[Task], req: Request[Task]) = {
    val ret = runTask(service(req).value).get
    (ret.status.code, new String(runTask(ret.body.compile.toList).toArray))
  }
}
