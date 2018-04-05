package com.svalaskevicius.account_transfers.model

object PositiveNumber {

  /**
    * Validate and construct a PositiveNumber
    * @param value
    * @return
    */
  def apply(value: Long): Option[PositiveNumber] = if (value > 0) Some(new PositiveNumber(value)) else None
}

/**
  * A class that is ensured to contain only positive numbers.
  *
  * @param value
  */
final case class PositiveNumber private(value: Long)
