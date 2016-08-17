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
import com.madgag.scalagithub.GitHub
import com.madgag.scalagithub.GitHub._
import com.madgag.scalagithub.commands.{CreateComment, CreateLabel}
import com.madgag.scalagithub.model.{PullRequest, Repo}
import com.madgag.time.Implicits._
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.typesafe.scalalogging.LazyLogging
import lib.Config.Checkpoint
import lib.RepoSnapshot._
import lib.gitgithub.{IssueUpdater, LabelMapping}
import lib.labels.{Overdue, PullRequestCheckpointStatus, Seen}
import lib.travis.TravisApiClient
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.{RevCommit, RevWalk}
import org.joda.time.format.PeriodFormat
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
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

  def log(message: String)(implicit repo: Repo) = logger.info(s"${repo.full_name} - $message")

  def isMergedToMaster(pr: PullRequest)(implicit repo: Repo): Boolean = pr.merged_at.isDefined && pr.base.ref == repo.default_branch

  def mergedPullRequestsFor(repo: Repo)(implicit g: GitHub): Future[Seq[PullRequest]] = {
    implicit val r = repo
    (for {
      litePullRequests <- repo.pullRequests.list(ClosedPRsMostlyRecentlyUpdated).takeUpTo(2)
      pullRequests <- Future.traverse(litePullRequests.filter(isMergedToMaster).take(10))(pr => repo.pullRequests.get(pr.number).map(_.result))
    } yield {
      log(s"PRs merged to master size=${pullRequests.size}")
      pullRequests
    }) andThen { case cprs => log(s"Merged Pull Requests fetched: ${cprs.map(_.map(_.number).sorted.reverse)}") }
  }

  def apply(githubRepo: Repo)(implicit checkpointSnapshoter: CheckpointSnapshoter): Future[RepoSnapshot] = {

    implicit val ir: Repo = githubRepo
    implicit val github = Bot.github

    val repoId = githubRepo.repoId

    val hooksF: Future[Seq[Uri]] = if (githubRepo.permissions.exists(_.admin)) githubRepo.hooks.list().map(_.flatMap(_.config.get("url").map(_.uri))).all() else {
      log(s"No admin rights to check hooks")
      Future.successful(Seq.empty)
    }

    val gitRepoF = Future {
      RepoUtil.getGitRepo(
        Bot.parentWorkDir / repoId.owner / repoId.name,
        githubRepo.clone_url,
        Some(Bot.githubCredentials.git))
    } andThen { case r => log(s"Git Repo ref count: ${r.map(_.getAllRefs.size)}") }

    for {
      mergedPullRequests <- mergedPullRequestsFor(githubRepo)
      gitRepo <- gitRepoF
    } yield RepoSnapshot(githubRepo, gitRepo, mergedPullRequests, hooksF, checkpointSnapshoter)

  }
}

case class Diagnostic(
                       snapshots: Set[CheckpointSnapshot],
                       prDetails: Seq[PRCheckpointDetails]
                     ) {

  val snapshotsByCheckpoint: Map[Checkpoint, CheckpointSnapshot] = snapshots.map(s => s.checkpoint -> s).toMap
}

