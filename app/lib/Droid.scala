package lib

import org.kohsuke.github.GHRepository
import play.api.Logger

import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Droid {

  def scan(
    githubRepo: GHRepository
  )(implicit checkpointSnapshoter: CheckpointSnapshoter): Future[Seq[PullRequestCheckpointsSummary]] = {
    Logger.info(s"Asked to audit ${githubRepo.getFullName}")

    val repoSnapshotF = RepoSnapshot(githubRepo)

    for {
      repoSnapshot <- repoSnapshotF
      pullRequestUpdates <- Future.traverse(repoSnapshot.mergedPullRequests)(repoSnapshot.issueUpdater.process)
    } yield pullRequestUpdates.flatten
  }

}
