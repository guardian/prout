package lib

import com.squareup.okhttp
import com.squareup.okhttp.{OkHttpClient, OkUrlFactory}
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.kohsuke.github.GitHub
import org.kohsuke.github.extras.OkHttpConnector
import play.api.Logger

import scalax.file.ImplicitConversions._
import scalax.file.Path

object Bot {
  import play.api.Play.current
  val config = play.api.Play.configuration.underlying

  val parentWorkDir = Path.fromString("/tmp") / "bot" / "working-dir"

  val accessToken: String = config.getString("github.access.token")

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

  def conn() = {
    val gh = GitHub.connectUsingOAuth(accessToken)
    gh.setConnector(new OkHttpConnector(new OkUrlFactory(okHttpClient)))
    gh
  }

  val gitCredentials = new UsernamePasswordCredentialsProvider(accessToken, "")

  val user = conn().getMyself
}
