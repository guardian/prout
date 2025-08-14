package configuration

import com.madgag.github.apps.{GitHubAppAuth, GitHubAppJWTs}
import com.madgag.scalagithub.GitHub
import com.softwaremill.macwire._
import controllers._
import lib._
import lib.actions.Actions
import lib.sentry.SentryApiClient
import monitoring.SentryLogging
import org.apache.pekko.actor.ActorSystem
import play.api.routing.Router
import play.api.{ApplicationLoader, BuiltInComponentsFromContext, Logging}
import router.Routes

import java.nio.file.Path
import scala.concurrent.Await
import scala.concurrent.duration._

class ApplicationComponents(context: ApplicationLoader.Context)
  extends BuiltInComponentsFromContext(context) with ReasonableHttpFilters
    with AssetsComponents with Logging {
  val sentryLogging: SentryLogging = wire[SentryLogging]
  sentryLogging.init() // Is this the best place to start this?!

  implicit val as: ActorSystem = actorSystem

  implicit val checkpointSnapshoter: CheckpointSnapshoter = CheckpointSnapshoter

  val workingDir: Path = Path.of("/tmp", "bot", "working-dir")

  val gitHubAppJWTs = new GitHubAppJWTs(
    configuration.get[String]("github.app.clientId"),
    GitHubAppJWTs.parsePrivateKeyFrom(configuration.get[String]("github.app.privateKey")).get
  )

  val githubAppAuth = new GitHubAppAuth(gitHubAppJWTs)

  implicit val bot: Bot = Await.result(Bot.forGithubApp(githubAppAuth), 3.seconds)

  implicit val github: GitHub = bot.github

  implicit val authClient: com.madgag.playgithub.auth.Client = com.madgag.playgithub.auth.Client(
    id = configuration.get[String]("github.app.clientId"),
    secret = configuration.get[String]("github.app.clientSecret")
  )

  val delayer: Delayer = wire[Delayer]
  val repoSnapshotFactory: RepoSnapshot.Factory = wire[RepoSnapshot.Factory]

  implicit val sentryApiClient: Option[SentryApiClient] = SentryApiClient.instanceOptFrom(configuration)
  val repoUpdater: RepoUpdater = wire[RepoUpdater]
  val prUpdater: PRUpdater = wire[PRUpdater]
  val droid: Droid = wire[Droid]
  val scanSchedulerFactory: ScanScheduler.Factory = wire[ScanScheduler.Factory]
  val repoAcceptListService: RepoAcceptListService = wire[RepoAcceptListService]
  repoAcceptListService.start() // Is this the best place to start this?!

  val actions: Actions = wire[Actions]
  val controllerAppComponents: ControllerAppComponents = wire[ControllerAppComponents]

  val apiController: Api = wire[Api]
  val appController: Application = wire[Application]
  val authController: Auth = wire[_root_.controllers.Auth]

  val router: Router = {
    // add the prefix string in local scope for the Routes constructor
    val prefix: String = "/"
    wire[Routes]
  }

}
