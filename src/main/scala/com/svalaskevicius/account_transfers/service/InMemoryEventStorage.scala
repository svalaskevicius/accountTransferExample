package com.svalaskevicius.account_transfers.service

import java.util.concurrent.ConcurrentHashMap

import cats.Id
import com.svalaskevicius.account_transfers.model.{AggregateLoader, EventStorage}

class InMemoryEventStorage[Aggregate, Snapshot, Event] (val aggregateLoader: AggregateLoader[Aggregate, Snapshot, Event]) extends EventStorage[Id, Aggregate, Snapshot, Event] {

  private val storage = new ConcurrentHashMap[String, List[Event]]()

  /**
    * Read aggregate
    *
    * @param aggregateId
    * @return
    */
  def readAggregate[A](aggregateId: String): Aggregate = aggregateFromStoredInfo(storage.get(aggregateId))

  /**
    * Run a transaction for a given aggregate, store the changes, and return their result
    *
    * @param aggregateId
    * @param f
    * @tparam Err
    * @return
    */
  def runTransaction[Err](aggregateId: String)(f: Aggregate => Err Either List[Event]): Err Either Unit = {
    var returnValue: Either[Err, Unit] = Right(())
    storage.compute(aggregateId, (_, currentEventsOrNull) => {
      f(aggregateFromStoredInfo(currentEventsOrNull)) match {
        case Left(error) =>
          returnValue = Left(error)
          currentEventsOrNull
        case Right(newEvents) =>
          val currentEvents = Option(currentEventsOrNull).getOrElse(List.empty)
          newEvents ++ currentEvents
      }
    })
    returnValue
  }

  private def aggregateFromStoredInfo(currentEventsOrNull: List[Event]) = {
    val currentEvents = Option(currentEventsOrNull).getOrElse(List.empty)
    currentEvents.foldRight(aggregateLoader.empty)((event, agg) => aggregateLoader.applyEvent(agg, event))
  }
}
