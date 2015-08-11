package monitoring

import org.kohsuke.github.GitHub
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

object GitHubQuota {

  def trackQuotaOver[T](conn: GitHub, task: String)(taskFuture: =>Future[T])(implicit ec: ExecutionContext): Future[T] = {
    val startQuota = conn.getRateLimit
    taskFuture.onComplete {
      case _ =>
        val endQuota = conn.getRateLimit
        Logger.info(s"Quota for '$task' - start=${startQuota.remaining} end=${endQuota.remaining} (${startQuota.remaining - endQuota.remaining} used?) reset=${endQuota.getResetDate}")
    }
    taskFuture
  }
}
