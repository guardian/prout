package lib


import com.github.nscala_time.time.Imports._
import com.madgag.scalagithub.model.PullRequest
import lib.Config.Checkpoint
import lib.gitgithub.StateSnapshot
import lib.labels.{Pending, PullRequestCheckpointStatus, Seen}
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit

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

case class PRCommitVisibility(seen: Set[RevCommit], unseen: Set[RevCommit])






object PRCheckpointDetails {
  def apply(
           pr: PullRequest,
           snapshots: Set[CheckpointSnapshot],
           gitRepo: Repository
           ): PRCheckpointDetails = {

    val everythingByCheckpoint: Map[Checkpoint, EverythingYouWantToKnowAboutACheckpoint] =
      (for (snapsot <- snapshots) yield {
        snapsot.checkpoint -> EverythingYouWantToKnowAboutACheckpoint(pr,snapsot,gitRepo)
      }).toMap

    PRCheckpointDetails(pr,everythingByCheckpoint)
  }
}

case class PRCheckpointDetails(
  pr: PullRequest,
  everythingByCheckpoint: Map[Checkpoint, EverythingYouWantToKnowAboutACheckpoint]
) {
  val checkpointStatusByName = for ((c, e) <- everythingByCheckpoint) yield c.name -> e.checkpointStatus
  val everythingByCheckpointName = for ((c, e) <- everythingByCheckpoint) yield c.name -> e

  val checkpointsByState: Map[PullRequestCheckpointStatus, Set[Checkpoint]] =
    everythingByCheckpoint.values.groupBy(_.checkpointStatus).mapValues(_.map(_.snapshot.checkpoint).toSet)

  val soonestPendingCheckpointOverdueTime: Option[java.time.Instant] = {
    implicit val periodOrdering = Ordering.by[Period, Duration](_.toStandardDuration)

    checkpointsByState.get(Pending).map(_.flatMap(_.details.overdueInstantFor(pr)).min)
  }

}

case class PullRequestCheckpointsStateChangeSummary(
  prCheckpointDetails: PRCheckpointDetails,
  oldState: PRCheckpointState
) extends StateSnapshot[PRCheckpointState] {

  val checkpointStatuses: PRCheckpointState = oldState.updateWith(prCheckpointDetails.checkpointStatusByName)

  override val newPersistableState = checkpointStatuses

  val changed: Set[EverythingYouWantToKnowAboutACheckpoint] =
    checkpointStatuses.changeFrom(oldState).keySet.map(prCheckpointDetails.everythingByCheckpointName)

  val changedByState: Map[PullRequestCheckpointStatus, Set[EverythingYouWantToKnowAboutACheckpoint]] =
    changed.groupBy(_.checkpointStatus)
}
