package com.svalaskevicius.account_transfers

import org.scalatest._

class PositiveAmountSpec extends FlatSpec with Matchers {
  "Positive amount" should "be allowed to be constructed with a positive value" in {
    PositiveAmount(1).map(_.value) should be (Some(1))
  }

  it should "not allow to be constructed with non positive value" in {
    PositiveAmount(0).map(_.value) should be (None)
    PositiveAmount(-1).map(_.value) should be (None)
  }
}
