package lib.gitgithub

import lib.Delayer
import org.kohsuke.github.{GHPullRequest, GHRepository, GHIssue}
import play.api.Logger
import lib.LabelledState._

trait IssueUpdater[IssueType <: GHIssue, PersistableState, Snapshot <: StateSnapshot[PersistableState]] {

  val repo: GHRepository

  val labelToStateMapping:LabelMapping[PersistableState]

  def ignoreItemsWithExistingState(existingState: PersistableState): Boolean

  def snapshoter(oldState: PersistableState, issue: IssueType): Snapshot

  def actionTaker(snapshot: Snapshot)

  def process(issueLike: IssueType): Option[Snapshot] = {
    Logger.trace(s"handling ${issueLike.getNumber}")
    val issue: GHIssue = issueLike match {
      case pr: GHPullRequest => repo.getIssue(issueLike.getNumber)
      case _ => issueLike
    }

    val oldLabels = issue.labelledState(_ => true).applicableLabels
    val existingPersistedState: PersistableState = labelToStateMapping.stateFrom(oldLabels)
    if (!ignoreItemsWithExistingState(existingPersistedState)) {
      val currentSnapshot = snapshoter(existingPersistedState, issueLike)
      val newPersistableState = currentSnapshot.newPersistableState
      if (newPersistableState != existingPersistedState) {
        Logger.info(s"#${issue.getNumber} state-change: $existingPersistedState -> $newPersistableState")
        val newLabels: Set[String] = labelToStateMapping.labelsFor(newPersistableState)
        assert(oldLabels != newLabels, s"Labels should differ for differing states. labels=$oldLabels oldState=$existingPersistedState newState=$newPersistableState")
        issue.setLabels(newLabels.toSeq: _*)
        Delayer.doAfterSmallDelay {
          actionTaker(currentSnapshot)
        }
      }
      Some(currentSnapshot)
    } else None
  }
}
