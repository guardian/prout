package lib

import lib.Implicits._
import org.kohsuke.github.GHIssue
import play.api.Logger

object LabelledState{
  implicit class RichGHIssue(issue: GHIssue) {
    def labelledState(applicableFilter: String => Boolean) = new LabelledState(issue, issue.labelNames.toSet)
  }
}

class LabelledState(issue: GHIssue, val applicableLabels: Set[String]) {
  def updateLabels(newLabels: Set[String]) = {
    val oldLabelSet = issue.labelNames.toSet
    val unassociatedLabels = oldLabelSet -- applicableLabels
    val newLabelSet = newLabels ++ unassociatedLabels

    val labelStateChanged = newLabelSet != oldLabelSet
    Logger.info(s"${issue.getNumber} labelStateChanged=$labelStateChanged $newLabelSet")
    if (labelStateChanged) {
      try {
        issue.setLabels(newLabelSet.toSeq: _*)
        Logger.info(s"${issue.getNumber} set labels!?!?")
      } catch {
        case e: Exception => Logger.error(s"Error $e")
      }
    }
  }
}
