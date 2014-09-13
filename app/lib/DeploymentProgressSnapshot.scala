package lib

import com.github.nscala_time.time.Imports._
import com.madgag.git._
import lib.gitgithub.{LabelMapping, IssueUpdater}
import org.eclipse.jgit.revwalk.{RevCommit, RevWalk}
import org.kohsuke.github.GHPullRequest
import play.api.Logger
import play.twirl.api.Html

import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


case class DeploymentProgressSnapshot(repoSnapshot: RepoSnapshot, siteSnapshot: SiteSnapshot) {

  val OverdueThreshold = 15.minutes

  val WorthyOfCommentWindow = 6.hours

  def goCrazy(): Future[Seq[PullRequestSiteCheck]] = Future.traverse(repoSnapshot.mergedPullRequests)(handlePR).map(_.flatten.toSeq)

  def handlePR(pr : GHPullRequest): Future[Option[PullRequestSiteCheck]] = Future { issueUpdater.process(pr) }

  def messageOptFor(prsc: PullRequestSiteCheck) = {
    val boo: PartialFunction[PullRequestDeploymentStatus, Html] = {
      case Seen =>
        views.html.ghIssues.seen(prsc)
      case Overdue =>
        views.html.ghIssues.overdue(prsc)
    }

    boo.lift(prsc.newPersistableState).map(_.body.replace("\n", ""))
  }

  val issueUpdater = new IssueUpdater[GHPullRequest, PullRequestDeploymentStatus, PullRequestSiteCheck] {
    val labelToStateMapping = new LabelMapping[PullRequestDeploymentStatus] {
      def labelsFor(s: PullRequestDeploymentStatus): Set[String] = Set(s.labelFor(siteSnapshot.site))

      def stateFrom(labels: Set[String]): PullRequestDeploymentStatus =
        PullRequestDeploymentStatus.fromLabels(labels, siteSnapshot.site).getOrElse(Pending)
    }

    def ignoreItemsWithExistingState(existingState: PullRequestDeploymentStatus) = existingState == Seen

    def snapshoter(oldState: PullRequestDeploymentStatus, pr: GHPullRequest) =
      PullRequestSiteCheck(pr, siteSnapshot, repoSnapshot.gitRepo)

    def actionTaker(prsc: PullRequestSiteCheck) = {
      Logger.debug(prsc.pr.getNumber+" "+messageOptFor(prsc).toString)
      if (prsc.timeSinceMerge < WorthyOfCommentWindow) {
        for (message <- messageOptFor(prsc)) {
          Logger.info("Normally I would be saying " + prsc.pr.getNumber+" : "+message)
          // prsc.pr.comment(message)
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


