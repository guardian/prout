package lib

import java.util.concurrent.TimeUnit.MINUTES

import lib.Config.Checkpoint
import org.kohsuke.github.GHRepository
import play.api.Logger
import play.api.cache.Cache

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object Scanner {

  import play.api.Play.current

  val droid: Droid = new Droid

  def updateFor(repoFullName: RepoFullName)(implicit checkpointSnapshoter: Checkpoint => Future[CheckpointSnapshot]) {
    val key = repoFullName
    Logger.debug(s"update requested for $key")
    Cache.getOrElse(key.text) {
      new Dogpile({
        val githubRepo = Bot.githubCredentials.conn().getRepository(repoFullName.text)

        Await.ready(droid.scan(githubRepo), Duration(2, MINUTES))
      })
    }.doAtLeastOneMore()
  }


}
