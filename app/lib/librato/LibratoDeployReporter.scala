package lib.librato

import com.madgag.scalagithub.model.PullRequest
import io.lemonlabs.uri.Uri
import lib.Responsibility.createdByAndMergedByFor
import lib.labels.Seen
import lib.librato.model.{Annotation, Link}
import lib.{EverythingYouWantToKnowAboutACheckpoint, PullRequestCheckpointsStateChangeSummary, RepoLevelDetails, RepoSnapshot, UpdateReporter}

import scala.concurrent.{ExecutionContext, Future}

class LibratoDeployReporter(
  librato: LibratoApiClient
)(implicit
  ec: ExecutionContext
) extends UpdateReporter {

  override def report(
    repoSnapshot: RepoSnapshot,
    pr: PullRequest,
    checkpointsChangeSummary: PullRequestCheckpointsStateChangeSummary
  ): Unit = Future.traverse(checkpointsChangeSummary.changedByState(Seen)) { checkpoint =>
    report(pr, checkpoint)
  }

  private def report(pr: PullRequest, checkpoint: EverythingYouWantToKnowAboutACheckpoint): Future[_] = {
    librato.createAnnotation(s"${pr.baseRepo.name}.prout", Annotation(
      title = s"PR #${pr.number} : '${pr.title}' deployed",
      description = Some(createdByAndMergedByFor(pr).capitalize),
      start_time = pr.merged_at.map(_.toInstant),
      end_time = Some(checkpoint.snapshot.time),
      source = Some(checkpoint.snapshot.checkpoint.name),
      links = Seq(Link(
        rel = "github",
        label = Some(s"PR #${pr.number}"),
        href = Uri.parse(pr.html_url)
      ))
    ))
  }

}
