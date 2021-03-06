package com.svalaskevicius.account_transfers.service

import com.svalaskevicius.account_transfers.TestUtils._
import com.svalaskevicius.account_transfers.model.AggregateLoader
import org.scalatest._

class InMemoryEventStorageSpec extends FlatSpec with Matchers {
  "In memory event storage" should "return empty aggregate if it's missing" in {
    runTask(new InMemoryEventStorage(testAggregateLoader).readOperation("test")(Right(_))) should be(List.empty)
  }

  it should "allow to update aggregate" in {
    val storage = new InMemoryEventStorage(testAggregateLoader)
    runTask(storage.runTransaction("test") { existing => Right(List("new ev1", s"len: ${existing.length}")) }) should be (List("new ev1", "len: 0"))
    runTask(storage.readOperation("test")(Right(_))) should be (List("new ev1", "len: 0"))
    runTask(storage.runTransaction("test") { existing => Right(List(s"new ev2, len: ${existing.length}")) }) should be (List("new ev2, len: 2"))
    runTask(storage.readOperation("test")(Right(_))) should be (List("new ev2, len: 2", "new ev1", "len: 0"))
  }

  it should "keep existing aggregate info on error" in {
    val storage = new InMemoryEventStorage(testAggregateLoader)
    runTask(storage.runTransaction("test") { existing => Right(List("new ev1", s"len: ${existing.length}")) }) should be (List("new ev1", "len: 0"))
    runFailingTask(storage.runTransaction("test") { _ => Left(new RuntimeException("error")) }).getMessage should be ("error")
    runTask(storage.readOperation("test")(Right(_))) should be (List("new ev1", "len: 0"))
  }

  private object testAggregateLoader extends AggregateLoader[List[String], String] {
    def empty = List.empty
    def applyEvent(aggregate: List[String], event: String) = event :: aggregate
  }

}
