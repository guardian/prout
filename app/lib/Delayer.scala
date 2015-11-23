package lib

import java.util.concurrent.TimeUnit

import play.api.Play.current
import play.api.libs.concurrent.Akka

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

object Delayer {

  private implicit val system = Akka.system

  def doAfterSmallDelay(f: => Unit): Unit = {
    system.scheduler.scheduleOnce(concurrent.duration.Duration(1, TimeUnit.SECONDS))(f)
  }

  def delayTheFuture[T](f: => Future[T]): Future[T] = {
    val p = Promise[T]
    doAfterSmallDelay(p.completeWith(f))
    p.future
  }
}
