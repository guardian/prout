import lib.Implicits._
import lib._
import org.eclipse.jgit.lib.ObjectId.zeroId

class FunctionalSpec extends Helpers {

  "Update repo" must {

    "not spam not spam not spam" in {
      implicit val repoPR = mergePullRequestIn("/feature-branches.top-level-config.git.zip", "feature-1")

      scan(checkpointWith(zeroId), shouldAddComment = false) {
        _.labelNames must contain only ("Pending-on-PROD")
      }

      scan(checkpointWith("master"), shouldAddComment = true) {
        _.labelNames must contain only ("Seen-on-PROD")
      }

      scanShouldNotChangeAnything(checkpointWith("master"))
    }

    "report an overdue merge" in {
      implicit val repoPR = mergePullRequestIn("/impatient-top-level-config.git.zip", "feature-1")

      scanUntil(checkpointWith(zeroId), shouldAddComment = true) {
        _.labelNames must contain only("Overdue-on-PROD")
      }

      scanShouldNotChangeAnything(checkpointWith(zeroId))

      scan(checkpointWith("master"), shouldAddComment = true) {
        _.labelNames must contain only("Seen-on-PROD")
      }
    }
  }
}