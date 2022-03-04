package lib

import play.api.Logging

import java.security.cert.X509Certificate
import javax.net.ssl._

object SSL {

  val InsecureSocketFactory: SSLSocketFactory = {
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, Array(TrustEveryoneTrustManager), null)
    sslContext.getSocketFactory
  }

  object TrustEveryoneTrustManager extends X509TrustManager with Logging {
    def checkClientTrusted(chain: Array[X509Certificate], authType: String): Unit = {}

    def checkServerTrusted(chain: Array[X509Certificate], authType: String): Unit = {
      logger.warn("Skipping SSL server chain verification")
    }

    val getAcceptedIssuers = new Array[X509Certificate](0)
  }
}
