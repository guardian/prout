package lib

import com.madgag.scalagithub.model.PullRequest
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global

class LabelledState(issue: PullRequest, val applicableLabels: String => Boolean) {

  implicit val github = Bot.github

  def currentLabelsF = issue.labels.list().map(_.map(_.name).toSet)

  def updateLabels(newLabels: Set[String]) = for {
    allOldLabels <- issue.labels.list()
  } {
    val allOldLabelsSet = allOldLabels.map(_.name).toSet
    val unassociatedLabels = allOldLabelsSet.filterNot(applicableLabels)
    val newLabelSet = newLabels ++ unassociatedLabels

    val labelStateChanged = newLabelSet != allOldLabelsSet
    Logger.info(s"${issue.prId.slug} labelStateChanged=$labelStateChanged $newLabelSet")
    if (labelStateChanged) {
      issue.labels.replace(newLabelSet.toSeq)
    }
  }
}
