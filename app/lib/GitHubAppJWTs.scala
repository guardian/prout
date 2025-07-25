package lib

import com.github.blemale.scaffeine.{LoadingCache, Scaffeine}
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm.RS256
import lib.GitHubAppJWTs.{CacheLifeTime, MaxLifeTime}
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.{PEMKeyPair, PEMParser}
import play.api.Logging

import java.io.StringReader
import java.security.PrivateKey
import java.time.Clock.systemUTC
import java.time.Duration.ofMinutes
import java.time.{Clock, Duration}
import java.util.Date
import scala.jdk.DurationConverters._
import scala.util.{Try, Using}

class GitHubAppJWTs(
  appClientId: String,
  privateKey: PrivateKey
) extends Logging {
  private val cache: LoadingCache[Unit, String] = Scaffeine()
    .expireAfterWrite(CacheLifeTime.toScala)
    .maximumSize(1)
    .build(_ => generate())

  private def generate()(implicit clock: Clock = systemUTC()): String = {
    val now = clock.instant()
    val expiration = now.plus(MaxLifeTime)
    logger.info(s"Generating JWT: expiration=$expiration")
    Jwts.builder()
      .setIssuer(appClientId)
      .setIssuedAt(Date.from(now.minusSeconds(60)))
      .setExpiration(Date.from(expiration))
      .signWith(privateKey, RS256)
      .compact()
  }

  def currentJWT(): String = cache.get(())
}

object GitHubAppJWTs {
  val MaxLifeTime: Duration = ofMinutes(10) // https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-json-web-token-jwt-for-a-github-app

  val CacheLifeTime: Duration = MaxLifeTime.multipliedBy(9).dividedBy(10) // Avoid race-condition near expiry

  def parsePrivateKeyFrom(privateKeyPem: String): Try[PrivateKey] = {
    val converter = new JcaPEMKeyConverter()
    Using(new PEMParser(new StringReader(privateKeyPem))) { pemParser =>
      val keyObject = pemParser.readObject()
      converter.getPrivateKey(keyObject.asInstanceOf[PEMKeyPair].getPrivateKeyInfo)
    }
  }
}