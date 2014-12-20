import lib.Config.{CheckpointDetails, Checkpoint}
import lib.Implicits._
import lib._
import org.eclipse.jgit.lib.ObjectId.zeroId
import org.joda.time.Period

import com.netaporter.uri.dsl._

class FunctionalSpec extends Helpers {

  "Update repo" must {

    "not spam not spam not spam" in {
      implicit val repoPR = mergePullRequestIn("/feature-branches.top-level-config.git.zip", "feature-1")

      repoPR setCheckpointTo zeroId

      scan(shouldAddComment = false) {
        _.labelNames must contain only ("Pending-on-PROD")
      }

      repoPR setCheckpointTo "master"

      scan(shouldAddComment = true) {
        _.labelNames must contain only ("Seen-on-PROD")
      }

      scanShouldNotChangeAnything()
    }

    "report an overdue merge without being called" in {
      implicit val repoPR = mergePullRequestIn("/impatient-top-level-config.git.zip", "feature-1")

      repoPR setCheckpointTo zeroId

      scan(shouldAddComment = false) {
        _.labelNames must contain only ("Pending-on-PROD")
      }

      waitUntil(shouldAddComment = true) {
        _.labelNames must contain only ("Overdue-on-PROD")
      }

      scanShouldNotChangeAnything()

      repoPR setCheckpointTo "master"

      scan(shouldAddComment = true) {
        _.labelNames must contain only ("Seen-on-PROD")
      }
    }

    "be able to hit Ophan" in {
      val checkpoint = Checkpoint("PROD", CheckpointDetails("https://dashboard.ophan.co.uk/login", Period.parse("PT1H")))
      whenReady(CheckpointSnapshot(checkpoint)) { s =>
        s must not be 'empty
      }
    }
  }
}