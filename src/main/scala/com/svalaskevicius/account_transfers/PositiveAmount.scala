package com.svalaskevicius.account_transfers

object PositiveAmount {
  def apply(value: Long) = if (value > 0) Some(new PositiveAmount(value)) else None
}

final case class PositiveAmount private (value: Long)
