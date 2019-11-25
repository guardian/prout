package lib

import java.io.File
import java.nio.file.Paths

import com.madgag.scalagithub.model.User
import com.madgag.scalagithub.{GitHub, GitHubCredentials}
import okhttp3.OkHttpClient
import play.api.{Logger, Logging}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

trait Bot extends Logging {

  val accessToken: String

  val parentWorkDir = Paths.get("/tmp/bot/working-dir")

  parentWorkDir.toFile.mkdirs()

  lazy val okHttpClient = {
    val clientBuilder = new OkHttpClient.Builder()

    val responseCacheDir = parentWorkDir.resolve("http-cache").toFile
    responseCacheDir.mkdirs()
    if (responseCacheDir.exists) {
      clientBuilder.cache(new okhttp3.Cache(responseCacheDir, 5 * 1024 * 1024))
    } else logger.warn(s"Couldn't create HttpResponseCache dir ${responseCacheDir.getAbsolutePath}")

    clientBuilder.build()
  }

  lazy val githubCredentials = GitHubCredentials.forAccessKey(accessToken, parentWorkDir.resolve("http-cache")).get

  lazy val github = new GitHub(githubCredentials)

  lazy val user: User = {
    val myself = Await.result(github.getUser(), 3 seconds)
    logger.info(s"Token '${accessToken.take(2)}...' gives GitHub user ${myself.atLogin}")
    myself
  }

}

object Bot extends Bot {

  val accessToken: String = config.get[String]("github.access.token")

}