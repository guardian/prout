package lib

import com.netaporter.uri.Uri
import lib.Config.CheckpointDetails
import org.joda.time.Period.minutes
import org.scalatestplus.play._
import play.api.libs.json.{JsResult, JsSuccess, Json}

import scalax.io.JavaConverters._

class ConfigSpec extends PlaySpec {

   "Config json parsing" must {
     "parse normal Checkpoint config" in {
       val details = checkpointDetailsFrom("/sample.checkpoint.json")

       details mustEqual JsSuccess(CheckpointDetails(Uri.parse("https://membership.theguardian.com/"), minutes(14)))
       details.get.sslVerification mustBe true
      }

     "parse insecure config" in {
       checkpointDetailsFrom("/sample.insecure.checkpoint.json").get.sslVerification mustBe false
     }
   }

  def checkpointDetailsFrom(resourcePath: String): JsResult[CheckpointDetails] = {
    Json.parse(getClass.getResource(resourcePath).asInput.byteArray).validate[CheckpointDetails]
  }
}