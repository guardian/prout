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

import cats.*
import cats.data.*
import cats.effect.IO
import cats.syntax.all.*
import com.madgag.git.*
import com.madgag.github.Implicits.*
import com.madgag.scala.collection.decorators.*
import com.madgag.scalagithub.GitHub
import com.madgag.scalagithub.GitHub.*
import com.madgag.scalagithub.model.{PullRequest, Repo, RepoId}
import com.madgag.time.Implicits.*
import lib.Config.Checkpoint
import lib.gitgithub.LabelMapping
import lib.labels.*
import org.apache.pekko.actor.ActorSystem
import org.eclipse.jgit.lib.{ObjectId, Repository}
import org.eclipse.jgit.revwalk.{RevCommit, RevWalk}
import play.api.Logging
import sttp.model.Uri

import java.time.ZonedDateTime
import scala.concurrent.*
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.math.Ordering.Implicits.*
import scala.util.Success

object RepoSnapshot {

  val MaxPRsToScanPerRepo = 30
  val WorthyOfScanWindow: java.time.Duration = 14.days
  val WorthyOfCommentWindow: java.time.Duration = 12.hours

  val ClosedPRsMostlyRecentlyUpdated: Map[String, String] =
    Map("state" -> "closed", "sort" -> "updated", "direction" -> "desc")

  class Factory(
    bot: Bot
  )(implicit
    as: ActorSystem,
    checkpointSnapshoter: CheckpointSnapshoter
  ) {
    given github: GitHub = bot.github

    def log(message: String)(using repo: Repo): Unit = logger.info(s"${repo.full_name} - $message")
    def logAround[T](desc: String)(thunk: IO[T])(using repo: Repo): IO[T] = thunk.timed.map {
      case (duration, result) =>
        log(s"${repo.repoId.fullName} : '$desc' ${duration.toMillis} ms")
        result
    }

    def isMergedToMain(pr: PullRequest): Boolean = pr.merged_at.isDefined

    def snapshot(repoId: RepoId): IO[RepoSnapshot] = for {
      githubRepo <- github.getRepo(repoId)
      repoSnapshot <- snapshotRepo(using githubRepo.result)
    } yield repoSnapshot

    def snapshotRepo(using githubRepo: Repo): IO[RepoSnapshot] = {
      val mergedPullRequestsF = logAround("fetch PRs")(fetchMergedPullRequests())
      val hooksF = logAround("fetch repo hooks")(fetchRepoHooks())
      val gitRepoF = logAround("fetch git repo")(fetchLatestCopyOfGitRepo())

      for {
        mergedPullRequests <- mergedPullRequestsF
        gitRepo <- gitRepoF
        hooks <- hooksF
      } yield RepoSnapshot(RepoLevelDetails(githubRepo, gitRepo, hooks), mergedPullRequests)
    }

    def prSnapshot(prNumber: Int)(using repo: Repo): IO[PRSnapshot] = for {
      prResponse <- repo.pullRequests.get(prNumber)
      pr = prResponse.result
      labelsResponse <- pr.labels.list().compile.toList
    } yield PRSnapshot(pr, labelsResponse)

    def fetchMergedPullRequests()(using repo: Repo): IO[Seq[PRSnapshot]] = {
      val now = ZonedDateTime.now()
      val timeThresholdForScan = now.minus(WorthyOfScanWindow)
      val criteriaForClosedPrsBasedOnTheDefaultBranch: Map[String, String] =
        ClosedPRsMostlyRecentlyUpdated + ("base" -> repo.default_branch)

      def isMergedRecentlyEnoughToBeWorthScanning(pr: PullRequest) =
        pr.merged_at.exists(_.isAfter(timeThresholdForScan))

      for {
        prSnapshots: Seq[PRSnapshot] <-
          repo.pullRequests.list(criteriaForClosedPrsBasedOnTheDefaultBranch)
            .takeWhile(_.updated_at >= timeThresholdForScan)
            .filter(isMergedRecentlyEnoughToBeWorthScanning)
            .take(MaxPRsToScanPerRepo)
            .parEvalMapUnordered(4) {
              pr => prSnapshot(pr.number)
            }.compile.toList
      } yield {
        log(s"PR snapshots size=${prSnapshots.size}")
        prSnapshots
      }
    }

    private def fetchLatestCopyOfGitRepo()(using githubRepo: Repo): IO[Repository] = for {
      creds <- bot.clientWithAccess.credentialsProvider
    } yield {
        val repoId = githubRepo.repoId
        RepoUtil.getGitRepo(
          bot.workingDir.resolve(s"${repoId.owner}/${repoId.name}").toFile,
          githubRepo.clone_url,
          Some(creds.git))
    }

    private def fetchRepoHooks()(using githubRepo: Repo): IO[Seq[Uri]] = 
      if (githubRepo.permissions.exists(_.admin)) githubRepo.hooks.list().collect {
        Function.unlift(hook => hook.config.get("url").map(Uri.unsafeParse))
      }.compile.toList else {
        log(s"No admin rights to check hooks")
        IO.pure(Seq.empty)
      }
  }
}

case class Diagnostic(
 snapshots: Set[CheckpointSnapshot],
 prDetails: Seq[PRCheckpointDetails]
) {
  val snapshotsByCheckpoint: Map[Checkpoint, CheckpointSnapshot] = snapshots.map(s => s.checkpoint -> s).toMap
}


