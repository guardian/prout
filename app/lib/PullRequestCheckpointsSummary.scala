package lib


import java.time.Instant.now

import com.github.nscala_time.time.Imports._
import com.madgag.git._
import com.madgag.scalagithub.model.PullRequest
import lib.Config.Checkpoint
import lib.gitgithub.StateSnapshot
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.{RevCommit, RevWalk}
import play.api.Logger

case class PRCheckpointState(statusByCheckpoint: Map[String, PullRequestCheckpointStatus]) {

  val checkpointsByStatus = statusByCheckpoint.groupBy(_._2).mapValues(_.keySet).withDefaultValue(Set.empty)

  def hasSeen(checkpoint: Checkpoint) = checkpointsByStatus(Seen).contains(checkpoint.name)

  def updateWith(newCheckpointStatus: Map[String, PullRequestCheckpointStatus]) =
    PRCheckpointState(newCheckpointStatus ++ statusByCheckpoint.filterKeys(checkpointsByStatus(Seen)))

  val states = checkpointsByStatus.keySet

  val hasStateForCheckpointsWhichHaveAllBeenSeen = states == Set(Seen)

  def all(s: PullRequestCheckpointStatus) = states.forall(_ == s)

  def has(s: PullRequestCheckpointStatus) = states.contains(s)

  def changeFrom(oldState: PRCheckpointState) =
    (statusByCheckpoint.toSet -- oldState.statusByCheckpoint.toSet).toMap

}

object PullRequestCheckpointsSummary {
  val logger = Logger(getClass)
}

case class PullRequestCheckpointsSummary(
  pr: PullRequest,
  snapshots: Set[CheckpointSnapshot],
  gitRepo: Repository,
  oldState: PRCheckpointState
) extends StateSnapshot[PRCheckpointState] {

  val snapshotsByName: Map[String, CheckpointSnapshot] = snapshots.map(cs => cs.checkpoint.name -> cs).toMap

  private val stringToCheckpointStatus: Map[String, PullRequestCheckpointStatus] = snapshots.map {
    cs =>
      val timeBetweenMergeAndSnapshot = java.time.Duration.between(pr.merged_at.get.toInstant, cs.time)

      val isVisibleOnSite: Boolean = (for (commitId <- cs.commitIdTry) yield {
        implicit val w: RevWalk = new RevWalk(gitRepo)
        val prCommit: RevCommit = pr.head.asRevCommit
        val siteCommit: RevCommit = commitId.get.asRevCommit
        Logger.trace(s"prCommit=${prCommit.name()} siteCommit=${siteCommit.name()}")

        w.isMergedInto(prCommit, siteCommit)
      }).getOrElse(false)

      val currentStatus: PullRequestCheckpointStatus =
        if (isVisibleOnSite) Seen else {
          val overdueThreshold = cs.checkpoint.overdueInstantFor(pr)
          if (overdueThreshold.exists(_ isBefore now)) Overdue else Pending
        }

      cs.checkpoint.name -> currentStatus
  }.toMap

  val checkpointStatuses: PRCheckpointState = oldState.updateWith(stringToCheckpointStatus)

  override val newPersistableState = checkpointStatuses

  implicit val periodOrdering = Ordering.by[Period, Duration](_.toStandardDuration)

  val checkpointsByState: Map[PullRequestCheckpointStatus, Set[Checkpoint]] =
    checkpointStatuses.statusByCheckpoint.groupBy(_._2).mapValues(_.keySet.map(tuple => snapshotsByName(tuple).checkpoint))

  val soonestPendingCheckpointOverdueTime: Option[java.time.Instant] =
    checkpointsByState.get(Pending).map(_.flatMap(_.details.overdueInstantFor(pr)).min)

  val stateChange: Map[String, PullRequestCheckpointStatus] = checkpointStatuses.changeFrom(oldState)

  val changedSnapshotsByState: Map[PullRequestCheckpointStatus, Seq[CheckpointSnapshot]] =
    stateChange.groupBy(_._2).mapValues(_.keySet).mapValues(checkpointNames => snapshotsByName.filterKeys(checkpointNames).values.toSeq)
}
