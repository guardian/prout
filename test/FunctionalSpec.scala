import com.netaporter.uri.Uri
import lib.Implicits._
import lib._
import lib.travis.TravisApiClient
import org.eclipse.jgit.lib.ObjectId.zeroId
import org.kohsuke.github.GHEvent
import org.scalatest.Inside
import play.api.libs.json.{JsDefined, JsString, JsSuccess}

import scala.collection.convert.wrapAll._

class FunctionalSpec extends Helpers with TestRepoCreation with Inside {

  "Update repo" must {

    "not spam not spam not spam" in {
      implicit val repoPR = mergePullRequestIn(createTestRepo("/feature-branches.top-level-config.git.zip"), "feature-1")

      repoPR setCheckpointTo zeroId

      scan(shouldAddComment = false) {
        _.labelNames must contain only "Pending-on-PROD"
      }

      repoPR setCheckpointTo "master"

      scan(shouldAddComment = true) {
        _.labelNames must contain only "Seen-on-PROD"
      }

      scanShouldNotChangeAnything()
    }

    "not act on a pull request if it does not touch a .prout.json configured folder" in {
      implicit val repoPR = mergePullRequestIn(createTestRepo("/multi-project.master-updated-before-feature-merged.git.zip"), "bard-feature")

      repoPR setCheckpointTo zeroId

      scanShouldNotChangeAnything()

      repoPR setCheckpointTo "master"

      scanShouldNotChangeAnything()
    }

    "act on a pull request if it touches a .prout.json configured folder" in {
      implicit val repoPR = mergePullRequestIn(createTestRepo("/multi-project.master-updated-before-feature-merged.git.zip"), "food-feature")

      repoPR setCheckpointTo zeroId

      scan(shouldAddComment = false) {
        _.labelNames must contain only "Pending-on-PROD"
      }

      repoPR setCheckpointTo "master"

      scan(shouldAddComment = true) {
        _.labelNames must contain only "Seen-on-PROD"
      }

      scanShouldNotChangeAnything()
    }

    "report an overdue merge without being called" in {
      implicit val repoPR = mergePullRequestIn(createTestRepo("/impatient-top-level-config.git.zip"), "feature-1")

      repoPR setCheckpointTo zeroId

      scan(shouldAddComment = false) {
        _.labelNames must contain only "Pending-on-PROD"
      }

      waitUntil(shouldAddComment = true) {
        _.labelNames must contain only "Overdue-on-PROD"
      }

      scanShouldNotChangeAnything()

      repoPR setCheckpointTo "master"

      scan(shouldAddComment = true) {
        _.labelNames must contain only "Seen-on-PROD"
      }
    }

    "report a broken site as overdue" in {
      implicit val repoPR = mergePullRequestIn(createTestRepo("/impatient-top-level-config.git.zip"), "feature-1")

      repoPR setCheckpointFailureTo new Exception("This website went Boom!")

      scan(shouldAddComment = false) {
        _.labelNames must contain only "Pending-on-PROD"
      }

      waitUntil(shouldAddComment = true) {
        _.labelNames must contain only "Overdue-on-PROD"
      }

      scanShouldNotChangeAnything()

      repoPR setCheckpointTo "master"

      scan(shouldAddComment = true) {
        _.labelNames must contain only "Seen-on-PROD"
      }
    }

    "report slackishly" in {
      assume(slackWebhookUrlOpt.isDefined, "No Slack credentials")
      val slackWebhookUrl = slackWebhookUrlOpt.get
      val prText = {
        val pr = conn().getRepository("guardian/membership-frontend").getPullRequest(15)
        PRText(pr.getTitle, pr.getBody)
      }

      val githubRepo = createTestRepo("/feature-branches.top-level-config.git.zip")
      implicit val repoPR = mergePullRequestIn(githubRepo, "feature-1", prText)

      githubRepo.createWebHook(slackWebhookUrl, Set(GHEvent.WATCH)) // Don't really want the hook to fire!
      eventually(githubRepo.getHooks must not be empty)

      repoPR setCheckpointTo "master"

      scan(shouldAddComment = true) {
        _.labelNames must contain only "Seen-on-PROD"
      }
    }

    "trigger a post-deploy travis build" in {
      val githubRepo = conn().getMyself.getRepository("test-travis-builds")

      val id = System.currentTimeMillis().toString

      val masterSha = defaultBranchShaFor(githubRepo)

      githubRepo.createRef("refs/heads/" + id, masterSha)

      githubRepo.createContent(Array[Byte](), "a", "junk/"+id, id)

      implicit val repoPR = mergePullRequestIn(githubRepo, id)

      repoPR setCheckpointTo "master"

      scan(shouldAddComment = true) {
        _.labelNames must contain only "Seen-on-PROD"
      }

      val t = new TravisApiClient(githubToken)
      eventually {
        val status = Option(githubRepo.getLastCommitStatus(defaultBranchShaFor(githubRepo))).value

        val buildId = Uri.parse(status.getTargetUrl).pathParts.last.part
        whenReady(t.build(buildId)) {
          buildResponse => inside(buildResponse) {
            case JsSuccess(b, _) =>
              inside (b.build.config \ "script") { case JsDefined(script) =>
                script mustEqual JsString("echo Prout - afterSeen script")
              }
          }
        }
      }
    }
  }
}