package lib


import com.github.nscala_time.time.Imports._
import com.madgag.git._
import lib.Config.Checkpoint
import lib.Implicits._
import lib.gitgithub.{LabelMapping, IssueUpdater, StateSnapshot}
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.{RevCommit, RevWalk}
import org.joda.time.DateTime
import org.kohsuke.github.GHPullRequest
import play.api.Logger
import play.twirl.api.Html

import scala.concurrent.Future

case class PullRequestCheckpointsSummary(pr: GHPullRequest, snapshots: Set[CheckpointSnapshot], gitRepo: Repository) extends StateSnapshot[Map[String, PullRequestCheckpointStatus]] {

  val checkpointStatuses: Map[String, PullRequestCheckpointStatus] = snapshots.map {
    cs =>
      val timeBetweenMergeAndSnapshot = (new DateTime(pr.getMergedAt) to cs.time).duration

      val isVisibleOnSite: Boolean = {
        implicit val w: RevWalk = new RevWalk(gitRepo)
        val prCommit: RevCommit = pr.getHead.asRevCommit
        val siteCommit: RevCommit = cs.commitId.get.asRevCommit

        val isVisible = w.isMergedInto(prCommit, siteCommit)

        Logger.trace(s"prCommit=${prCommit.name()} siteCommit=${siteCommit.name()} isVisible=$isVisible")

        isVisible
      }

      val currentStatus: PullRequestCheckpointStatus =
        if (isVisibleOnSite) Seen else if (timeBetweenMergeAndSnapshot > cs.checkpoint.overdue.standardDuration) Overdue else Pending

      cs.checkpoint.name -> currentStatus
  }.toMap


  override val newPersistableState = checkpointStatuses

  val WorthyOfCommentWindow = 12.hours

  def handlePR: Future[] = Future { issueUpdater.process(pr) }

  def messageOptFor() = {
    val boo: PartialFunction[PullRequestCheckpointStatus, Html] = {
      case Seen =>
        views.html.ghIssues.seen(prsc)
      case Overdue =>
        views.html.ghIssues.overdue(prsc)
    }

    boo.lift(newPersistableState).map(_.body.replace("\n", ""))
  }

  val issueUpdater = new IssueUpdater[GHPullRequest, PullRequestCheckpointsSummary, Map[String, PullRequestCheckpointStatus]] {
    val repo = gitRepo

    val labelToStateMapping = new LabelMapping[Map[String, PullRequestCheckpointStatus]] {
      def labelsFor(s: Map[String, PullRequestCheckpointStatus]): Set[String] = s.map {
          case (checkpointName, cs) => cs.labelFor(checkpointName)
        }.toSet

      def stateFrom(labels: Set[String]): Map[String, PullRequestCheckpointStatus] =
        snapshots.map(cs => cs.checkpoint.name -> PullRequestCheckpointStatus.fromLabels(labels, cs.checkpoint).getOrElse(Pending)).toMap
    }

    def ignoreItemsWithExistingState(existingState: PullRequestCheckpointStatus) = existingState == Seen

    override def snapshoter(oldState: Map[String, PullRequestCheckpointStatus], pr: GHPullRequest) = this

    override def actionTaker(prcs: Map[String, PullRequestCheckpointStatus]) = {
      if ((new DateTime(pr.getMergedAt) to DateTime.now).duration < WorthyOfCommentWindow) {


        //        for (message <- messageOptFor(prsc)) {
        //          Logger.info("Normally I would be saying " + prsc.pr.getNumber+" : "+message)
        //          // prsc.pr.comment(message)
        //        }
      }
    }
  }
}
