package lib

import java.net.URL

import com.madgag.github.RepoId
import com.squareup.okhttp.OkHttpClient
import lib.Implicits._
import lib.gitgithub.GitHubCredentials
import org.eclipse.jgit.lib.{AbbreviatedObjectId, ObjectId}
import org.kohsuke.github.GHIssueState.OPEN
import org.kohsuke.github._
import org.scalatest.Inspectors
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatestplus.play._

import scala.collection.convert.wrapAll._
import scala.concurrent.Future

case class PRText(title: String, desc: String)

trait Helpers extends PlaySpec with OneAppPerSuite with Inspectors with ScalaFutures with Eventually {

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(12, Seconds)), interval = scaled(Span(850, Millis)))

  val githubToken = sys.env("PROUT_GITHUB_ACCESS_TOKEN")

  val githubCredentials = new GitHubCredentials(githubToken, new OkHttpClient)

  val slackWebhookUrlOpt = sys.env.get("PROUT_TEST_SLACK_WEBHOOK").map(new URL(_))

  def conn(): GitHub = githubCredentials.conn()

  case class RepoPR(pr: GHPullRequest) {
    val githubRepo = pr.getRepository

    def getIssue(): GHIssue = githubRepo.getIssue(pr.getNumber)

    var checkpointCommitFuture: Future[Iterator[AbbreviatedObjectId]] = Future.successful(Iterator.empty)

    def setCheckpointTo(commitId: AbbreviatedObjectId) {
      checkpointCommitFuture = Future.successful(Iterator(commitId))
    }

    def setCheckpointTo(objectId: ObjectId) {
      setCheckpointTo(AbbreviatedObjectId.fromObjectId(objectId))
    }

    def setCheckpointTo(branchName: String) {
      setCheckpointTo(AbbreviatedObjectId.fromString(githubRepo.getBranches()(branchName).getSHA1))
    }

    def setCheckpointFailureTo(exception: Exception) {
      checkpointCommitFuture = Future.failed(exception)
    }

    val checkpointSnapshoter: CheckpointSnapshoter = _ => checkpointCommitFuture

    val scheduler = new ScanScheduler(RepoId.from(githubRepo.getFullName), checkpointSnapshoter, conn())
  }

  def scan[T](shouldAddComment: Boolean)(issueFun: GHIssue => T)(implicit repoPR: RepoPR) {
    val commentCountBeforeScan = repoPR.getIssue().getCommentsCount
    whenReady(repoPR.scheduler.scan()) { s =>
      eventually {
        val issueAfterScan = repoPR.getIssue()
        issueAfterScan.getCommentsCount must be(commentCountBeforeScan+(if (shouldAddComment) 1 else 0))
        issueFun(issueAfterScan)
      }
    }
  }

  def scanUntil[T](shouldAddComment: Boolean)(issueFun: GHIssue => T)(implicit repoPR: RepoPR) {
    val commentCountBeforeScan = repoPR.getIssue().getCommentsCount
    eventually {
      whenReady(repoPR.scheduler.scan()) { s =>
        val issueAfterScan = repoPR.getIssue()
        issueAfterScan.getCommentsCount must be(commentCountBeforeScan + (if (shouldAddComment) 1 else 0))
        issueFun(issueAfterScan)
      }
    }
  }

  def waitUntil[T](shouldAddComment: Boolean)(issueFun: GHIssue => T)(implicit repoPR: RepoPR) {
    val commentCountBeforeScan = repoPR.getIssue().getCommentsCount
    eventually {
      val currentIssue = repoPR.getIssue()
      currentIssue.getCommentsCount must be(commentCountBeforeScan + (if (shouldAddComment) 1 else 0))
      issueFun(currentIssue)
    }
  }

  def scanShouldNotChangeAnything[T,S]()(implicit meat: RepoPR) {
    scanShouldNotChange { issue => (issue.labelNames, issue.getCommentsCount) }
  }

  def scanShouldNotChange[T,S](issueState: GHIssue => S)(implicit repoPR: RepoPR) {
    val issueBeforeScan = repoPR.getIssue()
    val beforeState = issueState(issueBeforeScan)

    for (check <- 1 to 3) {
      whenReady(repoPR.scheduler.scan()) { s =>
        issueState(repoPR.getIssue()) must equal(beforeState)
      }
    }
  }

  def mergePullRequestIn(githubRepo: GHRepository, merging: String, prText: PRText = PRText("title", "desc")) = {

    eventually {
      githubRepo.getBranches must contain key merging
      githubRepo.getPullRequests(OPEN) mustBe empty
    }

    val pr = githubRepo.createPullRequest(prText.title, merging, "master", prText.desc)

    eventually(githubRepo.getPullRequest(pr.getNumber).getHead.getRef must be(merging))

    eventually(pr.merge("Go for it"))

    RepoPR(pr)
  }

  def defaultBranchShaFor(githubRepo: GHRepository): String = {
    githubRepo.getRef("heads/" + githubRepo.getDefaultBranch).getObject.getSha
  }
}