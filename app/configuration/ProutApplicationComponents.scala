package configuration

import controllers.{AssetsComponents, RepoWhitelistService}
import play.api.cache.ehcache.EhCacheComponents
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.routing.Router
import play.api.{ApplicationLoader, BuiltInComponentsFromContext}
import com.softwaremill.macwire._
import lib.Delayer
import lib.travis.{TravisApi, TravisApiClient, TravisCIOffering}
import monitoring.SentryLogging


class ProutApplicationComponents(context: ApplicationLoader.Context)
  extends BuiltInComponentsFromContext(context)
    with AssetsComponents
    with AhcWSComponents
    with EhCacheComponents {

  val controllerOphanComponents = ControllerOphanComponents(
    authAction,
    apiAction,
    queryAction,
    defaultActionBuilder,
    playBodyParsers,
    messagesApi,
    langs,
    fileMimeTypes,
    executionContext
  )

  val travisApiClientsByOffering: Map[TravisCIOffering, TravisApiClient] =
    TravisApi.travisApiClientsByOfferingGiven(configuration)

  val delayer: Delayer = wire[Delayer]
  val repoWhitelistService = wire[RepoWhitelistService]
  val sentryLogging: SentryLogging = ???

  sentryLogging.init()
  repoWhitelistService.start()

  val router: Router = {
    // add the prefix string in local scope for the Routes constructor
    val prefix: String = "/"
    wire[Routes]
  }

}