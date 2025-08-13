package controllers

import com.madgag.github.Implicits._
import com.madgag.scalagithub.GitHub
import com.madgag.scalagithub.model.{Repo, RepoId}
import com.typesafe.scalalogging.LazyLogging
import lib.ConfigFinder.ProutConfigFileName
import org.apache.pekko.actor.ActorSystem

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

case class RepoAcceptList(allKnownRepos: Set[RepoId], publicRepos: Set[RepoId])

class RepoAcceptListService(
  actorSystem: ActorSystem
) (implicit
  github: GitHub,
  as: ActorSystem
) extends LazyLogging {

  lazy val repoAcceptList = new AtomicReference[Future[RepoAcceptList]](getAllKnownRepos)

  def acceptList(): Future[RepoAcceptList] = repoAcceptList.get()

  def hasProutConfigFile(repo: Repo): Future[Boolean] = for {
    treeT <- repo.trees2.getRecursively(s"heads/${repo.default_branch}").trying
  } yield treeT.map(_.tree.exists(_.path.endsWith(ProutConfigFileName))).getOrElse(false)

  private def getAllKnownRepos: Future[RepoAcceptList] = for { // check this to see if it always expends quota...
    allRepos <- github.listRepos(sort="pushed", direction = "desc").take(6).all()
    proutRepos <- Future.traverse(allRepos.filter(_.permissions.exists(_.push))) { repo =>
      hasProutConfigFile(repo).map(hasConfig => Option.when(hasConfig)(repo))
    }.map(_.flatten.toSet)
  } yield {
    logger.info("All prout repos:\n\n" + proutRepos.map(r => s"${r.id},${r.name}"))
    RepoAcceptList(proutRepos.map(_.repoId), proutRepos.filterNot(_.`private`).map(_.repoId))
  }


  def start(): Unit = {
    logger.info("Starting background repo fetch")
    actorSystem.scheduler.scheduleWithFixedDelay(1.second, 60.seconds) { () =>
      repoAcceptList.set(getAllKnownRepos)
      github.checkRateLimit().foreach(status => logger.info(status.map(_.summary).getOrElse("Couldn't get rate-limit status")))
    }
  }

}
