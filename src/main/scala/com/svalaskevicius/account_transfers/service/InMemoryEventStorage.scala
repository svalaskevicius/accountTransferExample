package com.svalaskevicius.account_transfers.service

import cats.Id
import com.svalaskevicius.account_transfers.model.{AggregateLoader, EventStorage}

class InMemoryEventStorage[Aggregate, Snapshot, Event] (val aggregateLoader: AggregateLoader[Aggregate, Snapshot, Event]) extends EventStorage[Id, Aggregate, Snapshot, Event] {

  /**
    * Read aggregate
    *
    * @param aggregateId
    * @return
    */
  def readAggregate[A](aggregateId: String): Aggregate = aggregateLoader.empty

  /**
    * Run a transaction for a given aggregate, store the changes, and return their result
    *
    * @param aggregateId
    * @param f
    * @tparam Err
    * @return
    */
  def runTransaction[Err](aggregateId: String)(f: Aggregate => Err Either List[Event]): Err Either List[Event] = ???
}
