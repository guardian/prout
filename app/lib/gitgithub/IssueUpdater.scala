package lib.gitgithub

import lib.Delayer
import org.kohsuke.github.{GHPullRequest, GHRepository, GHIssue}
import play.api.Logger
import lib.Implicits._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

/**
 *
 * @tparam IssueType Pull Request or Issue
 * @tparam PersistableState State that can be converted to and from GitHub issue labels, ie a set of Strings
 * @tparam Snapshot A present-state snapshot that can yield a PersistableState
 */
trait IssueUpdater[IssueType <: GHIssue, PersistableState, Snapshot <: StateSnapshot[PersistableState]] {

  val repo: GHRepository

  val labelToStateMapping:LabelMapping[PersistableState]

  def ignoreItemsWithExistingState(existingState: PersistableState): Boolean

  def snapshot(oldState: PersistableState, issue: IssueType): Future[Snapshot]

  def actionTaker(snapshot: Snapshot)

  def process(issueLike: IssueType): Future[Option[Snapshot]] = {
    Logger.info(s"handling ${issueLike.getNumber}")
    val issue: GHIssue = issueLike match {
      case pr: GHPullRequest => repo.getIssue(issueLike.getNumber)
      case _ => issueLike
    }

    val oldLabels = issue.labelledState(_ => true).applicableLabels
    val existingPersistedState: PersistableState = labelToStateMapping.stateFrom(oldLabels)
    if (!ignoreItemsWithExistingState(existingPersistedState)) {
      for (currentSnapshot <- snapshot(existingPersistedState, issueLike)) yield {
        val newPersistableState = currentSnapshot.newPersistableState
        Logger.info(s"handling ${issueLike.getNumber} : $currentSnapshot state: existing=$existingPersistedState new=$newPersistableState")

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
      }
    } else Future.successful(None)
  }
}
