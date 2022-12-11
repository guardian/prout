package lib

import com.madgag.playgithub.auth.Client
import play.api.Configuration

object GithubAppConfig {

  def authClientOpt(config: Configuration): Option[Client] = for {
    clientId <- config.getOptional[String]("github.clientId")
    clientSecret <- config.getOptional[String]("github.clientSecret")
  } yield Client(clientId, clientSecret)

}

