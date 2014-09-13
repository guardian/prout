package lib

import java.util.concurrent.TimeUnit

import play.api.Play.current
import play.api.libs.concurrent.Akka
import scala.concurrent.ExecutionContext.Implicits.global

object Delayer {

  private implicit val system = Akka.system

  def doAfterSmallDelay(f: => Unit): Unit = {
    // akka.pattern.after(concurrent.duration.Duration(1, TimeUnit.SECONDS), system.scheduler)
    system.scheduler.scheduleOnce(concurrent.duration.Duration(1, TimeUnit.SECONDS))(f)
  }

}
