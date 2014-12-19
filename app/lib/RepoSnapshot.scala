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

import com.github.nscala_time.time.Imports._
import com.madgag.git._
import lib.Config.Checkpoint
import lib.Implicits._
import lib.RepoSnapshot._
import lib.gitgithub.{IssueUpdater, LabelMapping}
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.joda.time.DateTime
import org.joda.time.format.PeriodFormat
import org.kohsuke.github._
import play.api.Logger

import scala.collection.convert.wrapAsScala._
import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.util.Success
import scalax.file.ImplicitConversions._

object RepoSnapshot {

  val WorthyOfCommentWindow: Duration = 12.hours

  def apply(githubRepo: GHRepository)(implicit checkpointSnapshoter: CheckpointSnapshoter): Future[RepoSnapshot] = {
    val conn = Bot.githubCredentials.conn()

    val repoFullName = RepoFullName(githubRepo.getFullName)

    def isMergedToMaster(pr: GHPullRequest): Boolean = pr.isMerged && pr.getBase.getRef == githubRepo.getMasterBranch

    val mergedPullRequestsF = Future {
      githubRepo.listPullRequests(GHIssueState.CLOSED).iterator().filter(isMergedToMaster).take(50).toList
    } andThen { case cprs => Logger.info(s"Merged Pull Requests fetched: ${cprs.map(_.map(_.getNumber).sorted.reverse)}") }

    val gitRepoF = Future {
      RepoUtil.getGitRepo(
        Bot.parentWorkDir / repoFullName.owner / repoFullName.name,
        githubRepo.gitHttpTransportUrl,
        Some(Bot.githubCredentials.git))
    } andThen { case r => Logger.info(s"Git Repo ref count: ${r.map(_.getAllRefs.size)}") }

    for {
      mergedPullRequests <- mergedPullRequestsF
      gitRepo <- gitRepoF
    } yield RepoSnapshot(githubRepo, gitRepo, mergedPullRequests, checkpointSnapshoter)
  }
}

case class RepoSnapshot(
  repo: GHRepository,
  gitRepo: Repository,
  mergedPullRequests: Seq[GHPullRequest],
  checkpointSnapshoter: CheckpointSnapshoter) {
  self =>

  private implicit val (revWalk, reader) = gitRepo.singleThreadedReaderTuple

  lazy val masterCommit:RevCommit = gitRepo.resolve(repo.getMasterBranch).asRevCommit

  lazy val config = ConfigFinder.config(masterCommit)

  lazy val affectedFoldersByPullRequest: Map[GHPullRequest, Set[String]] = (for {
    pr <- mergedPullRequests
  } yield pr -> pr.affects(config.foldersWithValidConfig).toSet).toMap


  lazy val pullRequestsByAffectedFolder : Map[String, Set[GHPullRequest]] = config.foldersWithValidConfig.map {
    folder => folder -> mergedPullRequests.filter(pr => affectedFoldersByPullRequest(pr).contains(folder)).toSet
  }.toMap

  Logger.info(s"ZZ affectedFoldersByPullRequest=${pullRequestsByAffectedFolder.mapValues(_.map(_.getNumber))}")

  lazy val activeConfigByPullRequest: Map[GHPullRequest, Set[Checkpoint]] = affectedFoldersByPullRequest.mapValues {
    _.map(config.validConfigByFolder(_).checkpointSet).flatten
  }

  val activeConfig: Set[Checkpoint] = activeConfigByPullRequest.values.reduce(_ ++ _)

  lazy val checkpointSnapshotsF: Map[Checkpoint, Future[CheckpointSnapshot]] = activeConfig.map {
    c =>
      c -> {
        for (possibleIds <- checkpointSnapshoter(c)) yield {
          val objectIdOpt = possibleIds.map(reader.resolveExistingUniqueId).collectFirst {
            case Success(objectId) => objectId
          }
          Logger.info(s"${repo.getFullName} ${c.name} has $objectIdOpt")
          CheckpointSnapshot(c, objectIdOpt)
        }
      }
  }.toMap

  lazy val activeSnapshotsF = Future.sequence(activeConfig.map(checkpointSnapshotsF))

  def checkpointSnapshotsFor(pr: GHPullRequest): Future[Set[CheckpointSnapshot]] =
    Future.sequence(activeConfigByPullRequest(pr).map(checkpointSnapshotsF))

  val issueUpdater = new IssueUpdater[GHPullRequest, PRCheckpointState, PullRequestCheckpointsSummary] {
    val repo = self.repo

    val pf=PeriodFormat.getDefault()

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

    def snapshot(oldState: PRCheckpointState, pr: GHPullRequest) =
      for (cs <- checkpointSnapshotsFor(pr)) yield PullRequestCheckpointsSummary(pr, cs, gitRepo, oldState)

    override def actionTaker(snapshot: PullRequestCheckpointsSummary) {
      if ((new DateTime(snapshot.pr.getMergedAt) to DateTime.now).duration < WorthyOfCommentWindow) {
        println(snapshot.changedSnapshotsByState)

        val mergedBy = snapshot.pr.getMergedBy.atLogin
        val timeSinceMerge = (new DateTime(snapshot.pr.getMergedAt) to DateTime.now).toPeriod.withMillis(0).toString(pf)
        val mergedText = s"(merged by $mergedBy $timeSinceMerge ago)"

        def commentOn(status: PullRequestCheckpointStatus, advice: String) = {
          for (changedSnapshots <- snapshot.changedSnapshotsByState.get(status)) {
            snapshot.pr.comment(status.name  + " on " + changedSnapshots.map(_.checkpoint.nameMarkdown).mkString(", ")+" "+mergedText+". "+advice)
          }
        }

        commentOn(Seen, "Please check your changes!")
        commentOn(Overdue, "What's gone wrong?")
      }
    }
  }


}
