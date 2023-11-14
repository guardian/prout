package lib

import org.apache.pekko.stream.Materializer
import com.madgag.github.Implicits.{RichFuture, RichSource}
import com.madgag.scalagithub.GitHub
import com.madgag.scalagithub.commands.CreateLabel
import lib.labels.{CheckpointTestStatus, PullRequestCheckpointStatus}

import scala.concurrent.{ExecutionContext, Future}

class RepoUpdater(implicit
  g: GitHub,
  m: Materializer,
  ec: ExecutionContext
) {

  def attemptToCreateMissingLabels(repoLevelDetails: RepoLevelDetails): Future[_] = {
    for {
      existingLabels <- repoLevelDetails.repo.labels.list().all()
      createdLabels <- Future.traverse(missingLabelsGiven(repoLevelDetails, existingLabels.map(_.name).toSet)) {
        missingLabel => repoLevelDetails.repo.labels.create(missingLabel)
      }
    } yield createdLabels
  }.trying

  def missingLabelsGiven(repoLevelDetails: RepoLevelDetails, existingLabelNames: Set[String]): Set[CreateLabel] = for {
    prcs <- PullRequestCheckpointStatus.all ++ CheckpointTestStatus.all
    checkpointName <- repoLevelDetails.config.checkpointsByName.keySet
    label = prcs.labelFor(checkpointName)
    if !existingLabelNames(label)
  } yield CreateLabel(label, prcs.defaultColour)
}
