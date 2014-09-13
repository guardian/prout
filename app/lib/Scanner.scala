package lib

import java.util.concurrent.TimeUnit.MINUTES

import org.kohsuke.github.GHRepository
import play.api.Logger
import play.api.cache.Cache

import scala.collection.immutable.Seq
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

object Scanner {

  import play.api.Play.current

  def updateFor(site: Site, repoFullName: RepoFullName) {
    val key = repoFullName + " " + site
    Logger.debug(s"update requested for $key")
    Cache.getOrElse(key) {
      new Dogpile(scan(site, Bot.conn().getRepository(repoFullName.text)))
    }.doAtLeastOneMore()
  }

  private def scan(site: Site, githubRepo: GHRepository) = {
    Logger.info(s"Asked to audit ${githubRepo.getFullName}")

    val siteSnapshotF = SiteSnapshot(site)
    val repoSnapshotF = RepoSnapshot(githubRepo)
    val jobFuture = for {
      siteSnapshot <- siteSnapshotF
      repoSnapshot <- repoSnapshotF
      status = DeploymentProgressSnapshot(repoSnapshot, siteSnapshot)
      prStatuses <- status.goCrazy()
    } yield {
      val prByStatus = prStatuses.groupBy(_.currentStatus)

      Logger.info(s"${githubRepo.getFullName} : All: ${summary(prStatuses)}")
      Logger.info(s"${githubRepo.getFullName} : overdue=${prByStatus.get(Overdue).map(summary)} pending=${prByStatus.get(Pending).map(summary)}")
    }
    Await.ready(jobFuture, Duration(2, MINUTES))
  }

  def summary(prs: Seq[PullRequestSiteCheck]): String = prs.size + " " + prs.map(_.pr.getNumber).sorted.reverse.map("#"+_).mkString("(", ", ", ")")
}
