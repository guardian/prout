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

import java.time.{Instant, ZonedDateTime}

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
import lib.Config.{Checkpoint, CheckpointMessages}
import lib.RepoSnapshot._
import lib.Responsibility.{createdByAndMergedByFor, responsibilityAndRecencyFor}
import lib.PostDeployActions.TriggerdByProutMsg
import lib.gitgithub.{IssueUpdater, LabelMapping}
import lib.labels._
import lib.librato.LibratoApiClient
import lib.librato.model.{Annotation, Link}
import lib.sentry.{PRSentryRelease, SentryApiClient}
import lib.sentry.model.CreateRelease
import lib.travis.{TravisApi, TravisApiClient, TravisCIOffering}
import org.eclipse.jgit.lib.{ObjectId, Repository}
import org.eclipse.jgit.revwalk.{RevCommit, RevWalk}
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.Success
import scalax.file.ImplicitConversions._

object RepoSnapshot {

  val logger = Logger(getClass)

  val MaxPRsToScanPerRepo = 30

  val WorthyOfScanWindow: java.time.Duration = 14.days

  val WorthyOfCommentWindow: java.time.Duration = 12.hours

  val ClosedPRsMostlyRecentlyUpdated = Map(
    "state" -> "closed",
    "sort" -> "updated",
    "direction" -> "desc"
  )

  def log(message: String)(implicit repo: Repo) = logger.info(s"${repo.full_name} - $message")

  def isMergedToMaster(pr: PullRequest)(implicit repo: Repo): Boolean = pr.merged_at.isDefined && pr.base.ref == repo.default_branch

