package lib

import java.security.cert.X509Certificate

import javax.net.ssl._
import play.api.Logging

object SSL extends Logging {

  val InsecureSocketFactory = {
    val sslcontext = SSLContext.getInstance("TLS")
    sslcontext.init(null, Array(TrustEveryoneTrustManager), null)
    sslcontext.getSocketFactory
  }

  object TrustEveryoneTrustManager extends X509TrustManager {
    def checkClientTrusted(chain: Array[X509Certificate], authType: String): Unit = {}

    def checkServerTrusted(chain: Array[X509Certificate], authType: String): Unit = {
      logger.warn("Skipping SSL server chain verification")
    }

    val getAcceptedIssuers = new Array[X509Certificate](0)
  }
}
