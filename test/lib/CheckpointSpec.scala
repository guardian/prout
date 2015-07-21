package lib

import com.netaporter.uri.dsl._
import lib.Config.{Checkpoint, CheckpointDetails}
import org.joda.time.Period
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatestplus.play.PlaySpec

class CheckpointSpec extends PlaySpec with ScalaFutures with Eventually with IntegrationPatience {

  "Checkpoint snapshots" must {

    "be able to hit Ophan" in {
      val checkpoint = Checkpoint("PROD", CheckpointDetails("https://dashboard.ophan.co.uk/login", Period.parse("PT1H")))
      whenReady(CheckpointSnapshot(checkpoint)) { s =>
        s must not be 'empty
      }
    }
  }
}