case class RepoLevelDetails(
  repo: Repo,
  gitRepo: Repository,
  hooks: Seq[Uri]
) extends Logging {
  implicit val repoThreadLocal: ThreadLocalObjectDatabaseResources = gitRepo.getObjectDatabase.threadLocalResources

  lazy val mainCommit:RevCommit = {
    val id: ObjectId = gitRepo.resolve(repo.default_branch)
    logger.info(s"Need to look at ${repo.full_name}, branch:${repo.default_branch} commit $id")
    assert(id != null)
    id.asRevCommit(new RevWalk(repoThreadLocal.reader()))
  }

  lazy val config: Config.RepoConfig = ConfigFinder.config(mainCommit)

  def createFileFinder(): FileFinder = new FileFinder(mainCommit)
}

/**
 * Once we have the PRs, we need to know what's active... and then snapshot those checkpoints.
 */
case class RepoSnapshot(
  repoLevelDetails: RepoLevelDetails,
  mergedPullRequestSnapshots: Seq[PRSnapshot],
//  checkpointSnapshoter: CheckpointSnapshoter
) extends Logging {

  val repo: Repo = repoLevelDetails.repo
  val config: Config.RepoConfig = repoLevelDetails.config

  val mergedPRs: Seq[PullRequest] = mergedPullRequestSnapshots.map(_.pr)

  val updateReporters: Seq[UpdateReporter] = Seq.empty

  lazy val affectedFoldersByPullRequest: Map[PullRequest, Set[String]] = {
    implicit val revWalk = new RevWalk(repoLevelDetails.repoThreadLocal.reader())
    (for {
      pr <- mergedPRs
    } yield pr -> GitChanges.affects(pr, repoLevelDetails.config.foldersWithValidConfig)).toMap
  }

  lazy val pullRequestsByAffectedFolder : Map[String, Set[PullRequest]] = repoLevelDetails.config.foldersWithValidConfig.map {
    folder => folder -> mergedPRs.filter(pr => affectedFoldersByPullRequest(pr).contains(folder)).toSet
  }.toMap

  logger.info(s"${repoLevelDetails.repo.full_name} pullRequestsByAffectedFolder : ${pullRequestsByAffectedFolder.mapV(_.map(_.number))}")

  lazy val activeConfByPullRequest: Map[PullRequest, Set[ConfigFile]] = affectedFoldersByPullRequest.mapV {
    _.map(repoLevelDetails.config.validConfigByFolder(_))
  }

  lazy val activeCheckpointsByPullRequest: Map[PullRequest, Set[Checkpoint]] = activeConfByPullRequest.mapV {
    _.flatMap(_.checkpointSet)
  }

  val allAvailableCheckpoints: Set[Checkpoint] = repoLevelDetails.config.checkpointsByName.values.toSet

  val allPossibleCheckpointPRLabels: Set[String] = for {
    prLabel <- PullRequestLabel.all
    checkpoint <- allAvailableCheckpoints
  } yield prLabel.labelFor(checkpoint.name)

//  def diagnostic(): IO[Diagnostic] = for {
//    snapshots <- snapshotOfAllAvailableCheckpoints()
//  } yield Diagnostic(snapshots, mergedPRs.map { pr =>
//    PRCheckpointDetails(pr, snapshots.filter(s => activeCheckpointsByPullRequest(pr).contains(s.checkpoint)), repoLevelDetails.gitRepo)
//  })

//  def snapshotOfAllAvailableCheckpoints(): IO[Set[CheckpointSnapshot]] =
//    allAvailableCheckpoints.parUnorderedTraverse(takeCheckpointSnapshot)

  val activeCheckpoints: Set[Checkpoint] = activeCheckpointsByPullRequest.values.flatten.toSet

//  lazy val snapshotsOfActiveCheckpointsF: Map[Checkpoint, IO[CheckpointSnapshot]] =
//    activeCheckpoints.map { c => c -> takeCheckpointSnapshot(c) }.toMap
//
//  def takeCheckpointSnapshot(checkpoint: Checkpoint): IO[CheckpointSnapshot] = for (
//    result <- checkpointSnapshoter.snapshot(checkpoint)
//  ) yield CheckpointSnapshot(checkpoint, result.map { possibleIds =>
//    LazyList.from(possibleIds).flatMap(repoLevelDetails.repoThreadLocal.reader().resolveExistingUniqueId(_).toOption).headOption
//  })
//
//  def checkpointSnapshotsFor(pr: PullRequest, oldState: PRCheckpointState): IO[Set[CheckpointSnapshot]] =
//    Future.sequence(activeCheckpointsByPullRequest(pr).filter(!oldState.hasSeen(_)).map(snapshotsOfActiveCheckpointsF))

  val labelToStateMapping: LabelMapping[PRCheckpointState] = new LabelMapping[PRCheckpointState] {
    def labelsFor(s: PRCheckpointState): Set[String] = s.statusByCheckpoint.map {
      case (checkpointName, cs) => cs.labelFor(checkpointName)
    }.toSet

    def stateFrom(labels: Set[String]): PRCheckpointState = PRCheckpointState(activeCheckpoints.flatMap { checkpoint =>
      PullRequestCheckpointStatus.fromLabels(labels, checkpoint).map(checkpoint.name -> _)
    }.toMap)
  }
}

