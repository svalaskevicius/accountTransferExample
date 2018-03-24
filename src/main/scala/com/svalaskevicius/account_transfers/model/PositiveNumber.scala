package com.svalaskevicius.account_transfers.model

object PositiveNumber {
  def apply(value: Long) = if (value > 0) Some(new PositiveNumber(value)) else None
}

final case class PositiveNumber private(value: Long)
