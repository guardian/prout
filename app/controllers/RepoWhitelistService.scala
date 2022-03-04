package controllers

import java.util.concurrent.atomic.AtomicReference

import akka.actor.Scheduler
import com.madgag.github.Implicits._
import com.madgag.scalagithub.model.{Repo, RepoId}
import lib.Bot
import lib.ConfigFinder.ProutConfigFileName
import play.api.Logging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

case class RepoWhitelist(allKnownRepos: Set[RepoId], publicRepos: Set[RepoId])

class RepoWhitelistService(
  // configuration: Configuration,
  scheduler: Scheduler
) extends Logging {
  implicit val github = Bot.github

  lazy val repoWhitelist = new AtomicReference[Future[RepoWhitelist]](getAllKnownRepos)

  def whitelist(): Future[RepoWhitelist] = repoWhitelist.get()

  def hasProutConfigFile(repo: Repo): Future[Boolean] = for {
    treeT <- repo.trees2.getRecursively(s"heads/${repo.default_branch}").trying
  } yield treeT.map(_.tree.exists(_.path.endsWith(ProutConfigFileName))).getOrElse(false)

  private def getAllKnownRepos: Future[RepoWhitelist] = for { // check this to see if it always expends quota...
    allRepos <- github.listRepos(sort="pushed", direction = "desc").runW.takeUpTo(6)
    proutRepos <- Future.traverse(allRepos.filter(_.permissions.exists(_.push))) { repo =>
      hasProutConfigFile(repo).map(hasConfig => if (hasConfig) Some(repo) else None)
    }.map(_.flatten.toSet)
  } yield RepoWhitelist(proutRepos.map(_.repoId), proutRepos.filterNot(_.`private`).map(_.repoId))


  def start(): Unit = {
    logger.info("Starting background repo fetch")
    scheduler.schedule(1.second, 60.seconds) {
      repoWhitelist.set(getAllKnownRepos)
      github.checkRateLimit().foreach(status => logger.info(status.summary))
    }
  }

}
