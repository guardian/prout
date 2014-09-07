package lib.actions

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import play.api.Logger
import play.api.libs.iteratee.{Iteratee, Traversable}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.{Codecs, Crypto}
import play.api.mvc.{BodyParser, RequestHeader, Result, Results}

import scala.concurrent.Future
import scala.util.control.NonFatal

object Parsers {

  def assertSecureEquals(s1: String, s2: String) = {
    assert(Crypto.constantTimeEquals(s1, s2), "HMAC signatures did not match")
    Logger.debug("HMAC Signatures matched!")
  }

  def githubHookJson(sharedSecret: String): BodyParser[JsValue] = tolerantXHubSigned(sharedSecret, "json", 128 * 1024, "Invalid Json") {
    (requestHeader, bytes) => Json.parse(bytes)
  }

  type FullBodyParser[+A] = (RequestHeader, Array[Byte]) => Either[Result, A]

  /*
  The 'X-Hub-Signature' header is defined by the PubSubHubbub Protocol:

  https://pubsubhubbub.googlecode.com/git/pubsubhubbub-core-0.4.html#authednotify ("8. Authenticated Content Distribution")

   */
  def tolerantXHubSigned[A](sharedSecret: String, name: String, maxLength: Int, errorMessage: String)(parser: (RequestHeader, Array[Byte]) => A): BodyParser[A] =
    tolerantBodyParser[A]("json", maxLength, "Invalid Json") { (request, bytes) =>
      assertSecureEquals(request.headers("X-Hub-Signature").replaceFirst("sha1=", ""), sign(bytes, sharedSecret.getBytes))
      parser(request, bytes)
    }

  def tolerantBodyParser[A](name: String, maxLength: Int, errorMessage: String)(parser: (RequestHeader, Array[Byte]) => A): BodyParser[A] =
    BodyParser(name + ", maxLength=" + maxLength) { request =>
      import play.api.libs.iteratee.Execution.Implicits.trampoline
      import scala.util.control.Exception._

      val bodyParser: Iteratee[Array[Byte], Either[Result, Either[Future[Result], A]]] =
        Traversable.takeUpTo[Array[Byte]](maxLength).transform(
          Iteratee.consume[Array[Byte]]().map { bytes =>
            allCatch[A].either {
              parser(request, bytes)
            }.left.map {
              case NonFatal(e) =>
                Future.successful(Results.BadRequest(errorMessage))
              case t => throw t
            }
          }
        ).flatMap(Iteratee.eofOrElse(Results.EntityTooLarge))

      bodyParser.mapM {
        case Left(tooLarge) => Future.successful(Left(tooLarge))
        case Right(Left(badResult)) => badResult.map(Left.apply)
        case Right(Right(body)) => Future.successful(Right(body))
      }
    }


  def sign(message: Array[Byte], key: Array[Byte]): String = {
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(new SecretKeySpec(key, "HmacSHA1"))
    Codecs.toHexString(mac.doFinal(message))
  }
}
