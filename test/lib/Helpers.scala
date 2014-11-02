package lib

import com.madgag.git._
import com.squareup.okhttp.OkHttpClient
import lib.Config.Checkpoint
import lib.Implicits._
import lib.gitgithub.GitHubCredentials
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ObjectId.zeroId
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.kohsuke.github._
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, Inspectors}
import org.scalatestplus.play._

import scala.collection.convert.wrapAll._
import scala.concurrent.Future

trait Helpers extends PlaySpec with OneAppPerSuite with Inspectors with ScalaFutures with Eventually with IntegrationPatience with BeforeAndAfterAll {

  val testRepoNamePrefix = "prout-test-"

  val githubCredentials = new GitHubCredentials(sys.env("PROUT_GITHUB_ACCESS_TOKEN"), new OkHttpClient)

  def conn(): GitHub = githubCredentials.conn()

  override def beforeAll {
    conn().getMyself.getAllRepositories.values.filter(_.getName.startsWith(testRepoNamePrefix)).foreach(_.delete())
  }

  case class RepoPR(githubRepo: GHRepository, pr: GHPullRequest) {
    def getIssue(): GHIssue = githubRepo.getIssue(pr.getNumber)
  }

  val droid = new Droid()

  def scan[T](checkpointSnapshoter: Checkpoint => Future[CheckpointSnapshot],
              shouldAddComment: Boolean)(issueFun: GHIssue => T)(implicit repoPR: RepoPR) {
    val commentCountBeforeScan = repoPR.getIssue().getCommentsCount
    whenReady(droid.scan(repoPR.githubRepo)(checkpointSnapshoter)) { s =>
      eventually {
        val issueAfterScan = repoPR.getIssue()
        issueAfterScan.getCommentsCount must be(commentCountBeforeScan+(if (shouldAddComment) 1 else 0))
        issueFun(issueAfterScan)
      }
    }
  }

  def scanUntil[T](checkpointSnapshoter: Checkpoint => Future[CheckpointSnapshot],
              shouldAddComment: Boolean)(issueFun: GHIssue => T)(implicit repoPR: RepoPR) {
    val commentCountBeforeScan = repoPR.getIssue().getCommentsCount
    eventually {
      whenReady(droid.scan(repoPR.githubRepo)(checkpointSnapshoter)) { s =>
        val issueAfterScan = repoPR.getIssue()
        issueAfterScan.getCommentsCount must be(commentCountBeforeScan + (if (shouldAddComment) 1 else 0))
        issueFun(issueAfterScan)
      }
    }
  }

  def scanShouldNotChangeAnything[T,S](checkpointSnapshoter: Checkpoint => Future[CheckpointSnapshot])(implicit meat: RepoPR) {
    scanShouldNotChange(checkpointSnapshoter) { issue => (issue.labelNames, issue.getCommentsCount) }
  }

  def scanShouldNotChange[T,S](checkpointSnapshoter: Checkpoint => Future[CheckpointSnapshot])(issueState: GHIssue => S)(implicit repoPR: RepoPR) {
    val issueBeforeScan = repoPR.getIssue()
    val beforeState = issueState(issueBeforeScan)

    for (check <- 1 to 3) {
      whenReady(droid.scan(repoPR.githubRepo)(checkpointSnapshoter)) { s =>
        issueState(repoPR.getIssue()) must equal(beforeState)
      }
    }
  }

  def mergePullRequestIn(fileName: String, merging: String) = {
    val githubRepo = createTestRepo(fileName)

    eventually(githubRepo.getBranches must contain key merging)

    val pr = githubRepo.createPullRequest(s"title", merging, "master", "desc")

    eventually(githubRepo.getPullRequest(pr.getNumber).getLabels mustBe empty)

    pr.merge("Go for it")

    RepoPR(githubRepo, pr)
  }

  def checkpointWith(branch: String)(implicit meat: RepoPR): (Checkpoint) => Future[CheckpointSnapshot] = {
    checkpointWith(meat.githubRepo.getBranches()(branch).getSHA1.asObjectId)
  }

  def checkpointWith(zeroId: ObjectId): (Config.Checkpoint) => Future[CheckpointSnapshot] = {
    c => Future.successful(CheckpointSnapshot(c, Some(zeroId)))
  }

  def createTestRepo(fileName: String): GHRepository = {
    val gitHub = conn()
    val testGithubRepo = gitHub.createRepository(testRepoNamePrefix + System.currentTimeMillis().toString, fileName, "", true)

    val localGitRepo = test.unpackRepo(fileName)

    val config = localGitRepo.getConfig()
    config.setString("remote", "origin", "url", testGithubRepo.gitHttpTransportUrl)
    config.save()

    val pushResults = localGitRepo.git.push.setCredentialsProvider(Bot.githubCredentials.git).setPushTags().setPushAll().call()

    forAll (pushResults.toSeq) { pushResult =>
      all (pushResult.getRemoteUpdates.map(_.getStatus)) must be(RemoteRefUpdate.Status.OK)
    }

    eventually {
      testGithubRepo.getBranches must not be empty
    }
    testGithubRepo
  }
}