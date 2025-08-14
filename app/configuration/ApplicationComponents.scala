package configuration

import com.madgag.scalagithub.GitHub
import com.softwaremill.macwire._
import controllers._
import lib.actions.Actions
import lib.sentry.SentryApiClient
import lib._
import monitoring.SentryLogging
import org.apache.pekko.actor.ActorSystem
import play.api.routing.Router
import play.api.{ApplicationLoader, BuiltInComponentsFromContext, Logging}
import router.Routes

import java.nio.file.Path

class ApplicationComponents(context: ApplicationLoader.Context)
  extends BuiltInComponentsFromContext(context) with ReasonableHttpFilters
    with AssetsComponents with Logging {
  val sentryLogging: SentryLogging = wire[SentryLogging]
  sentryLogging.init() // Is this the best place to start this?!

  implicit val as: ActorSystem = actorSystem

  implicit val checkpointSnapshoter: CheckpointSnapshoter = CheckpointSnapshoter

  val workingDir: Path = Path.of("/tmp", "bot", "working-dir")

  implicit val bot: Bot = Bot.forAccessToken(configuration.get[String]("github.botAccessToken"))

  implicit val github: GitHub = bot.github

  implicit val authClient: com.madgag.playgithub.auth.Client = com.madgag.playgithub.auth.Client(
    id = configuration.get[String]("github.clientId"),
    secret = configuration.get[String]("github.clientSecret")
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