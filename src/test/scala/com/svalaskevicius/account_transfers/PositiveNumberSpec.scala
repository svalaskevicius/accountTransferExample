package com.svalaskevicius.account_transfers

import org.scalacheck.Gen
import org.scalatest._
import org.scalatest.prop.PropertyChecks

class PositiveNumberSpec extends FlatSpec with Matchers with PropertyChecks {
  "Positive amount" should "be allowed to be constructed with a positive value" in forAll(Gen.posNum[Long]) { value =>
    PositiveNumber(value).map(_.value) should be (Some(value))
  }

  it should "not allow to be constructed with zero" in {
    PositiveNumber(0).map(_.value) should be (None)
  }

  it should "not allow to be constructed with negative value" in forAll(Gen.negNum[Long]) { value =>
    PositiveNumber(value).map(_.value) should be (None)
  }
}
