package lib

import com.madgag.git._
import com.madgag.scalagithub.model.Repo
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Droid {

  val logger = Logger(getClass)

  def scan(
    githubRepo: Repo
  )(implicit checkpointSnapshoter: CheckpointSnapshoter): Future[Seq[PullRequestCheckpointsStateChangeSummary]] = {
    logger.info(s"Asked to audit ${githubRepo.repoId}")

    val repoSnapshotF = RepoSnapshot(githubRepo)

    for {
      repoSnapshot <- repoSnapshotF
      pullRequestUpdates <- repoSnapshot.processMergedPullRequests()
      activeSnapshots <- repoSnapshot.activeSnapshotsF
    } yield {
      logger.info(s"${githubRepo.repoId} has ${activeSnapshots.size} active snapshots : ${activeSnapshots.map(s => s.checkpoint.name -> s.commitIdTry.map(_.map(_.shortName).getOrElse("None"))).toMap}")
      pullRequestUpdates
    }
  }

}
