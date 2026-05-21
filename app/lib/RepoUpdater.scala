package lib

import cats.effect.IO
import com.madgag.github.Implicits.*
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

  def attemptToCreateMissingLabels(repoLevelDetails: RepoLevelDetails): IO[_] = {
    val labels = repoLevelDetails.repo.labels
    for {
      existingLabels <- labels.list().compile.toList
      createdLabels <- IO.parTraverse(missingLabelsGiven(repoLevelDetails, existingLabels.map(_.name).toSet).toList) {
        missingLabel => labels.create(missingLabel).attempt
      }
    } yield createdLabels
  }

  def missingLabelsGiven(repoLevelDetails: RepoLevelDetails, existingLabelNames: Set[String]): Set[CreateLabel] = for {
    prcs <- PullRequestCheckpointStatus.all ++ CheckpointTestStatus.all
    checkpointName <- repoLevelDetails.config.checkpointsByName.keySet
    label = prcs.labelFor(checkpointName)
    if !existingLabelNames(label)
  } yield CreateLabel(label, prcs.defaultColour)
}
