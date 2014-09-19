package lib.actions

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import org.apache.commons.codec.binary.Base64
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

  private val requestBasicAuth = Unauthorized.withHeaders("WWW-Authenticate" -> """Basic realm="Secured"""")

  // derived from https://gist.github.com/guillaumebort/2328236
  def basicAuth[U](validUserForCredentials: (String, String) => Option[U]) =
    new AuthenticatedBuilder(req =>
      req.headers.get("Authorization").flatMap { authorization =>
        authorization.split(" ").drop(1).headOption.flatMap { encoded =>
          new String(Base64.decodeBase64(encoded.getBytes)).split(":").toList match {
            case u :: p :: Nil => validUserForCredentials(u, p)
            case _ => None
          }
        }
      }
      , _ => requestBasicAuth)

}
