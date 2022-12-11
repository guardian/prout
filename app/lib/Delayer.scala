package lib

import akka.actor.ActorSystem

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

class Delayer(system: ActorSystem) {

  def doAfterSmallDelay(f: => Unit): Unit = {
    system.scheduler.scheduleOnce(concurrent.duration.Duration(1, TimeUnit.SECONDS))(f)
  }

  def delayTheFuture[T](f: => Future[T]): Future[T] = {
    val p = Promise[T]()
    doAfterSmallDelay(p.completeWith(f))
    p.future
  }
}
