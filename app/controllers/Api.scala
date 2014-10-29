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

import lib.Config.Checkpoint
import lib._
import lib.actions.BasicAuth._
import lib.actions.Parsers
import org.kohsuke.github.GitHub
import play.api.mvc._

import scala.concurrent.Future

object Api extends Controller {

  implicit val checkpointSnapshoter: Checkpoint => Future[CheckpointSnapshot] = CheckpointSnapshot(_)

  def githubHook() = Action(Parsers.githubHookRepository) { request =>
    val repoFullName: RepoFullName = request.body
    Scanner.updateFor(repoFullName)
    NoContent
  }

  def updateRepo(repoOwner: String, repoName: String) = {
    val repoFullName = RepoFullName(repoOwner, repoName)
    basicAuth {
      creds => Some(GitHub.connectUsingOAuth(creds.username)).filter(_.getRepository(repoFullName.text).hasPullAccess)
    } { implicit req =>
      Scanner.updateFor(repoFullName)
      NoContent
    }
  }
}
