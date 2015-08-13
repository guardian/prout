package lib.slack

import com.madgag.github.Implicits._
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import lib.{PullRequestCheckpointsSummary, Seen}
import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.libs.ws.WS

object DeployReporter {

  def report(snapshot: PullRequestCheckpointsSummary, hooks: Seq[Uri]) {
    val slackHooks = hooks.filter(_.host.contains("hooks.slack.com"))
    if (slackHooks.nonEmpty) {
      for (changedSnapshots <- snapshot.changedSnapshotsByState.get(Seen)) {
        val pr = snapshot.pr
        val mergedBy = pr.getMergedBy
        val checkpoints = changedSnapshots.map(_.checkpoint)
        val attachments = Seq(Attachment(s"PR #${pr.getNumber} deployed to ${checkpoints.map(_.name).mkString(", ")}",
          Seq(
            Attachment.Field("PR", s"<${pr.getHtmlUrl}|#${pr.getNumber}>", true),
            Attachment.Field("Merged by", s"<${mergedBy.getHtmlUrl}|${mergedBy.atLogin}>", true)
          )
        ))

        val checkpointsAsSlack = checkpoints.map(c => s"<${c.details.url}|${c.name}>").mkString(", ")
        val json = Json.toJson(
          Message(
            s"*Deployed to $checkpointsAsSlack: ${pr.getTitle}*\n\n${pr.getBody}",
            Some(lib.Bot.user.getLogin),
            Some(mergedBy.getAvatarUrl),
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
