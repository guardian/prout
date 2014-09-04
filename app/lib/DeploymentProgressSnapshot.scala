package lib

import com.github.nscala_time.time.Imports._
import com.madgag.git._
import lib.LabelledState._
import org.eclipse.jgit.revwalk.{RevCommit, RevWalk}
import org.joda.time.DateTime
import org.kohsuke.github.GHPullRequest
import play.api.Logger

import scala.collection.convert.wrapAsScala._


sealed trait PullRequestDeploymentStatus {
  def labelFor(site: Site) = getClass.getSimpleName.dropRight(1) + " on " + site.label
}

object PullRequestDeploymentStatus {
  val all = Set[PullRequestDeploymentStatus](Seen, Pending, Overdue)

  def fromLabels(labels: Set[String], site: Site): Option[PullRequestDeploymentStatus] =
    PullRequestDeploymentStatus.all.find(s => labels(s.labelFor(site)))
}

sealed trait NotSeenOnSite extends PullRequestDeploymentStatus

case object Seen extends PullRequestDeploymentStatus

case object Pending extends NotSeenOnSite

case object Overdue extends NotSeenOnSite


case class DeploymentProgressSnapshot(repoSnapshot: RepoSnapshot, siteSnapshot: SiteSnapshot) {

  val OverdueThreshold = 15.minutes

  val WorthyOfCommentWindow = 6.hours

  def goCrazy()= {
    repoSnapshot.mergedPullRequests.par.foreach(handlePR)
  }

  def handlePR(pr : GHPullRequest) {
    Logger.info(s"handling ${pr.getNumber}")
    val issueHack = repoSnapshot.repo.getIssue(pr.getNumber)
    val labelledState = issueHack.labelledState(_ => true)
    val existingState = PullRequestDeploymentStatus.fromLabels(labelledState.applicableLabels, siteSnapshot.site).getOrElse(Pending)

    if (existingState != Seen) {
      val timeSinceMerge = (new DateTime(pr.getMergedAt) to siteSnapshot.time).duration

      val currentState = if (isVisibleOnSite(pr)) Seen else if (timeSinceMerge > OverdueThreshold) Overdue else Pending

      // update labels before comments - looks better on pull request page
      labelledState.updateLabels(Set(currentState.labelFor(siteSnapshot.site)))

      if (timeSinceMerge < WorthyOfCommentWindow) {
        Logger.info("Totally worthy of comment!")
        currentState match {
          case Pending => Logger.info("Still waiting for this one")
          case Seen =>
            // Logger.info("This pull request has been seen on-site!")
            pr.comment(views.html.ghIssues.seen(siteSnapshot.site, timeSinceMerge).body)
          case Overdue =>
            Logger.info("Overdue!")
            // pr.comment(views.html.ghIssues.overdue(siteSnapshot.site, timeSinceMerge).body)
        }
      }


    }
  }

  def isVisibleOnSite(pr: GHPullRequest): Boolean = {
    implicit val w: RevWalk = new RevWalk(repoSnapshot.gitRepo)
    val prCommit: RevCommit = pr.getHead.getSha.asObjectId.asRevCommit
    val siteCommit: RevCommit = siteSnapshot.commitId.get.asRevCommit

    val isVisible = w.isMergedInto(prCommit,siteCommit)

    Logger.info(s"prCommit=${prCommit.name()} siteCommit=${siteCommit.name()} isVisible=$isVisible")
    isVisible
  }
}
