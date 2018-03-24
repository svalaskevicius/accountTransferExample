package com.svalaskevicius.account_transfers

object PositiveAmount {
  def apply(value: Long) = Some(new PositiveAmount(value))
}
final case class PositiveAmount private (value: Long)
