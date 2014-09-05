package lib.actions

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import play.api.libs.Codecs



object Functions {

  def sign(message: Array[Byte], key: Array[Byte]): String = {
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(new SecretKeySpec(key, "HmacSHA1"))
    Codecs.toHexString(mac.doFinal(message))
  }
}
