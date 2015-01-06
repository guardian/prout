package lib

import org.kohsuke.github.GHRepository
import play.api.Logger

import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import com.madgag.git._

class Droid {

  def scan(
    githubRepo: GHRepository
  )(implicit checkpointSnapshoter: CheckpointSnapshoter): Future[Seq[PullRequestCheckpointsSummary]] = {
    Logger.info(s"Asked to audit ${githubRepo.getFullName}")

    val repoSnapshotF = RepoSnapshot(githubRepo)

    for {
      repoSnapshot <- repoSnapshotF
      pullRequestUpdates <- repoSnapshot.processMergedPullRequests()
      activeSnapshots <- repoSnapshot.activeSnapshotsF
    } yield {
      Logger.info(s"${githubRepo.getFullName} has ${activeSnapshots.size} active snapshots : ${activeSnapshots.map(s => s.checkpoint.name -> s.commitId.map(_.shortName).getOrElse("None")).toMap}")
      pullRequestUpdates
    }
  }

}
