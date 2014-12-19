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
      activeSnapshots <- repoSnapshot.activeSnapshotsF
    } yield {
      Logger.info(s"affectedFoldersByPullRequest=${repoSnapshot.affectedFoldersByPullRequest}")
      Logger.info(s"${activeSnapshots.size} activeSnapshots : ${activeSnapshots.map(s => s.checkpoint.name -> s.commitId.map(_.name()).getOrElse("None")).toMap}")
      pullRequestUpdates.flatten
    }
  }

}
