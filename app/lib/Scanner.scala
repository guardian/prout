package lib

import java.util.concurrent.TimeUnit.MINUTES

import org.kohsuke.github.GHRepository
import play.api.Logger
import play.api.cache.Cache

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object Scanner {

  import play.api.Play.current

  def updateFor(repoFullName: RepoFullName) {
    val key = repoFullName
    Logger.debug(s"update requested for $key")
    Cache.getOrElse(key.text) {
      new Dogpile(scan(Bot.conn().getRepository(repoFullName.text)))
    }.doAtLeastOneMore()
  }

  private def scan(githubRepo: GHRepository) = {
    Logger.info(s"Asked to audit ${githubRepo.getFullName}")

    val repoSnapshotF = RepoSnapshot(githubRepo)
    val jobFuture = for {
      repoSnapshot <- repoSnapshotF
      foo <- Future.traverse(repoSnapshot.mergedPullRequests)(repoSnapshot.issueUpdater.process)
    } yield {
      // val prByStatus = prStatuses.groupBy(_.currentStatus)
    }
    Await.ready(jobFuture, Duration(2, MINUTES))
  }

}
