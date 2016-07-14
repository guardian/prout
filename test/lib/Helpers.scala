package lib

import java.net.URL

import com.madgag.scalagithub.GitHub._
import com.madgag.scalagithub.commands.{CreatePullRequest, MergePullRequest}
import com.madgag.scalagithub.model._
import com.madgag.scalagithub.{GitHub, GitHubCredentials}
import org.eclipse.jgit.lib.{AbbreviatedObjectId, ObjectId}
import org.scalatest.Inspectors
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatestplus.play._
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scalax.file.ImplicitConversions._
import scalax.file.Path

case class PRText(title: String, desc: String)

trait Helpers extends PlaySpec with OneAppPerSuite with Inspectors with ScalaFutures with Eventually {

  val logger = Logger(getClass)

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(12, Seconds)), interval = scaled(Span(850, Millis)))

  val githubToken = sys.env("PROUT_GITHUB_ACCESS_TOKEN")

  val githubCredentials = GitHubCredentials.forAccessKey(githubToken, Path.createTempDirectory().toPath).get

  val slackWebhookUrlOpt = sys.env.get("PROUT_TEST_SLACK_WEBHOOK").map(new URL(_))

  implicit lazy val github = new GitHub(githubCredentials)

  def labelsOn(pr: PullRequest): Set[String] =
    pr.labels.list().all().futureValue.map(_.name).toSet

  case class RepoPR(pr: PullRequest) {
    val githubRepo = pr.baseRepo

    def currentPR(): PullRequest = githubRepo.pullRequests.get(pr.number).futureValue

    def listComments(): Seq[Comment] = pr.comments2.list().all().futureValue

    var checkpointCommitFuture: Future[Iterator[AbbreviatedObjectId]] = Future.successful(Iterator.empty)

    def setCheckpointTo(commitId: AbbreviatedObjectId) {
      checkpointCommitFuture = Future.successful(Iterator(commitId))
    }

    def setCheckpointTo(objectId: ObjectId) {
      setCheckpointTo(AbbreviatedObjectId.fromObjectId(objectId))
    }

    def setCheckpointTo(branchName: String) {
      val objectId = githubRepo.refs.get(s"heads/$branchName").futureValue.objectId
      setCheckpointTo(objectId)
      logger.info(s"Set checkpoint to '$branchName' (${objectId.name.take(8)})")
    }

    def setCheckpointFailureTo(exception: Exception) {
      checkpointCommitFuture = Future.failed(exception)
    }

    val checkpointSnapshoter: CheckpointSnapshoter = _ => checkpointCommitFuture

    val scheduler = new ScanScheduler(githubRepo.repoId, checkpointSnapshoter, github)
  }

  def scan[T](shouldAddComment: Boolean)(issueFun: PullRequest => T)(implicit repoPR: RepoPR) {
    val commentsBeforeScan = repoPR.listComments()
    whenReady(repoPR.scheduler.scan()) { s =>
      eventually {
        val commentsAfterScan = repoPR.listComments()
        commentsAfterScan must have size (commentsBeforeScan.size+(if (shouldAddComment) 1 else 0))
        issueFun(repoPR.currentPR())
      }
    }
  }

  def scanUntil[T](shouldAddComment: Boolean)(issueFun: PullRequest => T)(implicit repoPR: RepoPR) {
    val commentsBeforeScan = repoPR.listComments()
    eventually {
      whenReady(repoPR.scheduler.scan()) { s =>
        val commentsAfterScan = repoPR.listComments()
        commentsAfterScan must have size (commentsBeforeScan.size + (if (shouldAddComment) 1 else 0))
        issueFun(repoPR.currentPR())
      }
    }
  }

  def waitUntil[T](shouldAddComment: Boolean)(issueFun: PullRequest => T)(implicit repoPR: RepoPR) {
    val commentsBeforeScan = repoPR.listComments()
    eventually {
      val commentsAfterScan = repoPR.listComments()
      commentsAfterScan must have size (commentsBeforeScan.size + (if (shouldAddComment) 1 else 0))
      issueFun(repoPR.currentPR())
    }
  }

  def scanShouldNotChangeAnything[T,S]()(implicit meat: RepoPR) {
    scanShouldNotChange { pr => (pr.labels.list().all().futureValue, pr.comments) }
  }

  def scanShouldNotChange[T,S](issueState: PullRequest => S)(implicit repoPR: RepoPR) {
    val issueBeforeScan = repoPR.currentPR()
    val beforeState = issueState(issueBeforeScan)

    for (check <- 1 to 3) {
      whenReady(repoPR.scheduler.scan()) { s =>
        issueState(repoPR.currentPR()) must equal(beforeState)
      }
    }
  }

  def mergePullRequestIn(repo: Repo, merging: String, prText: PRText = PRText("title", "desc")) = {
    eventually {
      whenReady(repo.refs.get(s"heads/$merging")) { _.ref must endWith(merging) }
    }

    val createPullRequest = CreatePullRequest(
      title = prText.title,
      head = merging,
      base = "master"
    )

    val pr = repo.pullRequests.create(createPullRequest).futureValue

    eventually {
      whenReady(pr.merge(MergePullRequest())) { _.merged must be(true) }
    }

    val mergedPR = eventually {
      whenReady(repo.pullRequests.get(pr.number)) {
        pr => pr.merged_by mustBe defined ;
          logger.info("Created and merged PR: "+pr.html_url)
          pr }
    }

    RepoPR(mergedPR)
  }

  def shaForDefaultBranchOf(repo: Repo): ObjectId = {
    repo.refs.get("heads/" + repo.default_branch).futureValue.objectId
  }
}