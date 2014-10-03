package lib.actions

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.binary.Base64.decodeBase64
import play.api.libs.Codecs
import play.api.mvc.Results.Unauthorized
import play.api.mvc.Security.AuthenticatedBuilder
import play.api.mvc._


object Functions {

  def sign(message: Array[Byte], key: Array[Byte]): String = {
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(new SecretKeySpec(key, "HmacSHA1"))
    Codecs.toHexString(mac.doFinal(message))
  }
}

object BasicAuth {

  case class Credentials(username: String, password: String)

  private val requestBasicAuth = Unauthorized.withHeaders("WWW-Authenticate" -> """Basic realm="Secured"""")

  // derived from https://gist.github.com/guillaumebort/2328236
  def credentialsFromAuth(authorization: String): Option[Credentials] = {
    authorization.split(" ").drop(1).headOption.flatMap { encoded =>
      new String(decodeBase64(encoded.getBytes)).split(":").toList match {
        case u :: p :: Nil => Some(Credentials(u, p))
        case _ => None
      }
    }
  }

  def validUserFor[U](req: RequestHeader, userForCredentials: Credentials => Option[U]): Option[U] = for {
    authorizationHeader <- req.headers.get("Authorization")
    credentials <- credentialsFromAuth(authorizationHeader)
    validUser <- userForCredentials(credentials)
  } yield validUser

  def basicAuth[U](userForCredentials: Credentials => Option[U]) =
    new AuthenticatedBuilder(req => validUserFor(req, userForCredentials), _ => requestBasicAuth)

}