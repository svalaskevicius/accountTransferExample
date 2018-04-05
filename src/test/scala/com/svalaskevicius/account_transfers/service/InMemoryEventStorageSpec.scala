package com.svalaskevicius.account_transfers.service

import com.svalaskevicius.account_transfers.model.AggregateLoader
import org.scalatest._

import scala.concurrent.duration.Duration
import monix.execution.Scheduler.Implicits.global

class InMemoryEventStorageSpec extends FlatSpec with Matchers {
  "In memory event storage" should "return empty aggregate if it's missing" in {
    new InMemoryEventStorage(testAggregateLoader).readAggregate("test").runSyncUnsafe(Duration.Inf) should be(List.empty)
  }

  it should "allow to update aggregate" in {
    val storage = new InMemoryEventStorage(testAggregateLoader)
    storage.runTransaction("test") { existing => Right(List("new ev1", s"len: ${existing.length}")) }.runSyncUnsafe(Duration.Inf) should be (Right(List("new ev1", "len: 0")))
    storage.readAggregate("test").runSyncUnsafe(Duration.Inf) should be (List("new ev1", "len: 0"))
    storage.runTransaction("test") { existing => Right(List(s"new ev2, len: ${existing.length}")) }.runSyncUnsafe(Duration.Inf) should be (Right(List("new ev2, len: 2")))
    storage.readAggregate("test").runSyncUnsafe(Duration.Inf) should be (List("new ev2, len: 2", "new ev1", "len: 0"))
  }

  it should "keep existing aggregate info on error" in {
    val storage = new InMemoryEventStorage(testAggregateLoader)
    storage.runTransaction("test") { existing => Right(List("new ev1", s"len: ${existing.length}")) }.runSyncUnsafe(Duration.Inf) should be (Right(List("new ev1", "len: 0")))
    storage.runTransaction("test") { _ => Left("error") }.runSyncUnsafe(Duration.Inf) should be (Left("error"))
    storage.readAggregate("test").runSyncUnsafe(Duration.Inf) should be (List("new ev1", "len: 0"))
  }

  private object testAggregateLoader extends AggregateLoader[List[String], String] {
    def empty = List.empty
    def applyEvent(aggregate: List[String], event: String) = event :: aggregate
  }

}
