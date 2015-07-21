package lib

import com.squareup.okhttp
import com.squareup.okhttp.OkHttpClient
import lib.gitgithub.GitHubCredentials
import play.api.Logger

import scalax.file.ImplicitConversions._
import scalax.file.Path

trait Bot {

  val accessToken: String

  val parentWorkDir = Path.fromString("/tmp") / "bot" / "working-dir"

  parentWorkDir.mkdirs()

  lazy val okHttpClient = {
    val client = new OkHttpClient

    val responseCacheDir = parentWorkDir / "http-cache"
    responseCacheDir.mkdirs()
    if (responseCacheDir.exists) {
      client.setCache(new okhttp.Cache(responseCacheDir, 5 * 1024 * 1024))
    } else Logger.warn(s"Couldn't create HttpResponseCache dir ${responseCacheDir.path}")

    client
  }

  lazy val githubCredentials = new GitHubCredentials(accessToken, okHttpClient)

  lazy val user = githubCredentials.conn().getMyself
}

object Bot extends Bot {
  import play.api.Play.current
  val config = play.api.Play.configuration.underlying

  val accessToken: String = config.getString("github.access.token")
}