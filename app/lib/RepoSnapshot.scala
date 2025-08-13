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

import org.apache.pekko.stream.Materializer
import com.madgag.git._
import com.madgag.github.Implicits._
import com.madgag.scala.collection.decorators._
import com.madgag.scalagithub.GitHub
import com.madgag.scalagithub.GitHub._
import com.madgag.scalagithub.model.{PullRequest, Repo, RepoId}
import com.madgag.time.Implicits._
import io.lemonlabs.uri.Url
import lib.Config.Checkpoint
import lib.gitgithub.LabelMapping
import lib.labels._
import org.apache.pekko.actor.ActorSystem
import org.eclipse.jgit.lib.{ObjectId, Repository}
import org.eclipse.jgit.revwalk.{RevCommit, RevWalk}
import play.api.Logging

import java.time.ZonedDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
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
    implicit val github: GitHub = bot.github

    def log(message: String)(implicit repo: Repo): Unit = logger.info(s"${repo.full_name} - $message")
    def logAround[T](desc: String)(thunk: => Future[T])(implicit repo: Repo): Future[T] = {
      val start = System.currentTimeMillis()
      val fut = thunk // evaluate thunk, evaluate only once!
      fut.onComplete { attempt =>
        val elapsedMs = System.currentTimeMillis() - start
        log(s"'$desc' $elapsedMs ms : success=${attempt.isSuccess}")
      }
      fut
    }

    def isMergedToMain(pr: PullRequest)(implicit repo: Repo): Boolean =
      pr.merged_at.isDefined && pr.base.ref == repo.default_branch

    def snapshot(repoId: RepoId): Future[RepoSnapshot] = for {
      githubRepo <- github.getRepo(repoId)
      repoSnapshot <- snapshot(githubRepo)
    } yield repoSnapshot

    def snapshot(implicit githubRepo: Repo): Future[RepoSnapshot] = {
      val mergedPullRequestsF = logAround("fetch PRs")(fetchMergedPullRequests())
      val hooksF = logAround("fetch repo hooks")(fetchRepoHooks())
      val gitRepoF = logAround("fetch git repo")(fetchLatestCopyOfGitRepo())

      for {
        mergedPullRequests <- mergedPullRequestsF
        gitRepo <- gitRepoF
        hooks <- hooksF
      } yield RepoSnapshot(
        RepoLevelDetails(githubRepo, gitRepo, hooks),
        mergedPullRequests, checkpointSnapshoter
      )
    }

    def prSnapshot(prNumber: Int)(implicit repo: Repo): Future[PRSnapshot] = for {
      prResponse <- repo.pullRequests.get(prNumber)
      pr = prResponse.result
      labelsResponse <- pr.labels.list().all()
    } yield PRSnapshot(pr, labelsResponse)

    def fetchMergedPullRequests()(implicit repo: Repo): Future[Seq[PRSnapshot]] = {
      val now = ZonedDateTime.now()
      val timeThresholdForScan = now.minus(WorthyOfScanWindow)

      def isNewEnoughToBeWorthScanning(pr: PullRequest) = pr.merged_at.exists(_.isAfter(timeThresholdForScan))

      (for {
        litePullRequests: Seq[PullRequest] <-
          repo.pullRequests.list(ClosedPRsMostlyRecentlyUpdated).take(2).all(): Future[Seq[PullRequest]]
        pullRequests <-
          Future.traverse(litePullRequests.filter(isMergedToMain).filter(isNewEnoughToBeWorthScanning).take(MaxPRsToScanPerRepo))(pr => prSnapshot(pr.number))
      } yield {
        log(s"PRs merged to master size=${pullRequests.size}")
        pullRequests
      }) andThen { case cprs => log(s"Merged Pull Requests fetched: ${cprs.map(_.map(_.pr.number).sorted.reverse)}") }
    }

    private def fetchLatestCopyOfGitRepo()(implicit githubRepo: Repo): Future[Repository] = (for {
      creds <- bot.gitHubCredsProvider()
    } yield {
      val repoId = githubRepo.repoId
      RepoUtil.getGitRepo(
        bot.workingDir.resolve(s"${repoId.owner}/${repoId.name}").toFile,
        githubRepo.clone_url,
        Some(creds.git))
    }) andThen { case r => log(s"Git Repo ref count: ${r.map(_.getRefDatabase.getRefs.size)}") }

    private def fetchRepoHooks()(implicit githubRepo: Repo) = if (githubRepo.permissions.exists(_.admin)) githubRepo.hooks.list().map(_.flatMap(_.config.get("url").map(Url.parse))).all() else {
      log(s"No admin rights to check hooks")
      Future.successful(Seq.empty)
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
  hooks: Seq[Url]
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

case class RepoSnapshot(
  repoLevelDetails: RepoLevelDetails,
  mergedPullRequestSnapshots: Seq[PRSnapshot],
  checkpointSnapshoter: CheckpointSnapshoter
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

  def diagnostic(): Future[Diagnostic] = for {
    snapshots <- snapshotOfAllAvailableCheckpoints()
  } yield Diagnostic(snapshots, mergedPRs.map { pr =>
    PRCheckpointDetails(pr, snapshots.filter(s => activeCheckpointsByPullRequest(pr).contains(s.checkpoint)), repoLevelDetails.gitRepo)
  })

  def snapshotOfAllAvailableCheckpoints(): Future[Set[CheckpointSnapshot]] =
    Future.sequence(allAvailableCheckpoints.map(takeCheckpointSnapshot))

  val activeCheckpoints: Set[Checkpoint] = activeCheckpointsByPullRequest.values.flatten.toSet

  lazy val snapshotsOfActiveCheckpointsF: Map[Checkpoint, Future[CheckpointSnapshot]] =
    activeCheckpoints.map { c => c -> takeCheckpointSnapshot(c) }.toMap

  def takeCheckpointSnapshot(checkpoint: Checkpoint): Future[CheckpointSnapshot] = for (
    possibleIdsTry <- checkpointSnapshoter.snapshot(checkpoint).trying
  ) yield {
    val objectIdTry = for (possibleIds <- possibleIdsTry) yield {
      possibleIds.map(repoLevelDetails.repoThreadLocal.reader().resolveExistingUniqueId).collectFirst {
        case Success(objectId) => objectId
      }
    }
    CheckpointSnapshot(checkpoint, objectIdTry)
  }

  lazy val activeSnapshotsF: Future[Set[CheckpointSnapshot]] =
    Future.sequence(activeCheckpoints.map(snapshotsOfActiveCheckpointsF))

  def checkpointSnapshotsFor(pr: PullRequest, oldState: PRCheckpointState): Future[Set[CheckpointSnapshot]] =
    Future.sequence(activeCheckpointsByPullRequest(pr).filter(!oldState.hasSeen(_)).map(snapshotsOfActiveCheckpointsF))

  val labelToStateMapping: LabelMapping[PRCheckpointState] = new LabelMapping[PRCheckpointState] {
    def labelsFor(s: PRCheckpointState): Set[String] = s.statusByCheckpoint.map {
      case (checkpointName, cs) => cs.labelFor(checkpointName)
    }.toSet

    def stateFrom(labels: Set[String]): PRCheckpointState = PRCheckpointState(activeCheckpoints.flatMap { checkpoint =>
      PullRequestCheckpointStatus.fromLabels(labels, checkpoint).map(checkpoint.name -> _)
    }.toMap)
  }
}

