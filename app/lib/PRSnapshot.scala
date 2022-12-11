package lib

import akka.stream.Materializer
import com.madgag.scalagithub.GitHub
import com.madgag.scalagithub.model.{Label, PullRequest, Repo}
import lib.gitgithub.RichSource

import scala.concurrent.{ExecutionContext, Future}

case class PRSnapshot(pr: PullRequest, labels: Seq[Label])
