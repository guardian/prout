package controllers

import akka.agent.Agent
import lib.{Bot, RepoFullName}
import org.kohsuke.github.GHRepository
import play.api.Logger
import play.api.libs.concurrent.Akka

import scala.collection.convert.wrapAll._
import scala.concurrent.Future
import scala.concurrent.duration._
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global

case class RepoWhitelist(allKnownRepos: Set[RepoFullName], publicRepos: Set[RepoFullName])

object RepoWhitelistService {
  lazy val repoWhitelist = Agent[RepoWhitelist](RepoWhitelist(Set.empty, Set.empty))

  def whitelist(): Future[RepoWhitelist] = repoWhitelist.future()

  val permissionsThatCanPush = Set("admin", "push")

  private def getAllKnownRepos = {
    val gitHub = Bot.githubCredentials.conn()
    val organisationRepos: Set[GHRepository] = (for {
      teamSet <- gitHub.getMyTeams.values
      team <- teamSet if permissionsThatCanPush(team.getPermission)
      repo <- team.getRepositories.values.toSet[GHRepository]
    } yield repo).toSet

    val userRepos = gitHub.getMyself.listRepositories().asList().toSet
    val allRepos = organisationRepos ++ userRepos
    val publicRepos = allRepos.filterNot(_.isPrivate)

    RepoWhitelist(allRepos.map(RepoFullName(_)), publicRepos.map(RepoFullName(_)))
  }

  def start() {
    Logger.info("Starting background repo fetch")
    Akka.system.scheduler.schedule(1.second, 60.seconds) {
      repoWhitelist.send(_ => getAllKnownRepos)
    }
  }

}
