package lib


import com.github.nscala_time.time.Imports._
import com.madgag.git._
import lib.Implicits._
import lib.gitgithub.StateSnapshot
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.{RevCommit, RevWalk}
import org.joda.time.DateTime
import org.kohsuke.github.GHPullRequest
import play.api.Logger

import scala.util.Try

case class PRCheckpointState(statusByCheckpoint: Map[String, PullRequestCheckpointStatus]) {
  def hasStateForCheckpointsWhichHaveAllBeenSeen = statusByCheckpoint.nonEmpty && all(Seen)

  def all(s: PullRequestCheckpointStatus) = statusByCheckpoint.values.forall(_ == s)

  def has(s: PullRequestCheckpointStatus) = statusByCheckpoint.values.exists(_ == s)

  def changeFrom(oldState: PRCheckpointState) =
    (statusByCheckpoint.toSet -- oldState.statusByCheckpoint.toSet).toMap

}

case class PullRequestCheckpointsSummary(
  pr: GHPullRequest,
  snapshots: Set[CheckpointSnapshot],
  gitRepo: Repository,
  oldState: PRCheckpointState
) extends StateSnapshot[PRCheckpointState] {

  val snapshotsByName: Map[String, CheckpointSnapshot] = snapshots.map(cs => cs.checkpoint.name -> cs).toMap

  val checkpointStatuses: PRCheckpointState = PRCheckpointState(snapshots.map {
    cs =>
      val timeBetweenMergeAndSnapshot = (new DateTime(pr.getMergedAt) to cs.time).duration

      val isVisibleOnSite: Boolean = Try {
        implicit val w: RevWalk = new RevWalk(gitRepo)
        val prCommit: RevCommit = pr.getHead.asRevCommit
        val siteCommit: RevCommit = cs.commitId.get.asRevCommit
        Logger.trace(s"prCommit=${prCommit.name()} siteCommit=${siteCommit.name()}")

        w.isMergedInto(prCommit, siteCommit)
      }.getOrElse(false)

      val currentStatus: PullRequestCheckpointStatus =
        if (isVisibleOnSite) Seen else if (timeBetweenMergeAndSnapshot > cs.checkpoint.overdue.standardDuration) Overdue else Pending

      cs.checkpoint.name -> currentStatus
  }.toMap)

  override val newPersistableState = checkpointStatuses

  val stateChange: Map[String, PullRequestCheckpointStatus] = checkpointStatuses.changeFrom(oldState)

  val changedSnapshotsByState: Map[PullRequestCheckpointStatus, Seq[CheckpointSnapshot]] =
    stateChange.groupBy(_._2).mapValues(_.keySet).mapValues(checkpointNames => snapshotsByName.filterKeys(checkpointNames).values.toSeq)
}
