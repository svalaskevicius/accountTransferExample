package com.svalaskevicius.account_transfers

import org.scalatest._

class PositiveAmountSpec extends FlatSpec with Matchers {
  "Positive amount" should "be allowed to construct with a positive value" in {
    PositiveAmount(1).map(_.value) should be (Some(1))
  }
}