  def mergedPullRequestsFor(repo: Repo)(implicit g: GitHub): Future[Seq[PullRequest]] = {
    val now = ZonedDateTime.now()
    val timeThresholdForScan = now.minus(WorthyOfScanWindow)
    def isNewEnoughToBeWorthScanning(pr: PullRequest) = pr.merged_at.exists(_.isAfter(timeThresholdForScan))

    implicit val r = repo
    (for {
      litePullRequests <- repo.pullRequests.list(ClosedPRsMostlyRecentlyUpdated).takeUpTo(2)
      pullRequests <- Future.traverse(litePullRequests.filter(isMergedToMaster).filter(isNewEnoughToBeWorthScanning).take(MaxPRsToScanPerRepo))(pr => repo.pullRequests.get(pr.number).map(_.result))
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

  lazy val masterCommit:RevCommit = {
    val id: ObjectId = gitRepo.resolve(repo.default_branch)
    Logger.info(s"Need to look at ${repo.full_name}, branch:${repo.default_branch} commit $id")
    assert(id != null)
    id.asRevCommit(new RevWalk(repoThreadLocal.reader()))
  }

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

  lazy val activeConfByPullRequest: Map[PullRequest, Set[ConfigFile]] = affectedFoldersByPullRequest.mapValues {
    _.map(config.validConfigByFolder(_))
  }

  lazy val activeCheckpointsByPullRequest: Map[PullRequest, Set[Checkpoint]] = activeConfByPullRequest.mapValues {
    _.flatMap(_.checkpointSet)
  }

  val allAvailableCheckpoints: Set[Checkpoint] = config.checkpointsByName.values.toSet

  val allPossibleCheckpointPRLabels: Set[String] = for {
    prLabel <- PullRequestLabel.all
    checkpoint <- allAvailableCheckpoints
  } yield prLabel.labelFor(checkpoint.name)

  def diagnostic(): Future[Diagnostic] = {
    for {
      snapshots <- snapshotOfAllAvailableCheckpoints()
    } yield {
      Diagnostic(snapshots, mergedPullRequests.map(pr => PRCheckpointDetails(pr, snapshots.filter(s => activeCheckpointsByPullRequest(pr).contains(s.checkpoint)), gitRepo)))
    }
  }

  def snapshotOfAllAvailableCheckpoints(): Future[Set[CheckpointSnapshot]] =
    Future.sequence(allAvailableCheckpoints.map(takeCheckpointSnapshot))

  val activeCheckpoints: Set[Checkpoint] = activeCheckpointsByPullRequest.values.flatten.toSet

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
    Future.sequence(activeCheckpointsByPullRequest(pr).filter(!oldState.hasSeen(_)).map(snapshotsOfActiveCheckpointsF))

  val issueUpdater = new IssueUpdater[PullRequest, PRCheckpointState, PullRequestCheckpointsStateChangeSummary] with LazyLogging {
    val repo = self.repo

    val repoSnapshot: RepoSnapshot = self

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
      val now = Instant.now()

      def sentryReleaseOpt(): Option[PRSentryRelease] = {
        val sentryProjects = for {
          configs <- activeConfByPullRequest.get(pr).toSeq
          config <- configs
          sentryConf <- config.sentry.toSeq
          sentryProject <- sentryConf.projects
        } yield sentryProject

        for {
          mergeCommit <- pr.merge_commit_sha if sentryProjects.nonEmpty
        } yield PRSentryRelease(mergeCommit, sentryProjects)
      }

      if (snapshot.newlyMerged) {
        activeCheckpointsByPullRequest
        logger.info(s"action taking: ${pr.prId} is newly merged")

        for {
          sentry <- SentryApiClient.instanceOpt.toSeq
          sentryRelease <- sentryReleaseOpt()
        } {
          val ref = lib.sentry.model.Ref(
            repo.repoId,
            sentryRelease.mergeCommit,
            sentryRelease.mergeCommit.asRevCommit(new RevWalk(repoThreadLocal.reader())).getParents.headOption)
          logger.info(s"${pr.prId.slug} : ref=$ref")

          sentry.createRelease(CreateRelease(
            sentryRelease.version,
            Some(sentryRelease.version),
            Some(pr.html_url),
            sentryRelease.projects,
            refs=Seq(ref)
          ))
        }
      }


      val newlySeenSnapshots = snapshot.changedByState.get(Seen).toSeq.flatten

      logger.info(s"action taking: ${pr.prId} newlySeenSnapshots = $newlySeenSnapshots")

      for {
        newlySeenSnapshot <- newlySeenSnapshots
      } {
        val checkpoint = newlySeenSnapshot.snapshot.checkpoint
        for { librato <- LibratoApiClient.instanceOpt } {
          librato.createAnnotation(s"${pr.baseRepo.name}.prout", Annotation(
            title = s"PR #${pr.number} : '${pr.title}' deployed",
            description = Some(createdByAndMergedByFor(pr).capitalize),
            start_time = pr.merged_at.map(_.toInstant),
            end_time = Some(now),
            source = Some(checkpoint.name),
            links = Seq(Link(
              rel = "github",
              label = Some(s"PR #${pr.number}"),
              href = Uri.parse(pr.html_url)
            ))
          ))
        }

        for {
          afterSeen <- checkpoint.details.afterSeen
          travis <- afterSeen.travis
        } {
          logger.info(s"${pr.prId} going to do $travis")
          TravisApi.clientFor(repo).requestBuild(repo.full_name, travis, TriggerdByProutMsg, repo.default_branch)
        }
      }

      val mergeToNow = java.time.Duration.between(pr.merged_at.get.toInstant, now)
      val previouslyTouchedByProut = snapshot.oldState.statusByCheckpoint.nonEmpty
      if (previouslyTouchedByProut || mergeToNow < WorthyOfCommentWindow) {
        logger.trace(s"changedSnapshotsByState : ${snapshot.changedByState}")

        def commentOn(status: PullRequestCheckpointStatus, additionalAdvice: Option[String] = None) = {

          lazy val fileFinder = new FileFinder(masterCommit)

          for (changedSnapshots <- snapshot.changedByState.get(status)) {

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

        for (hooks <- hooksF) {
          slack.DeployReporter.report(snapshot, hooks)
        }

        val sentryDetails: Option[String] = for {
          sentry <- SentryApiClient.instanceOpt
          sentryRelease <- sentryReleaseOpt()
        } yield sentryRelease.detailsMarkdown(sentry.org)

        commentOn(Seen, sentryDetails)
        commentOn(Overdue)
      }
    }


  }

  def processMergedPullRequests(): Future[Seq[PullRequestCheckpointsStateChangeSummary]] = for {
    _ <- attemptToCreateMissingLabels()
    summaryOpts <- Future.traverse(mergedPullRequests)(issueUpdater.process)
  } yield summaryOpts.flatten

  def missingLabelsGiven(existingLabelNames: Set[String]): Set[CreateLabel] = for {
    prcs <- (PullRequestCheckpointStatus.all ++ CheckpointTestStatus.all)
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

  def prByMasterCommitOpt = mergedPullRequests.find(_.merge_commit_sha.contains(masterCommit.toObjectId))

  def checkForResultsOfPostDeployTesting() = {
    // TiP currently works only when there is a single checkpoint with after seen instructions
    activeSnapshotsF.map { activeSnapshots =>
      val activeCheckpointsWithAfterSeenInstructions: Set[Checkpoint] = activeSnapshots.map(_.checkpoint).filter {
        _.details.afterSeen.isDefined
      }

      if (activeCheckpointsWithAfterSeenInstructions.size == 1) {
        prByMasterCommitOpt.foreach { masterPr =>
          repo.statusesFor(repo.default_branch).map { statuses =>
            PostDeployActions.updateFor(repo, statuses, masterPr, activeCheckpointsWithAfterSeenInstructions.head.name)
          }
        }
      }
    }
  }
}

