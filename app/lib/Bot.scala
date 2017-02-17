package lib

import com.madgag.scalagithub.model.User
import com.madgag.scalagithub.{GitHub, GitHubCredentials}
import okhttp3.OkHttpClient
import play.api.Logger

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scalax.file.ImplicitConversions._
import scalax.file.Path

trait Bot {

  val accessToken: String

  val parentWorkDir = Path.fromString("/tmp") / "bot" / "working-dir"

  parentWorkDir.mkdirs()

  lazy val okHttpClient = {
    val clientBuilder = new OkHttpClient.Builder()

    val responseCacheDir = parentWorkDir / "http-cache"
    responseCacheDir.mkdirs()
    if (responseCacheDir.exists) {
      clientBuilder.cache(new okhttp3.Cache(responseCacheDir, 5 * 1024 * 1024))
    } else Logger.warn(s"Couldn't create HttpResponseCache dir ${responseCacheDir.path}")

    clientBuilder.build()
  }

  lazy val githubCredentials = GitHubCredentials.forAccessKey(accessToken, (parentWorkDir / "http-cache").toPath).get

  lazy val github = new GitHub(githubCredentials)

  lazy val user: User = {
    val myself = Await.result(github.getUser(), 3 seconds)
    Logger.info(s"Token '${accessToken.take(2)}...' gives GitHub user ${myself.atLogin}")
    myself
  }

}

object Bot extends Bot {
  import play.api.Play.current
  val config = play.api.Play.configuration.underlying

  val accessToken: String = config.getString("github.access.token")

}