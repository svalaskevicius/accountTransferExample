package com.svalaskevicius.account_transfers.service

import java.util.concurrent.ConcurrentHashMap

import com.svalaskevicius.account_transfers.model.{AggregateLoader, EventStorage}
import monix.eval.Task

/**
  * `ConcurrentHashMap` backed `EventStorage` implementation.
  *
  * @param aggregateLoader injected `AggregateLoaded` implementation
  * @tparam Aggregate      Aggregate type
  * @tparam Event          Event type that is compatible with the Aggregate
  */
class InMemoryEventStorage[Aggregate, Event] (val aggregateLoader: AggregateLoader[Aggregate, Event]) extends EventStorage[Aggregate, Event] {

  private val storage = new ConcurrentHashMap[String, List[Event]]()

  /**
    * Read aggregate
    *
    * @param aggregateId
    * @return
    */
  def readAggregate(aggregateId: String): Task[Aggregate] = Task {
    aggregateFromStoredInfo(storage.get(aggregateId))
  }

  /**
    * Run a transaction for a given aggregate, store the changes, and return their result
    *
    * @param aggregateId
    * @param f
    * @tparam Err
    * @return
    */
  def runTransaction[Err](aggregateId: String)(f: Aggregate => Err Either List[Event]): Task[Err Either List[Event]] = Task {
    var returnValue: Either[Err, List[Event]] = Right(List.empty)
    storage.compute(aggregateId, (_, currentEventsOrNull) => {
      f(aggregateFromStoredInfo(currentEventsOrNull)) match {
        case Left(error) =>
          returnValue = Left(error)
          currentEventsOrNull
        case Right(newEvents) =>
          returnValue = Right(newEvents)
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
