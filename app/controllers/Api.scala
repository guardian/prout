/*
 * Copyright 2014 The Guardian
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import com.github.blemale.scaffeine.{LoadingCache, Scaffeine}
import com.madgag.scalagithub.model.RepoId
import lib._
import play.api.libs.json.{JsArray, JsNumber}
import play.api.mvc._

import scala.concurrent.Future

class Api(
  scanSchedulerFactory: ScanScheduler.Factory,
  repoAcceptListService: RepoAcceptListService,
  delayer: Delayer,
  cc: ControllerAppComponents
) extends AbstractAppController(cc) {

  def githubHook() = Action.async(parse.json) { implicit request =>
    val githubDeliveryGuid = request.headers.get("X-GitHub-Delivery")

    request.headers.get("X-Github-Event") match {
      case Some("ping") =>
        // Always say hello! If you set up webhooks at the organisation level the initial ping will not have repository data
        logger.info(s"githubHook event=ping githubDeliveryGuid=$githubDeliveryGuid xRequestId=$xRequestId")
        Future.successful(Ok("pong"))

      case event =>
        val repoId = (request.body \ "repository" \ "full_name").validate[String].map(RepoId.from).get
        val githubDeliveryGuid = request.headers.get("X-GitHub-Delivery")

        logger.info(s"githubHook event=${event.getOrElse("unknown")} repo=${repoId.fullName} githubDeliveryGuid=$githubDeliveryGuid xRequestId=$xRequestId")
        updateFor(repoId)
    }
  }

  def updateRepo(repoId: RepoId) = Action.async { implicit request =>
    logger.info(s"updateRepo repo=${repoId.fullName} xRequestId=$xRequestId")
    updateFor(repoId)
  }

  def xRequestId(implicit request: RequestHeader): Option[String] = request.headers.get("X-Request-ID")

  def updateFor(repoId: RepoId): Future[Result] = {
    logger.debug(s"update requested for $repoId")
    for {
      acceptList <- repoAcceptListService.acceptList()
      update <- updateFor(repoId, acceptList)
    } yield update
  }

  val repoScanSchedulerCache: LoadingCache[RepoId, ScanScheduler] = Scaffeine()
    .recordStats()
    .maximumSize(500)
    .build(scanSchedulerFactory.createFor)

  def updateFor(repoId: RepoId, acceptList: RepoAcceptList): Future[Result] = {
    val scanGuardF = Future { // wrapped in a future to avoid timing attacks
      val knownRepo = acceptList.allKnownRepos(repoId)
      logger.info(s"$repoId known=$knownRepo")
      require(knownRepo, s"${repoId.fullName} not on known-repo whitelist")

      val scanScheduler = repoScanSchedulerCache.get(repoId)
      logger.debug(s"$repoId scanScheduler=$scanScheduler")

      val firstScanF = scanScheduler.scan()

      firstScanF.onComplete { _ => delayer.delayTheFuture {
        /* Do a *second* scan shortly after the first one ends, to cope with:
         * 1. Latency in GH API
         * 2. Checkpoint site stabilising on the new version after deploy
         */
          scanScheduler.scan()
        }
      }

      firstScanF
    }
    val mightBePrivate = !acceptList.publicRepos(repoId)
    if (mightBePrivate) {
      // Response must be immediate, with no private information (e.g. even acknowledging that repo exists)
      Future.successful(NoContent)
    } else {
      // we can delay the response to return information about the repo config, and the updates generated
      for {
        scanGuard <- scanGuardF
        scan <- scanGuard
      } yield Ok(JsArray(scan.map(summary => JsNumber(summary.prCheckpointDetails.pr.number))))
    }
  }
}
