package lib


import com.github.nscala_time.time.Imports._
import com.madgag.git._
import lib.Config.Checkpoint
import lib.Implicits._
import lib.gitgithub.StateSnapshot
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.{RevCommit, RevWalk}
import org.joda.time.{DateTime, Instant}
import org.kohsuke.github.GHPullRequest
import play.api.Logger

import scala.util.Try

case class PRCheckpointState(statusByCheckpoint: Map[String, PullRequestCheckpointStatus]) {
  val states = statusByCheckpoint.values.toSet

  val hasStateForCheckpointsWhichHaveAllBeenSeen = states == Set(Seen)

  def all(s: PullRequestCheckpointStatus) = states.forall(_ == s)

  def has(s: PullRequestCheckpointStatus) = states.contains(s)

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

      val isVisibleOnSite: Boolean = (for (commitId <- cs.commitIdTry) yield {
        implicit val w: RevWalk = new RevWalk(gitRepo)
        val prCommit: RevCommit = pr.getHead.asRevCommit
        val siteCommit: RevCommit = commitId.get.asRevCommit
        Logger.trace(s"prCommit=${prCommit.name()} siteCommit=${siteCommit.name()}")

        w.isMergedInto(prCommit, siteCommit)
      }).getOrElse(false)

      val currentStatus: PullRequestCheckpointStatus =
        if (isVisibleOnSite) Seen else if (timeBetweenMergeAndSnapshot > cs.checkpoint.overdue.standardDuration) Overdue else Pending

      cs.checkpoint.name -> currentStatus
  }.toMap)

  override val newPersistableState = checkpointStatuses

  implicit val periodOrdering = Ordering.by[Period, Duration](_.toStandardDuration)

  val checkpointsByState: Map[PullRequestCheckpointStatus, Set[Checkpoint]] =
    checkpointStatuses.statusByCheckpoint.groupBy(_._2).mapValues(_.keySet.map(tuple => snapshotsByName(tuple).checkpoint))

  val soonestPendingCheckpointOverdueTime: Option[Instant] =
    checkpointsByState.get(Pending).map(_.map(_.details.overdue).min).map(new Instant(pr.getMergedAt) + _.standardDuration)

  val stateChange: Map[String, PullRequestCheckpointStatus] = checkpointStatuses.changeFrom(oldState)

  val changedSnapshotsByState: Map[PullRequestCheckpointStatus, Seq[CheckpointSnapshot]] =
    stateChange.groupBy(_._2).mapValues(_.keySet).mapValues(checkpointNames => snapshotsByName.filterKeys(checkpointNames).values.toSeq)
}
