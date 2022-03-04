package lib

import java.util.concurrent.TimeUnit

import akka.actor.Scheduler

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

class Delayer(
  scheduler: Scheduler
) {

  def doAfterSmallDelay(f: => Unit): Unit = {
    scheduler.scheduleOnce(concurrent.duration.Duration(1, TimeUnit.SECONDS))(f)
  }

  def delayTheFuture[T](f: => Future[T]): Future[T] = {
    val p = Promise[T]()
    doAfterSmallDelay(p.completeWith(f))
    p.future
  }
}
