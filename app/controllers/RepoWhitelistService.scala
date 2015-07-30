package controllers

import akka.agent.Agent
import lib.{Bot, RepoFullName}
import org.kohsuke.github.GHRepository
import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Akka

import scala.collection.convert.wrapAll._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

case class RepoWhitelist(allKnownRepos: Set[RepoFullName], publicRepos: Set[RepoFullName])

object RepoWhitelistService {
  lazy val repoWhitelist = Agent[RepoWhitelist](RepoWhitelist(Set.empty, Set.empty))

  def whitelist(): Future[RepoWhitelist] = repoWhitelist.future()

  val permissionsThatCanPush = Set("admin", "push")

  private def getAllKnownRepos = {
    val gitHub = Bot.githubCredentials.conn()
    val allRepos = gitHub.getMyself.listRepositories().filter(_.hasPushAccess).toSet
    val publicRepos = allRepos.filterNot(_.isPrivate)

    val wl = RepoWhitelist(allRepos.map(RepoFullName(_)), publicRepos.map(RepoFullName(_)))
    Logger.trace(s"wl=$wl")
    wl
  }

  def start() {
    Logger.info("Starting background repo fetch")
    Akka.system.scheduler.schedule(1.second, 60.seconds) {
      repoWhitelist.send(_ => getAllKnownRepos)
    }
  }

}
