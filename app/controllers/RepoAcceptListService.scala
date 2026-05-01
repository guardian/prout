package controllers

import cats.*
import cats.data.*
import cats.effect.IO
import cats.syntax.all.*
import com.madgag.github.Implicits.*
import com.madgag.scalagithub.GitHub
import com.madgag.scalagithub.model.{Repo, RepoId}
import com.typesafe.scalalogging.LazyLogging
import fs2.Chunk
import lib.ConfigFinder.ProutConfigFileName
import org.apache.pekko.actor.ActorSystem

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.*

case class RepoAcceptList(allKnownRepos: Set[RepoId], publicRepos: Set[RepoId])

class RepoAcceptListService()(implicit
  github: GitHub,
  actorSystem: ActorSystem
) extends LazyLogging {

  lazy val repoAcceptList = new AtomicReference[IO[RepoAcceptList]](getAllKnownRepos)

  def acceptList(): IO[RepoAcceptList] = repoAcceptList.get()

  def hasProutConfigFile(repo: Repo): IO[Boolean] = (for {
    treeT <- repo.trees2.getRecursively(s"heads/${repo.default_branch}")
  } yield treeT.result.tree.exists(_.path.endsWith(ProutConfigFileName))).recover(_ => false)

  private def getAllKnownRepos: IO[RepoAcceptList] = for { // check this to see if it always expends quota...
    proutRepos <- github.listReposAccessibleToTheApp()
      .mapChunks(_.flatMap(repos => Chunk.from(repos.repositories)))
      .evalFilterAsync(4)(hasProutConfigFile)
      .compile.to(Set)
  } yield RepoAcceptList(proutRepos.map(_.repoId), proutRepos.filterNot(_.`private`).map(_.repoId))


  def start(): Unit = {
    logger.info("Starting background repo fetch")
    actorSystem.scheduler.scheduleWithFixedDelay(1.second, 60.seconds) { () =>
      repoAcceptList.set(getAllKnownRepos)
      github.checkRateLimit().flatTap(status => IO.delay(logger.info(status.map(_.summary).getOrElse("Couldn't get rate-limit status"))))
    }
  }

}
