import lib.RepoSnapshot.ClosedPRsMostlyRecentlyUpdated
import lib._
import lib.gitgithub.RichSource
import org.eclipse.jgit.lib.ObjectId.zeroId
import org.scalatest.Inside

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps

class FunctionalSpec extends Helpers with TestRepoCreation with Inside {

  "Update repo" must {

    "not spam not spam not spam" in {
      implicit val repoPR = mergeSamplePR()

      repoPR setCheckpointTo zeroId

      eventually {
        whenReady(repoPR.githubRepo.pullRequests.list(ClosedPRsMostlyRecentlyUpdated).all()) {
          _ must have size 1
        }
      }

      scan(shouldAddComment = false) { _ =>
        labelsOnPR() must contain only "Pending-on-PROD"
      }

      repoPR setCheckpointToMatchDefaultBranch

      scan(shouldAddComment = true) { pr =>
        labelsOnPR() must contain only "Seen-on-PROD"
        lastCommentOn(pr) must include("Please check your changes!")
      }

      scanShouldNotChangeAnything()
    }

    "not act on a pull request if it does not touch a .prout.json configured folder" in {
      implicit val repoPR = mergePullRequestIn(createTestRepo("/multi-project.master-updated-before-feature-merged.git.zip"), "bard-feature")

      repoPR setCheckpointTo zeroId

      scanShouldNotChangeAnything()

      repoPR setCheckpointToMatchDefaultBranch

      scanShouldNotChangeAnything()
    }

    "act on a pull request if it touches a .prout.json configured folder" in {
      implicit val repoPR = mergePullRequestIn(createTestRepo("/multi-project.master-updated-before-feature-merged.git.zip"), "food-feature")

      repoPR setCheckpointTo zeroId

      scan(shouldAddComment = false) { _ =>
        labelsOnPR() must contain only "Pending-on-PROD"
      }

      repoPR setCheckpointToMatchDefaultBranch

      scan(shouldAddComment = true) { _ =>
        labelsOnPR() must contain only "Seen-on-PROD"
      }

      scanShouldNotChangeAnything()
    }

    "report an overdue merge without being called" in {
      implicit val repoPR = mergePullRequestIn(createTestRepo("/impatient-top-level-config.git.zip"), "feature-1")

      repoPR setCheckpointTo zeroId

      scan(shouldAddComment = false) { _ =>
        labelsOnPR() must contain only "Pending-on-PROD"
      }

      waitUntil(shouldAddComment = true) { pr =>
        labelsOnPR() must contain only "Overdue-on-PROD"
        lastCommentOn(pr) must include("What's gone wrong?")
      }

      scanShouldNotChangeAnything()

      repoPR setCheckpointToMatchDefaultBranch

      scan(shouldAddComment = true) { _ =>
        labelsOnPR() must contain only "Seen-on-PROD"
      }
    }

    "report a broken site as overdue" in {
      implicit val repoPR = mergePullRequestIn(createTestRepo("/impatient-top-level-config.git.zip"), "feature-1")

      repoPR setCheckpointFailureTo new Exception("This website went Boom!")

      scan(shouldAddComment = false) { _ =>
        labelsOnPR() must contain only "Pending-on-PROD"
      }

      waitUntil(shouldAddComment = true) { _ =>
        labelsOnPR() must contain only "Overdue-on-PROD"
      }

      scanShouldNotChangeAnything()

      repoPR setCheckpointToMatchDefaultBranch

      scan(shouldAddComment = true) { _ =>
        labelsOnPR() must contain only "Seen-on-PROD"
      }
    }

    "use custom messages in comments when set in the config" in {
      implicit val repoPR = mergePullRequestIn(createTestRepo("/simple-with-messages.git.zip"), "feature-elephant")

      repoPR setCheckpointTo zeroId

      scan(shouldAddComment = false) { _ =>
        labelsOnPR() must contain only "Pending-on-PROD"
      }

      repoPR setCheckpointToMatchDefaultBranch

      scan(shouldAddComment = true) { pr =>
        labelsOnPR() must contain only "Seen-on-PROD"
        lastCommentOn(pr) must include("This is a custom message for the `Seen` status")
      }

      scanShouldNotChangeAnything()
    }
  }

  private def mergeSamplePR(userLabels: Set[String] = Set.empty): RepoPR = mergePullRequestIn(
    createTestRepo("/feature-branches.top-level-config.git.zip"),
    "feature-1",
    userLabels = userLabels
  )
}