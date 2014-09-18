package lib

import java.util.concurrent.TimeUnit.MINUTES

import org.eclipse.jgit.lib.ObjectId
import org.kohsuke.github.GHRepository
import play.api.Logger
import play.api.cache.Cache

import scala.collection.immutable.Seq
import scala.concurrent.{Future, Await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

object Scanner {

  import play.api.Play.current

  def updateFor(checkpoint: String, repoFullName: RepoFullName, commitIdentifier: RepoSnapshot => Future[SiteSnapshot]) {
    val key = repoFullName + " " + checkpoint
    Logger.debug(s"update requested for $key")
    Cache.getOrElse(key) {
      new Dogpile {
        val githubRepo = Bot.conn().getRepository(repoFullName.text)
        Logger.info(s"Asked to audit ${githubRepo.getFullName}")

        val repoSnapshotF = RepoSnapshot(githubRepo)
        val jobFuture = for {
          repoSnapshot <- repoSnapshotF
          siteSnapshot <- commitIdentifier(repoSnapshot)
          prStatuses <- DeploymentProgressSnapshot(repoSnapshot, siteSnapshot).goCrazy()
        } yield {
          val prByStatus = prStatuses.groupBy(_.currentStatus)

          Logger.info(s"${githubRepo.getFullName} : All: ${summary(prStatuses)}")
          Logger.info(s"${githubRepo.getFullName} : overdue=${prByStatus.get(Overdue).map(summary)} pending=${prByStatus.get(Pending).map(summary)}")
        }
        Await.ready(jobFuture, Duration(2, MINUTES))
      }
    }.doAtLeastOneMore()
  }

  private def scan(githubRepo: GHRepository, checkpoint: String, commitIdentifier: RepoSnapshot => Future[SiteSnapshot]) = {

  }

  def summary(prs: Seq[PullRequestSiteCheck]): String = prs.size + " " + prs.map(_.pr.getNumber).sorted.reverse.map("#"+_).mkString("(", ", ", ")")
}
