package lib

import io.lemonlabs.uri.typesafe.dsl._
import lib.Config.{Checkpoint, CheckpointDetails}
import org.joda.time.Period
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatestplus.play.PlaySpec

class CheckpointSpec extends PlaySpec with ScalaFutures with Eventually with IntegrationPatience {

  "Checkpoint snapshots" must {

    "be able to hit Ophan" in {
      val checkpoint =
        Checkpoint("PROD", CheckpointDetails("https://dashboard.ophan.co.uk/login", Period.parse("PT1H")))
      whenReady(CheckpointSnapshoter.snapshot(checkpoint)) { _ must not be empty }
    }

    // TODO: We add this test once this gets merged: https://github.com/guardian/frontend/pull/25795
//    "be able to hit the UK network front" in {
//      val checkpoint =
//        Checkpoint("CODE", CheckpointDetails("https://m.code.dev-theguardian.com/uk", Period.parse("PT1H")))
//      whenReady(CheckpointSnapshoter.snapshot(checkpoint)) { gitCommitIds =>
//        val commitIds = gitCommitIds.toList
//        println("Git commit ids:" + commitIds)
//        println(commitIds.length)
//        commitIds must not be empty
//      }
//    }
  }
}