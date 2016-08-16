package lib.slack

import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import lib.PullRequestCheckpointsStateChangeSummary
import lib.labels.Seen
import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.libs.ws.WS

object DeployReporter {

  def report(snapshot: PullRequestCheckpointsStateChangeSummary, hooks: Seq[Uri]) {
    val slackHooks = hooks.filter(_.host.contains("hooks.slack.com"))
    if (slackHooks.nonEmpty) {
      for (changedSnapshots <- snapshot.changedByState.get(Seen)) {
        val pr = snapshot.prCheckpointDetails.pr
        val mergedBy = pr.merged_by.get
        val checkpoints = changedSnapshots.map(_.snapshot.checkpoint)
        val attachments = Seq(Attachment(s"PR #${pr.number} deployed to ${checkpoints.map(_.name).mkString(", ")}",
          Seq(
            Attachment.Field("PR", s"<${pr.html_url}|#${pr.number}>", short = true),
            Attachment.Field("Merged by", s"<${mergedBy.html_url}|${mergedBy.atLogin}>", short = true)
          )
        ))

        val checkpointsAsSlack = checkpoints.map(c => s"<${c.details.url}|${c.name}>").mkString(", ")
        val json = Json.toJson(
          Message(
            s"*Deployed to $checkpointsAsSlack: ${pr.title}*\n\n${pr.body.mkString}",
            Some(lib.Bot.user.login),
            Some(mergedBy.avatar_url),
            attachments
          )
        )

        for (hook <- slackHooks) {
          WS.url(hook).post(json).onComplete {
            r => Logger.debug(s"Response from Slack: ${r.map(_.body)}")
          }
        }
      }
    }
  }
}
