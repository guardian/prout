package monitoring

import org.kohsuke.github.{GHRateLimit, GitHub}
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

object GitHubQuota {

  def trackQuotaOver[T](conn: GitHub, task: String)(taskFuture: =>Future[T])(implicit ec: ExecutionContext): Future[T] = {
    val startQuota = conn.getRateLimit
    val t = taskFuture
    t.onComplete {
      case _ => logEnd(conn, task, startQuota)
    }
    t
  }

  def trackQuotaOn[T](conn: GitHub, taskName: String)(task: =>T): T = {
    val startQuota = conn.getRateLimit
    val t = task
    logEnd(conn, taskName, startQuota)
    t
  }

  def logEnd[T](conn: GitHub, taskName: String, startQuota: GHRateLimit) {
    val endQuota = conn.getRateLimit
    Logger.info(s"Quota for '$taskName' - start=${startQuota.remaining} end=${endQuota.remaining} (${startQuota.remaining - endQuota.remaining} used?) reset=${endQuota.getResetDate}")
  }
}
