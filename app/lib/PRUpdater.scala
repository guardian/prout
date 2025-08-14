package lib

import com.madgag.scalagithub.GitHub
import com.madgag.scalagithub.commands.CreateComment
import com.madgag.scalagithub.model.PullRequest
import com.madgag.time.Implicits._
import com.typesafe.scalalogging.LazyLogging
import lib.Config.CheckpointMessages
import lib.RepoSnapshot.WorthyOfCommentWindow
import lib.Responsibility.responsibilityAndRecencyFor
import lib.labels.{Overdue, PullRequestCheckpointStatus, Seen}
import lib.sentry.{PRSentryRelease, SentryApiClient}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PRUpdater(delayer: Delayer) extends LazyLogging {

  def process(prSnapshot: PRSnapshot, repoSnapshot: RepoSnapshot)(implicit
    g: GitHub,
    sentryApiClientOpt: Option[SentryApiClient]
  ): Future[Option[PullRequestCheckpointsStateChangeSummary]] = {
    logger.trace(s"handling ${prSnapshot.pr.prId.slug}")
    for {
      snapshot <- getSummaryOfCheckpointChangesGiven(prSnapshot, repoSnapshot)
    } yield snapshot
  }

  private def getSummaryOfCheckpointChangesGiven(prSnapshot: PRSnapshot, repoSnapshot: RepoSnapshot)(implicit
    gitHub: GitHub,
    sentryApiClientOpt: Option[SentryApiClient]
  ): Future[Option[PullRequestCheckpointsStateChangeSummary]] = {
    val pr = prSnapshot.pr
    val (oldStateLabelsSeq, userLabels) = prSnapshot.labels.map(_.name).partition(repoSnapshot.allPossibleCheckpointPRLabels)
    val oldLabels = oldStateLabelsSeq.toSet
    val existingPersistedState: PRCheckpointState = repoSnapshot.labelToStateMapping.stateFrom(oldLabels)
    if (!ignoreItemsWithExistingState(existingPersistedState)) {
      for (currentSnapshot <- findCheckpointStateChange(existingPersistedState, pr, repoSnapshot)) yield {
        val newPersistableState = currentSnapshot.newPersistableState
        val stateChanged = newPersistableState != existingPersistedState

        logger.debug(s"handling ${pr.prId.slug} : state: existing=$existingPersistedState new=$newPersistableState stateChanged=$stateChanged")

        if (stateChanged) {
          logger.info(s"#${pr.prId.slug} state-change: $existingPersistedState -> $newPersistableState")
          val newLabels: Set[String] = repoSnapshot.labelToStateMapping.labelsFor(newPersistableState)
          assert(oldLabels != newLabels, s"Labels should differ for differing states. labels=$oldLabels oldState=$existingPersistedState newState=$newPersistableState")
          pr.labels.replace(userLabels ++ newLabels)
          delayer.doAfterSmallDelay {
            actionTaker(currentSnapshot, repoSnapshot)
          }
        }
        Some(currentSnapshot)
      }
    } else Future.successful(None)
  }



  def ignoreItemsWithExistingState(existingState: PRCheckpointState): Boolean =
    existingState.hasStateForCheckpointsWhichHaveAllBeenSeen

  def findCheckpointStateChange(oldState: PRCheckpointState, pr: PullRequest, repoSnapshot: RepoSnapshot): Future[PullRequestCheckpointsStateChangeSummary] =
    for (cs <- repoSnapshot.checkpointSnapshotsFor(pr, oldState)) yield {
      val details = PRCheckpointDetails(pr, cs, repoSnapshot.repoLevelDetails.gitRepo)
      PullRequestCheckpointsStateChangeSummary(details, oldState)
    }

  def actionTaker(
    checkpointsChangeSummary: PullRequestCheckpointsStateChangeSummary,
    repoSnapshot: RepoSnapshot
  )(implicit
    g: GitHub,
    sentryApiClientOpt: Option[SentryApiClient]
  ): Unit = {
    val pr = checkpointsChangeSummary.prCheckpointDetails.pr
    val now = Instant.now()

    def sentryReleaseOpt(): Option[PRSentryRelease] = {
      val sentryProjects = for {
        configs <- repoSnapshot.activeConfByPullRequest.get(pr).toSeq
        config <- configs
        sentryConf <- config.sentry.toSeq
        sentryProject <- sentryConf.projects
      } yield sentryProject

      for {
        mergeCommit <- pr.merge_commit_sha if sentryProjects.nonEmpty
      } yield PRSentryRelease(mergeCommit, sentryProjects)
    }

    val newlySeenSnapshots = checkpointsChangeSummary.changedByState.get(Seen).toSeq.flatten

    logger.info(s"action taking: ${pr.prId} newlySeenSnapshots = $newlySeenSnapshots")

    val mergeToNow = java.time.Duration.between(pr.merged_at.get.toInstant, now)
    val previouslyTouchedByProut = checkpointsChangeSummary.oldState.statusByCheckpoint.nonEmpty
    if (previouslyTouchedByProut || mergeToNow < WorthyOfCommentWindow) {
      logger.trace(s"changedSnapshotsByState : ${checkpointsChangeSummary.changedByState}")

      def commentOn(status: PullRequestCheckpointStatus, additionalAdvice: Option[String] = None) = {

        lazy val fileFinder = repoSnapshot.repoLevelDetails.createFileFinder()

        for (changedSnapshots <- checkpointsChangeSummary.changedByState.get(status)) {

          val checkpoints = changedSnapshots.map(_.snapshot.checkpoint.nameMarkdown).mkString(", ")

          val customAdvices = for {
            s <- changedSnapshots
            messages <- s.snapshot.checkpoint.details.messages
            path <- messages.filePathforStatus(status)
            message <- fileFinder.read(path)
          } yield message
          val advices = if(customAdvices.nonEmpty) customAdvices else CheckpointMessages.defaults.get(status).toSet
          val advice = (advices ++ additionalAdvice).mkString("\n\n")

          pr.comments2.create(CreateComment(s"${status.name} on $checkpoints (${responsibilityAndRecencyFor(pr)}) $advice"))
        }
      }

      for (updateReporter <- repoSnapshot.updateReporters) {
        updateReporter.report(repoSnapshot, pr, checkpointsChangeSummary)
      }

      val sentryDetails: Option[String] = for {
        sentry <- sentryApiClientOpt
        sentryRelease <- sentryReleaseOpt()
      } yield sentryRelease.detailsMarkdown(sentry.org)

      commentOn(Seen, sentryDetails)
      commentOn(Overdue)
    }
  }
}

