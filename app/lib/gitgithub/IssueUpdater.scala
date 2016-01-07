package lib.gitgithub

import com.madgag.scalagithub.GitHub._
import com.madgag.scalagithub.model.{PullRequest, Repo}
import lib.{Bot, Delayer, LabelledState}
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object IssueUpdater {
  val logger = Logger(getClass)
}

/**
 *
 * @tparam IssueType Pull Request or Issue
 * @tparam PersistableState State that can be converted to and from GitHub issue labels, ie a set of Strings
 * @tparam Snapshot A present-state snapshot that can yield a PersistableState
 */
trait IssueUpdater[IssueType <: PullRequest, PersistableState, Snapshot <: StateSnapshot[PersistableState]] {

  implicit val github = Bot.github

  val repo: Repo

  val labelToStateMapping:LabelMapping[PersistableState]

  def ignoreItemsWithExistingState(existingState: PersistableState): Boolean

  def snapshot(oldState: PersistableState, issue: IssueType): Future[Snapshot]

  def actionTaker(snapshot: Snapshot)

  def process(issueLike: IssueType): Future[Option[Snapshot]] = {
    logger.trace(s"handling ${issueLike.prId.slug}")
    for {
      oldLabels <- new LabelledState(issueLike, _ => true).currentLabelsF
      snapshot <- takeSnapshotOf(issueLike, oldLabels)
    } yield snapshot
  }

  def takeSnapshotOf(issueLike: IssueType, oldLabels: Set[String]): Future[Option[Snapshot]] = {
    val existingPersistedState: PersistableState = labelToStateMapping.stateFrom(oldLabels)
    if (!ignoreItemsWithExistingState(existingPersistedState)) {
      for (currentSnapshot <- snapshot(existingPersistedState, issueLike)) yield {
        val newPersistableState = currentSnapshot.newPersistableState
        val stateChanged = newPersistableState != existingPersistedState

        logger.debug(s"handling ${issueLike.prId.slug} : state: existing=$existingPersistedState new=$newPersistableState stateChanged=$stateChanged")

        if (stateChanged) {
          logger.info(s"#${issueLike.prId.slug} state-change: $existingPersistedState -> $newPersistableState")
          val newLabels: Set[String] = labelToStateMapping.labelsFor(newPersistableState)
          assert(oldLabels != newLabels, s"Labels should differ for differing states. labels=$oldLabels oldState=$existingPersistedState newState=$newPersistableState")
          issueLike.labels.replace(newLabels.toSeq)
          Delayer.doAfterSmallDelay {
            actionTaker(currentSnapshot)
          }
        }
        Some(currentSnapshot)
      }
    } else Future.successful(None)
  }
}
