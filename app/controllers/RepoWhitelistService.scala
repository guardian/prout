package controllers

import akka.agent.Agent
import lib.{Bot, RepoFullName}
import play.api.Logger
import play.api.libs.concurrent.Akka

import scala.collection.convert.wrapAll._
import scala.concurrent.Future
import scala.concurrent.duration._
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global

object RepoWhitelistService {
  lazy val allKnownRepos = Agent[Set[RepoFullName]](Set.empty)

  def isKnown(repo: RepoFullName): Future[Boolean] = allKnownRepos.future().map(_(repo))

  val permissionsThatCanPush = Set("admin", "push")

  private def getAllKnownRepos = {
    val gitHub = Bot.githubCredentials.conn()
    val organisationRepos: Set[RepoFullName] = (for {
      teamSet <- gitHub.getMyTeams.values
      team <- teamSet if permissionsThatCanPush(team.getPermission)
      repos <- team.getRepositories.values
    } yield RepoFullName(repos.getFullName)).toSet

    val userRepos = gitHub.getMyself.listRepositories().iterator().map(r => RepoFullName(r.getFullName)).toSet
    Logger.info(s"organisationRepos=$organisationRepos userRepos=$userRepos")
    organisationRepos ++ userRepos
  }

  def start() {
    Logger.info("Starting background repo fetch")
    Akka.system.scheduler.schedule(1.second, 60.seconds) {
      allKnownRepos.send(_ => getAllKnownRepos)
    }
  }

}
