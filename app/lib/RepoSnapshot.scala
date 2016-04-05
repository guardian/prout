/*
 * Copyright 2014 The Guardian
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package lib

import java.time.Instant.now

import com.madgag.git._
import com.madgag.github.Implicits._
import com.madgag.scalagithub.GitHub._
import com.madgag.scalagithub.commands.{CreateComment, CreateLabel}
import com.madgag.scalagithub.model.{PullRequest, Repo}
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.typesafe.scalalogging.LazyLogging
import lib.Config.Checkpoint
import com.madgag.time.Implicits._
import lib.RepoSnapshot._
import lib.gitgithub.{IssueUpdater, LabelMapping}
import lib.travis.TravisApiClient
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.joda.time.format.PeriodFormat
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
import scala.math.Ordering._
import scala.util.Success
import scalax.file.ImplicitConversions._

object RepoSnapshot {

  val logger = Logger(getClass)

  val WorthyOfCommentWindow: java.time.Duration = 12.hours

  val travisApiClient = new TravisApiClient(Bot.accessToken)

  val ClosedPRsMostlyRecentlyUpdated = Map(
    "state" -> "closed",
    "sort" -> "updated",
    "direction" -> "desc"
  )

  def apply(githubRepo: Repo)(implicit checkpointSnapshoter: CheckpointSnapshoter): Future[RepoSnapshot] = {

    implicit val github = Bot.github

    val repoId = githubRepo.repoId

    def isMergedToMaster(pr: PullRequest): Boolean = pr.merged_at.isDefined && pr.base.ref == githubRepo.default_branch

    val hooksF = githubRepo.hooks.list().map(_.flatMap(_.config.get("url").map(_.uri))).all()

    val mergedPullRequestsF: Future[Seq[PullRequest]] = (for {
      litePullRequests <- githubRepo.pullRequests.list(ClosedPRsMostlyRecentlyUpdated).all()
      pullRequests <- Future.traverse(litePullRequests.filter(isMergedToMaster).take(10))(pr => githubRepo.pullRequests.get(pr.number).map(_.result))
    } yield {
        logger.info("PRs merged to master size="+pullRequests.size)
      pullRequests
    }) andThen { case cprs => logger.info(s"Merged Pull Requests fetched: ${cprs.map(_.map(_.number).sorted.reverse)}") }

    val gitRepoF = Future {
      RepoUtil.getGitRepo(
        Bot.parentWorkDir / repoId.owner / repoId.name,
        githubRepo.clone_url,
        Some(Bot.githubCredentials.git))
    } andThen { case r => logger.info(s"Git Repo ref count: ${r.map(_.getAllRefs.size)}") }

    for {
      mergedPullRequests <- mergedPullRequestsF
      gitRepo <- gitRepoF
    } yield RepoSnapshot(githubRepo, gitRepo, mergedPullRequests, hooksF, checkpointSnapshoter)

  }
}

case class RepoSnapshot(
  repo: Repo,
  gitRepo: Repository,
  mergedPullRequests: Seq[PullRequest],
  hooksF: Future[Seq[Uri]]  ,
  checkpointSnapshoter: CheckpointSnapshoter) {
  self =>

  implicit val github = Bot.github

  private implicit val (revWalk, reader) = gitRepo.singleThreadedReaderTuple

  lazy val masterCommit:RevCommit = gitRepo.resolve(repo.default_branch).asRevCommit

  lazy val config = ConfigFinder.config(masterCommit)

  lazy val affectedFoldersByPullRequest: Map[PullRequest, Set[String]] = (for {
    pr <- mergedPullRequests
  } yield pr -> GitChanges.affects(pr, config.foldersWithValidConfig)).toMap


  lazy val pullRequestsByAffectedFolder : Map[String, Set[PullRequest]] = config.foldersWithValidConfig.map {
    folder => folder -> mergedPullRequests.filter(pr => affectedFoldersByPullRequest(pr).contains(folder)).toSet
  }.toMap

  Logger.info(s"${repo.full_name} pullRequestsByAffectedFolder : ${pullRequestsByAffectedFolder.mapValues(_.map(_.number))}")

  lazy val activeConfigByPullRequest: Map[PullRequest, Set[Checkpoint]] = affectedFoldersByPullRequest.mapValues {
    _.flatMap(config.validConfigByFolder(_).checkpointSet)
  }

  val activeConfig: Set[Checkpoint] = activeConfigByPullRequest.values.flatten.toSet

  lazy val checkpointSnapshotsF: Map[Checkpoint, Future[CheckpointSnapshot]] = activeConfig.map {
    c =>
      c -> {
        for (possibleIdsTry <- checkpointSnapshoter(c).trying) yield {
          val objectIdTry = for (possibleIds <- possibleIdsTry) yield {
            possibleIds.map(reader.resolveExistingUniqueId).collectFirst {
              case Success(objectId) => objectId
            }
          }
          CheckpointSnapshot(c, objectIdTry)
        }
      }
  }.toMap

  lazy val activeSnapshotsF = Future.sequence(activeConfig.map(checkpointSnapshotsF))

  def checkpointSnapshotsFor(pr: PullRequest, oldState: PRCheckpointState): Future[Set[CheckpointSnapshot]] =
    Future.sequence(activeConfigByPullRequest(pr).filter(!oldState.hasSeen(_)).map(checkpointSnapshotsF))

  val issueUpdater = new IssueUpdater[PullRequest, PRCheckpointState, PullRequestCheckpointsSummary] with LazyLogging {
    val repo = self.repo

    val pf=PeriodFormat.getDefault

    val labelToStateMapping = new LabelMapping[PRCheckpointState] {
      def labelsFor(s: PRCheckpointState): Set[String] = s.statusByCheckpoint.map {
        case (checkpointName, cs) => cs.labelFor(checkpointName)
      }.toSet

      def stateFrom(labels: Set[String]): PRCheckpointState = PRCheckpointState(activeConfig.flatMap { checkpoint =>
        PullRequestCheckpointStatus.fromLabels(labels, checkpoint).map(checkpoint.name -> _)
      }.toMap)
    }

    def ignoreItemsWithExistingState(existingState: PRCheckpointState): Boolean =
      existingState.hasStateForCheckpointsWhichHaveAllBeenSeen

    def snapshot(oldState: PRCheckpointState, pr: PullRequest) =
      for (cs <- checkpointSnapshotsFor(pr, oldState)) yield PullRequestCheckpointsSummary(pr, cs, gitRepo, oldState)

    override def actionTaker(snapshot: PullRequestCheckpointsSummary) {
      val pr = snapshot.pr

      val newlySeenSnapshots = snapshot.changedSnapshotsByState.get(Seen).toSeq.flatten

      logger.info(s"action taking: ${pr.prId} newlySeenSnapshots = $newlySeenSnapshots")

      for {
        newlySeenSnapshot <- newlySeenSnapshots
        afterSeen <- newlySeenSnapshot.checkpoint.details.afterSeen
        travis <- afterSeen.travis
      } {
        logger.info(s"${pr.prId} going to do $travis")

        travisApiClient.requestBuild(repo.full_name, travis, repo.default_branch)
      }

      val mergeToNow = java.time.Duration.between(pr.merged_at.get.toInstant, now)
      val previouslyTouchedByProut = snapshot.oldState.statusByCheckpoint.nonEmpty
      if (previouslyTouchedByProut || mergeToNow < WorthyOfCommentWindow) {
        logger.trace(s"changedSnapshotsByState : ${snapshot.changedSnapshotsByState}")

        val timeSinceMerge = mergeToNow.toPeriod().withMillis(0).toString(pf)

        val mergedByOpt = pr.merged_by

        logger.info(s"mergedByOpt=$mergedByOpt merged_at=${pr.merged_at}")

        val mergedByText = s"merged by ${mergedByOpt.get.atLogin} $timeSinceMerge ago"
        val responsibleText = if (pr.user.id == mergedByOpt.get.id) mergedByText else {
          s"created by ${pr.user.atLogin} and $mergedByText"
        }

        def commentOn(status: PullRequestCheckpointStatus, advice: String) = {
          for (changedSnapshots <- snapshot.changedSnapshotsByState.get(status)) {
            val checkpoints = changedSnapshots.map(_.checkpoint.nameMarkdown).mkString(", ")

            pr.comments2.create(CreateComment(s"${status.name} on $checkpoints ($responsibleText) $advice"))
          }
        }

        for (hooks <- hooksF) {
          slack.DeployReporter.report(snapshot, hooks)
        }

        commentOn(Seen, "Please check your changes!")
        commentOn(Overdue, "What's gone wrong?")
      }
    }
  }

  def processMergedPullRequests(): Future[Seq[PullRequestCheckpointsSummary]] = for {
    _ <- attemptToCreateMissingLabels()
    summaryOpts <- Future.traverse(mergedPullRequests)(issueUpdater.process)
  } yield summaryOpts.flatten

  def missingLabelsGiven(existingLabelNames: Set[String]): Set[CreateLabel] = for {
    prcs <- PullRequestCheckpointStatus.all
    checkpointName <- config.checkpointsByName.keySet
    label = prcs.labelFor(checkpointName)
    if !existingLabelNames(label)
  } yield CreateLabel(label, prcs.defaultColour)

  def attemptToCreateMissingLabels(): Future[_] = {
    for {
      existingLabels <- repo.labels.list().all()
      createdLabels <- Future.traverse(missingLabelsGiven(existingLabels.map(_.name).toSet)) {
        missingLabel => repo.labels.create(missingLabel)
      }
    } yield createdLabels
  }.trying
}