case class RepoSnapshot(
  repo: Repo,
  gitRepo: Repository,
  mergedPullRequests: Seq[PullRequest],
  hooksF: Future[Seq[Uri]]  ,
  checkpointSnapshoter: CheckpointSnapshoter) {
  self =>

  implicit val github = Bot.github

  implicit val repoThreadLocal = gitRepo.getObjectDatabase.threadLocalResources



  lazy val masterCommit:RevCommit = gitRepo.resolve(repo.default_branch).asRevCommit(new RevWalk(repoThreadLocal.reader()))

  lazy val config = ConfigFinder.config(masterCommit)

  lazy val affectedFoldersByPullRequest: Map[PullRequest, Set[String]] = {
    implicit val revWalk = new RevWalk(repoThreadLocal.reader())
    println(s"getting affectedFoldersByPullRequest on ${gitRepo.getDirectory.getAbsolutePath}")
    (for {
      pr <- mergedPullRequests
    } yield pr -> GitChanges.affects(pr, config.foldersWithValidConfig)).toMap
  }


  lazy val pullRequestsByAffectedFolder : Map[String, Set[PullRequest]] = config.foldersWithValidConfig.map {
    folder => folder -> mergedPullRequests.filter(pr => affectedFoldersByPullRequest(pr).contains(folder)).toSet
  }.toMap

  Logger.info(s"${repo.full_name} pullRequestsByAffectedFolder : ${pullRequestsByAffectedFolder.mapValues(_.map(_.number))}")




  lazy val activeConfigByPullRequest: Map[PullRequest, Set[Checkpoint]] = affectedFoldersByPullRequest.mapValues {
    _.flatMap(config.validConfigByFolder(_).checkpointSet)
  }

  val allAvailableCheckpoints: Set[Checkpoint] = config.checkpointsByName.values.toSet

  def diagnostic(): Future[Diagnostic] = {
    for {
      snapshots <- snapshotOfAllAvailableCheckpoints()
    } yield {
      Diagnostic(snapshots, mergedPullRequests.map(pr => PRCheckpointDetails(pr, snapshots.filter(s => activeConfigByPullRequest(pr).contains(s.checkpoint)), gitRepo)))
    }
  }

  def snapshotOfAllAvailableCheckpoints(): Future[Set[CheckpointSnapshot]] =
    Future.sequence(allAvailableCheckpoints.map(takeCheckpointSnapshot))

  val activeCheckpoints: Set[Checkpoint] = activeConfigByPullRequest.values.flatten.toSet

  lazy val snapshotsOfActiveCheckpointsF: Map[Checkpoint, Future[CheckpointSnapshot]] =
    activeCheckpoints.map { c => c -> takeCheckpointSnapshot(c) }.toMap

  def takeCheckpointSnapshot(checkpoint: Checkpoint): Future[CheckpointSnapshot] = {
    for (possibleIdsTry <- checkpointSnapshoter(checkpoint).trying) yield {
      val objectIdTry = for (possibleIds <- possibleIdsTry) yield {
        possibleIds.map(repoThreadLocal.reader().resolveExistingUniqueId).collectFirst {
          case Success(objectId) => objectId
        }
      }
      CheckpointSnapshot(checkpoint, objectIdTry)
    }
  }

  lazy val activeSnapshotsF = Future.sequence(activeCheckpoints.map(snapshotsOfActiveCheckpointsF))

  def checkpointSnapshotsFor(pr: PullRequest, oldState: PRCheckpointState): Future[Set[CheckpointSnapshot]] =
    Future.sequence(activeConfigByPullRequest(pr).filter(!oldState.hasSeen(_)).map(snapshotsOfActiveCheckpointsF))

  val issueUpdater = new IssueUpdater[PullRequest, PRCheckpointState, PullRequestCheckpointsStateChangeSummary] with LazyLogging {
    val repo = self.repo

    val pf=PeriodFormat.getDefault

    val labelToStateMapping = new LabelMapping[PRCheckpointState] {
      def labelsFor(s: PRCheckpointState): Set[String] = s.statusByCheckpoint.map {
        case (checkpointName, cs) => cs.labelFor(checkpointName)
      }.toSet

      def stateFrom(labels: Set[String]): PRCheckpointState = PRCheckpointState(activeCheckpoints.flatMap { checkpoint =>
        PullRequestCheckpointStatus.fromLabels(labels, checkpoint).map(checkpoint.name -> _)
      }.toMap)
    }

    def ignoreItemsWithExistingState(existingState: PRCheckpointState): Boolean =
      existingState.hasStateForCheckpointsWhichHaveAllBeenSeen

    def snapshot(oldState: PRCheckpointState, pr: PullRequest) =
      for (cs <- checkpointSnapshotsFor(pr, oldState)) yield {
        val details = PRCheckpointDetails(pr, cs, gitRepo)
        PullRequestCheckpointsStateChangeSummary(details, oldState)
      }

    override def actionTaker(snapshot: PullRequestCheckpointsStateChangeSummary) {
      val pr = snapshot.prCheckpointDetails.pr

      val newlySeenSnapshots = snapshot.changedByState.get(Seen).toSeq.flatten

      logger.info(s"action taking: ${pr.prId} newlySeenSnapshots = $newlySeenSnapshots")

      for {
        newlySeenSnapshot <- newlySeenSnapshots
        afterSeen <- newlySeenSnapshot.snapshot.checkpoint.details.afterSeen
        travis <- afterSeen.travis
      } {
        logger.info(s"${pr.prId} going to do $travis")

        travisApiClient.requestBuild(repo.full_name, travis, repo.default_branch)
      }

      val mergeToNow = java.time.Duration.between(pr.merged_at.get.toInstant, now)
      val previouslyTouchedByProut = snapshot.oldState.statusByCheckpoint.nonEmpty
      if (previouslyTouchedByProut || mergeToNow < WorthyOfCommentWindow) {
        logger.trace(s"changedSnapshotsByState : ${snapshot.changedByState}")

        val timeSinceMerge = mergeToNow.toPeriod().withMillis(0).toString(pf)

        val mergedByOpt = pr.merged_by

        logger.info(s"mergedByOpt=$mergedByOpt merged_at=${pr.merged_at}")

        val mergedByText = s"merged by ${mergedByOpt.get.atLogin} $timeSinceMerge ago"
        val responsibleText = if (pr.user.id == mergedByOpt.get.id) mergedByText else {
          s"created by ${pr.user.atLogin} and $mergedByText"
        }

        def commentOn(status: PullRequestCheckpointStatus, advice: String) = {
          for (changedSnapshots <- snapshot.changedByState.get(status)) {
            val checkpoints = changedSnapshots.map(_.snapshot.checkpoint.nameMarkdown).mkString(", ")

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

  def processMergedPullRequests(): Future[Seq[PullRequestCheckpointsStateChangeSummary]] = for {
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
