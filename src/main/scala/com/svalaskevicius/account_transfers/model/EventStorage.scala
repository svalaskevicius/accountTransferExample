package com.svalaskevicius.account_transfers.model

trait AggregateLoader[Aggregate, Snapshot, Event] {
  def empty: Aggregate
  def takeSnapshot(aggregate: Aggregate): Snapshot
  def fromSnapshot(snapshot: Snapshot): Aggregate
  def applyEvent(aggregate: Aggregate, event: Event): Aggregate
}

trait EventStorage[F[_], Aggregate, Snapshot, Event] {

  def aggregateLoader: AggregateLoader[Aggregate, Snapshot, Event]

  /**
    * Read aggregate
    *
    * @param aggregateId
    * @return
    */
  def readAggregate[A](aggregateId: String): F[Aggregate]

  /**
    * Run a transaction for a given aggregate, store the changes, and return their result
    *
    * @param aggregateId
    * @param f
    * @tparam Err
    * @return
    */
  def runTransaction[Err](aggregateId: String)(f: Aggregate => Err Either List[Event]): F[Err Either Unit]
}
