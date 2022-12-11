package lib

import com.madgag.scalagithub.model.PullRequest

trait UpdateReporter {
  def report(
    repoSnapshot: RepoSnapshot,
    pr: PullRequest,
    checkpointsStateChangeSummary: PullRequestCheckpointsStateChangeSummary
  ): Unit
}
