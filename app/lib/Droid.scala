package lib

import lib.Config.Checkpoint
import org.kohsuke.github.GHRepository
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Droid {

  def scan(githubRepo: GHRepository)(implicit checkpointSnapshoter: Checkpoint => Future[CheckpointSnapshot]) = {
    Logger.info(s"Asked to audit ${githubRepo.getFullName}")

    val repoSnapshotF = RepoSnapshot(githubRepo)

    for {
      repoSnapshot <- repoSnapshotF
      foo <- Future.traverse(repoSnapshot.mergedPullRequests)(repoSnapshot.issueUpdater.process)
    } yield foo
  }

}
