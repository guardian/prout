package lib


import com.github.nscala_time.time.Imports._
import com.madgag.git._
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.{RevCommit, RevWalk}
import org.joda.time.DateTime
import org.kohsuke.github.GHPullRequest
import play.api.Logger

case class PullRequestSiteCheck(pr: GHPullRequest, siteSnapshot: SiteSnapshot, gitRepo: Repository) {

  val site = siteSnapshot.site

  val OverdueThreshold = 15.minutes

  val timeSinceMerge = (new DateTime(pr.getMergedAt) to siteSnapshot.time).duration

  val isVisibleOnSite: Boolean = {
    implicit val w: RevWalk = new RevWalk(gitRepo)
    val prCommit: RevCommit = pr.getHead.getSha.asObjectId.asRevCommit
    val siteCommit: RevCommit = siteSnapshot.commitId.get.asRevCommit

    val isVisible = w.isMergedInto(prCommit,siteCommit)

    Logger.trace(s"prCommit=${prCommit.name()} siteCommit=${siteCommit.name()} isVisible=$isVisible")

    isVisible
  }

  val currentState = if (isVisibleOnSite) Seen else if (timeSinceMerge > OverdueThreshold) Overdue else Pending

  val label = currentState.labelFor(siteSnapshot.site)
}
