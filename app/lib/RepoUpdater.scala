package lib

import com.madgag.github.Implicits._
import com.madgag.scalagithub.GitHub
import com.madgag.scalagithub.commands.CreateLabel
import lib.labels.{CheckpointTestStatus, PullRequestCheckpointStatus}
import org.apache.pekko.actor.ActorSystem

import scala.concurrent.{ExecutionContext, Future}

class RepoUpdater(implicit
  g: GitHub,
  as: ActorSystem,
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
