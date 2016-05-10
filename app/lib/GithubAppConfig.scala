package lib

import com.madgag.playgithub.auth.Client

object GithubAppConfig {

  import play.api.Play.current
  val config = play.api.Play.configuration

  val authClient = {
    val clientId = config.getString("github.clientId").getOrElse("blah")
    val clientSecret = config.getString("github.clientSecret").getOrElse("blah")

    Client(clientId, clientSecret)
  }

}

