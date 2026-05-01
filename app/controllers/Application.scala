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

import org.apache.pekko.actor.ActorSystem
import com.madgag.scalagithub.model.RepoId
import lib.{Bot, RepoSnapshot}
import play.api.Logging
import play.api.mvc.{Action, AnyContent}

import scala.concurrent.ExecutionContext
import cats.effect.unsafe.implicits.global

class Application(
  repoAcceptListService: RepoAcceptListService,
  repoSnapshotFactory: RepoSnapshot.Factory,
  sentryApiClientOpt: Option[lib.sentry.SentryApiClient],
  cc: ControllerAppComponents
)(implicit
  ec: ExecutionContext,
  bot: Bot
) extends AbstractAppController(cc) with Logging {

  def index: Action[AnyContent] = Action { implicit req =>
    Ok(views.html.userPages.index())
  }

  def configDiagnostic(repoId: RepoId) = repoAuthenticated(repoId).async { implicit req =>
    (for {
      repoFetchedByProut <- bot.github.getRepo(repoId)
      repo = repoFetchedByProut.result
      proutPresenceQuickCheck <- repoAcceptListService.hasProutConfigFile(repo)
      repoSnapshot <- repoSnapshotFactory.snapshotRepo(using repo)
      diagnostic <- repoSnapshot.diagnostic()
    } yield {
      Ok(views.html.userPages.repo(proutPresenceQuickCheck, repoSnapshot, diagnostic, sentryApiClientOpt))
    }).unsafeToFuture()
  }

}
