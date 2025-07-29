package lib.slack

import cats.data.NonEmptySeq
import com.madgag.scalagithub.model.{PullRequest, User}
import io.lemonlabs.uri.Url
import lib.labels.Seen
import lib._
import play.api.Logging
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}

class DeployReporter(
  ws: WSClient,
  bot: Bot
)(implicit
  ec: ExecutionContext
) extends Logging with UpdateReporter {

  override def report(
    repoSnapshot: RepoSnapshot,
    pr: PullRequest,
    checkpointsChangeSummary: PullRequestCheckpointsStateChangeSummary
  ): Unit = slackHooksFrom(repoSnapshot.repoLevelDetails).flatMap { slackHooks =>
    report(checkpointsChangeSummary, slackHooks)
  }.getOrElse(Future.successful(()))

  private def slackHooksFrom(repoLevelDetails: RepoLevelDetails): Option[NonEmptySeq[Url]] =
    NonEmptySeq.fromSeq(repoLevelDetails.hooks.filter(_.hostOption.exists(_.value == "hooks.slack.com")))

  private def report(checkpointChangeSummary: PullRequestCheckpointsStateChangeSummary, slackHooks: NonEmptySeq[Url]): Option[Future[Unit]] = {
    val pr = checkpointChangeSummary.prCheckpointDetails.pr
    for (changedSnapshots <- checkpointChangeSummary.changedByState.get(Seen)) yield {
      val json = messageJsonFor(pr, changedSnapshots)

      Future.traverse(slackHooks.toSeq) { hook =>
        val responseF = ws.url(hook.toString).post(json)
        responseF.onComplete { r => logger.debug(s"Response from Slack: ${r.map(_.body)}") }
        responseF
      }.map(_ => ())
    }
  }

  private def messageJsonFor(pr: PullRequest, changedSnapshots: Set[EverythingYouWantToKnowAboutACheckpoint]): JsValue = {
    val checkpointsWherePRIsNewlySeen = changedSnapshots.map(_.snapshot.checkpoint)

    Json.toJson(
      Message(
        s"*Deployed to ${checkpointsWherePRIsNewlySeen.map(slackLinkFor).mkString(", ")}: ${pr.title}*",
        Some(bot.identity.login),
        pr.merged_by.map(_.avatar_url),
        attachments = Seq(Attachment(s"PR #${pr.number} deployed to ${checkpointsWherePRIsNewlySeen.map(_.name).mkString(", ")}", Seq(
          Attachment.Field("PR", s"<${pr.html_url}|#${pr.number}>", short = true),
        ) ++ pr.merged_by.map(mergedBy => Attachment.Field("Merged by", slackLinkFor(mergedBy), short = true))))
      )
    )
  }

  private def slackLinkFor(c: Config.Checkpoint) = s"<${c.details.url}|${c.name}>"
  private def slackLinkFor(user: User) = s"<${user.html_url}|${user.atLogin}>"
}
