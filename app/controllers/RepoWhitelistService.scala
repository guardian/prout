package controllers

import java.util.concurrent.atomic.AtomicReference

import com.madgag.scalagithub.model.RepoId
import com.typesafe.scalalogging.LazyLogging
import lib.Bot
import lib.ConfigFinder.ProutConfigFileName
import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Akka

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

case class RepoWhitelist(allKnownRepos: Set[RepoId], publicRepos: Set[RepoId])

object RepoWhitelistService extends LazyLogging {
  implicit val github = Bot.github

  lazy val repoWhitelist = new AtomicReference[Future[RepoWhitelist]](getAllKnownRepos)

  def whitelist(): Future[RepoWhitelist] = repoWhitelist.get()

  val permissionsThatCanPush = Set("admin", "push")

  private def getAllKnownRepos = for {
    allRepos <- github.listRepos(sort="pushed", direction = "desc")
    proutRepos <- Future.traverse(allRepos.filter(_.permissions.exists(_.push))) { repo =>
      for (tree <- repo.trees2.getRecursively(s"heads/${repo.default_branch}")) yield {
        if (tree.truncated) logger.error("Truncated tree for "+repo.repoId)
        Some(repo).filter(_ => tree.tree.exists(_.path.endsWith(ProutConfigFileName)))
      }
    }.map(_.flatten.toSet)
  } yield RepoWhitelist(proutRepos.map(_.repoId), proutRepos.filterNot(_.`private`).map(_.repoId))


  def start() {
    Logger.info("Starting background repo fetch")
    Akka.system.scheduler.schedule(1.second, 60.seconds) {
      repoWhitelist.set(getAllKnownRepos)
    }
  }

}
