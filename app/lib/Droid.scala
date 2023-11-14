package lib

import org.apache.pekko.stream.Materializer
import com.madgag.git._
import com.madgag.scalagithub.GitHub
import com.madgag.scalagithub.model.RepoId
import lib.sentry.SentryApiClient
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Droid(
  repoSnapshotFactory: RepoSnapshot.Factory,
  repoUpdater: RepoUpdater,
  prUpdater: PRUpdater
)(implicit
  g: GitHub,
  mat: Materializer,
  sentryApiClientOpt: Option[SentryApiClient]
) {

  val logger = Logger(getClass)

  def scan(repoId: RepoId): Future[Seq[PullRequestCheckpointsStateChangeSummary]] = {
    logger.info(s"Asked to audit ${repoId.fullName}")

    for {
      repoSnapshot <- repoSnapshotFactory.snapshot(repoId)
      pullRequestUpdates <- processMergedPullRequestsOn(repoSnapshot)
      activeSnapshots <- repoSnapshot.activeSnapshotsF
    } yield {
      logger.info(s"${repoId.fullName} has ${activeSnapshots.size} active snapshots : ${activeSnapshots.map(s => s.checkpoint.name -> s.commitIdTry.map(_.map(_.shortName).getOrElse("None"))).toMap}")
      pullRequestUpdates
    }
  }

  def processMergedPullRequestsOn(repoSnapshot: RepoSnapshot): Future[Seq[PullRequestCheckpointsStateChangeSummary]] = for {
    _ <- repoUpdater.attemptToCreateMissingLabels(repoSnapshot.repoLevelDetails)
    summaryOpts <- Future.traverse(repoSnapshot.mergedPullRequestSnapshots)(prSnapshot => prUpdater.process(prSnapshot, repoSnapshot))
  } yield summaryOpts.flatten


}
