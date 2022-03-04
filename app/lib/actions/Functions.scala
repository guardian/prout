package lib.actions

import play.api.libs.Codecs

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Functions {

  def sign(message: Array[Byte], key: Array[Byte]): String = {
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(new SecretKeySpec(key, "HmacSHA1"))
    Codecs.toHexString(mac.doFinal(message))
  }
}