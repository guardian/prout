package lib

import com.madgag.github.AccessToken
import com.madgag.scalagithub.{GitHub, GitHubCredentials}
import com.madgag.scalagithub.model.User
import org.eclipse.jgit.transport.CredentialsProvider
import play.api.Logging

import java.nio.file.Path
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

case class Bot(
  workingDir: Path,
  gitHubCredsProvider: GitHubCredentials.Provider,
  user: User
) {
  val github = new GitHub(gitHubCredsProvider)
}

object Bot extends Logging {
  def forAccessToken(accessToken: String)(implicit ec: ExecutionContext): Bot = {
    val workingDir = Path.of("/tmp", "bot", "working-dir")

    val credentialsProvider: GitHubCredentials.Provider =
      GitHubCredentials.Provider.fromStatic(AccessToken(accessToken))

    val github: GitHub = new GitHub(credentialsProvider)
    val user: User = Await.result(github.getUser().map(_.result), 3.seconds)
    logger.info(s"Token gives GitHub user ${user.atLogin}")

    Bot(
      workingDir,
      credentialsProvider,
      user
    )
  }
}