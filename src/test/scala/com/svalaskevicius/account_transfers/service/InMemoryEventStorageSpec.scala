package com.svalaskevicius.account_transfers.service

import com.svalaskevicius.account_transfers.model.AggregateLoader
import org.scalatest._

class InMemoryEventStorageSpec extends FlatSpec with Matchers {
  "Empty event storage" should "return empty aggregate" in {
    new InMemoryEventStorage(testAggregateLoader).readAggregate("test") should be(List.empty)
  }

  private object testAggregateLoader extends AggregateLoader[List[String], List[String], String] {
    def empty = List.empty
    def takeSnapshot(aggregate: List[String]) = aggregate
    def fromSnapshot(snapshot: List[String]) = snapshot
    def applyEvent(aggregate: List[String], event: String) = event :: aggregate
  }

}
