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

//
//  def messageOptFor() = {
//    val boo: PartialFunction[PullRequestCheckpointStatus, Html] = {
//      case Seen =>
//        views.html.ghIssues.seen(prsc)
//      case Overdue =>
//        views.html.ghIssues.overdue(prsc)
//    }
//
//    boo.lift(newPersistableState).map(_.body.replace("\n", ""))
//  }

}
