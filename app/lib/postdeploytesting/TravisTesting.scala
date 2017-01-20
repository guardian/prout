package lib.postdeploytesting

import com.madgag.scalagithub.model.PullRequest
import lib.Config.Checkpoint
import lib.{RepoSnapshot, TestingInProduction}


object TravisTesting {
  def handleResultsOfPostDeployTesting(repoSnapshot: RepoSnapshot) = {
    val travisAfterSeenConfig = repoSnapshot.config.checkpointsByName.values.filter(_.details.afterSeen.flatMap(_.travis).isDefined).headOption
    if (travisAfterSeenConfig.isDefined) {
      val checkpointWithTravisConfig: Checkpoint = ???

      val masterCommitStatuses = repoSnapshot.repo.combinedStatusFor(repoSnapshot.repo.default_branch).value.get.get.result.statuses
      val postDeployTravisTestResultStatus = masterCommitStatuses.filter(TestingInProduction.isCompletedTravisTest).headOption
      if (postDeployTravisTestResultStatus.isDefined) {
        val latestPRSeenForCheckpoint: PullRequest = ???
        updatePRToReflectTestResults(latestPRSeenForCheckpoint)
      }
    }
  }

}
