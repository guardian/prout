package lib.gitgithub

import lib.Delayer
import org.kohsuke.github.GHIssue
import play.api.Logger
import lib.LabelledState._

trait IssueUpdater[IssueType <: GHIssue, PersistableState, Snapshot <: StateSnapshot[PersistableState]] {

  val labelToStateMapping:LabelMapping[PersistableState]

  def ignoreItemsWithExistingState(existingState: PersistableState): Boolean

  def snapshoter(oldState: PersistableState, issue: IssueType): Snapshot

  def actionTaker(snapshot: Snapshot)

  def process(issue: IssueType): Option[Snapshot] = {
    Logger.trace(s"handling ${issue.getNumber}")
    val oldLabels = issue.labelledState(_ => true).applicableLabels
    val existingPersistedState: PersistableState = labelToStateMapping.stateFrom(oldLabels)
    if (!ignoreItemsWithExistingState(existingPersistedState)) {
      val currentSnapshot = snapshoter(existingPersistedState, issue)
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
