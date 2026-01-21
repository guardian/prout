package lib

import cats.*
import cats.data.*
import cats.effect.IO
import cats.syntax.all.*
import com.madgag.git.*
import com.madgag.scalagithub.GitHub
import com.madgag.scalagithub.model.RepoId
import lib.sentry.SentryApiClient
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Droid(
  repoSnapshotFactory: RepoSnapshot.Factory,
  repoUpdater: RepoUpdater,
  prUpdater: PRUpdater
)(implicit
  g: GitHub,
  as: ActorSystem,
  sentryApiClientOpt: Option[SentryApiClient]
) {

  val logger = Logger(getClass)

  def scan(repoId: RepoId): IO[Seq[PullRequestCheckpointsStateChangeSummary]] = {
    logger.info(s"Asked to audit ${repoId.fullName}")

    for {
      repoSnapshot <- repoSnapshotFactory.snapshot(repoId)
      pullRequestUpdates <- processMergedPullRequestsOn(repoSnapshot)
    } yield pullRequestUpdates
  }

  def processMergedPullRequestsOn(repoSnapshot: RepoSnapshot): IO[Seq[PullRequestCheckpointsStateChangeSummary]] = for {
    _ <- repoUpdater.attemptToCreateMissingLabels(repoSnapshot.repoLevelDetails)
    summaryOpts <-
      repoSnapshot.mergedPullRequestSnapshots.parUnorderedTraverse(prSnapshot => prUpdater.process(prSnapshot, repoSnapshot)).getOrElse(Seq.empty)
  } yield summaryOpts


}
