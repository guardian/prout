package controllers

import akka.agent.Agent
import com.typesafe.scalalogging.LazyLogging
import lib.ConfigFinder.ProutConfigFileName
import lib.{Bot, RepoFullName}
import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Akka

import scala.collection.convert.wrapAll._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

case class RepoWhitelist(allKnownRepos: Set[RepoFullName], publicRepos: Set[RepoFullName])

object RepoWhitelistService extends LazyLogging {
  lazy val repoWhitelist = Agent[RepoWhitelist](RepoWhitelist(Set.empty, Set.empty))

  def whitelist(): Future[RepoWhitelist] = repoWhitelist.future()

  val permissionsThatCanPush = Set("admin", "push")

  private def getAllKnownRepos = {
    val gitHub = Bot.githubCredentials.conn()
    val allReposWithPushAccess = gitHub.getMyself.listRepositories().filter(_.hasPushAccess).toSet

    logger.info(s"Starting allReposWithPushAccess (${allReposWithPushAccess.size}) filter")
    val allRepos = allReposWithPushAccess.par.filter {
      r =>
        val refTry = Try(r.getRef(s"heads/${r.getDefaultBranch}"))
        refTry.map {ref =>
          val defaultBranchSha = ref.getObject.getSha
          val treeRecursive = r.getTreeRecursive(defaultBranchSha, 1)
          if (treeRecursive.isTruncated) logger.error("Truncated tree for "+r.getFullName)
          treeRecursive.getTree.exists(_.getPath.endsWith(ProutConfigFileName))
        }.getOrElse(false)
    }.seq

    logger.warn(s"allRepos size = ${allRepos.size}")

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
