package lib

import com.madgag.playgithub.auth.Client
import play.api.Configuration

class GithubAppConfig(config: Configuration) {

  val authClient = {
    val clientId = config.get[String]("github.clientId")
    val clientSecret = config.get[String]("github.clientSecret")

    Client(clientId, clientSecret)
  }

}

