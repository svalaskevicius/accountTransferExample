package com.svalaskevicius.account_transfers.model

import monix.eval.Task

/**
  * Defines an interface for an aggregate loader.
  *
  * Each aggregate, that is using `EventStorage` for storing their events *must* provide one.
  *
  * @tparam Aggregate
  * @tparam Event
  */
trait AggregateLoader[Aggregate, Event] {

  /**
    * Create an initial Aggregate state - one that has no events applied yet.
    *
    * @return
    */
  def empty: Aggregate

  /**
    * Apply a given event to an aggregate and return the new state
    *
    * @param aggregate
    * @param event
    * @return
    */
  def applyEvent(aggregate: Aggregate, event: Event): Aggregate
}

/**
  * EventStorage handles loading and updating Aggregate.
  *
  * @tparam Aggregate Aggregate type
  * @tparam Event     Event type that is compatible with the Aggregate
  */
trait EventStorage[Aggregate, Event] {

  def aggregateLoader: AggregateLoader[Aggregate, Event]

  /**
    * Load the latest version of an aggregate (non transactionally).
    *
    * @param aggregateId
    * @param f           a function to apply on loaded Aggregate
    * @tparam Err        error type that `f` returns
    * @tparam A          result type of `f`
    * @return            the aggregate state with all events applied
    */
  def readOperation[Err <: Throwable, A](aggregateId: String)(f: Aggregate => Err Either A): Task[A]

  /**
    * Run a transaction for a given aggregate, store the changes (events) on success, and return their result.
    *
    * @param aggregateId
    * @param f           a function to apply on loaded Aggregate
    * @tparam Err        error type that `f` returns
    * @return            the result of the provided function `f`
    */
  def runTransaction[Err <: Throwable](aggregateId: String)(f: Aggregate => Err Either List[Event]): Task[List[Event]]
}
