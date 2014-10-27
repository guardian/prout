import com.madgag.git._
import com.squareup.okhttp.OkHttpClient
import lib.Config.Checkpoint
import lib.Implicits._
import lib.gitgithub.GitHubCredentials
import lib.{Config, Bot, CheckpointSnapshot, Droid}
import org.eclipse.jgit.lib.{Repository, ObjectId}
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.kohsuke.github.{GHPullRequest, GHIssue, GHRepository, GitHub}
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, Inspectors}
import org.scalatestplus.play._
import play.api.test.Helpers._
import play.api.test._

import scala.collection.convert.wrapAll._
import scala.concurrent.Future

class BooSpec extends PlaySpec with OneAppPerSuite with Inspectors with ScalaFutures with Eventually with IntegrationPatience with BeforeAndAfterAll {

  val testRepoNamePrefix = "prout-test-"

  val githubCredentials = new GitHubCredentials(sys.env("PROUT_GITHUB_ACCESS_TOKEN"), new OkHttpClient)

  def conn(): GitHub = githubCredentials.conn()

  override def beforeAll {
    conn().getMyself.getAllRepositories.values.filter(_.getName.startsWith(testRepoNamePrefix)).foreach(_.delete())
  }

  val droid = new Droid()

  def scan[T](checkpointSnapshoter: Checkpoint => Future[CheckpointSnapshot],
              shouldAddComment: Boolean)(issueFun: GHIssue => T)(implicit githubRepo: GHRepository, pr: GHPullRequest) {
    def getIssue(): GHIssue = githubRepo.getIssue(pr.getNumber)

    val commentCountBeforeScan = getIssue().getCommentsCount
    whenReady(droid.scan(githubRepo)(checkpointSnapshoter)) { s =>
      eventually {
        val issueAfterScan = getIssue()
        issueAfterScan.getCommentsCount must be(commentCountBeforeScan+(if (shouldAddComment) 1 else 0))
        issueFun(issueAfterScan)
      }
    }
  }

  "Update repo" must {
    "not spam not spam not spam" in {

        implicit val githubRepo = createTestRepo("/feature-branches.top-level-config.git.zip")

        implicit val pr = githubRepo.createPullRequest("title", "feature-1", "master", "desc")

        pr.merge("Go for it")

        scan(checkpointWith(ObjectId.zeroId), shouldAddComment = false) {
          _.labelNames must contain only("Pending-on-PROD")
        }

        scan(checkpointWith("master"), shouldAddComment = true) {
          _.labelNames must contain only("Seen-on-PROD")
        }

        scan(checkpointWith("master"), shouldAddComment = false) {
          _.labelNames must contain only("Seen-on-PROD")
        }
    }

    "report an overdue merge" in {

        implicit val githubRepo = createTestRepo("/impatient-top-level-config.git.zip")

        implicit val pr = githubRepo.createPullRequest("title", "feature-1", "master", "desc")

        pr.merge("I hope the deploy system isn't broke")

        // TODO : we actually have to wait 2 seconds before doing the scan!
        scan(checkpointWith(ObjectId.zeroId), shouldAddComment = true) {
          _.labelNames must contain only("Overdue-on-PROD")
        }
    }
  }

  def checkpointWith(branch: String)(implicit githubRepo: GHRepository): (Checkpoint) => Future[CheckpointSnapshot] = {
    checkpointWith(githubRepo.getBranches()(branch).getSHA1.asObjectId)
  }

  def checkpointWith(zeroId: ObjectId): (Config.Checkpoint) => Future[CheckpointSnapshot] = {
    c => Future.successful(CheckpointSnapshot(c, Some(zeroId)))
  }

  def createTestRepo(fileName: String): GHRepository = {
    val gitHub = conn()
    val testGithubRepo = gitHub.createRepository(testRepoNamePrefix + System.currentTimeMillis().toString, "", "", true)

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