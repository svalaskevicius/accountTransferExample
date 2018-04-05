package com.svalaskevicius.account_transfers

import monix.eval.Task

import scala.concurrent.duration.Duration

object TestUtils {

  def runTask[A](t: Task[A]): A = {
    import monix.execution.Scheduler.Implicits.global
    t.runSyncUnsafe(Duration.Inf)
  }
}
