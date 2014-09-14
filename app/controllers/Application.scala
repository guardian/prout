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

import com.netaporter.uri.Uri
import lib._
import lib.actions.Parsers
import play.api.mvc._

object Application extends Controller {

  def updateRepo(repoOwner: String, repoName: String, checkpoint: String) = Action { implicit req =>
    Scanner.updateFor(checkpoint, RepoFullName(repoOwner, repoName))
    NoContent
  }

  def snsHook(repoOwner: String, repoName: String, checkpoint: String) = Action(parse.json) { request =>
    for (message <- (request.body \ "Message").validate[String]) {
      Scanner.updateFor(checkpoint, RepoFullName(repoOwner, repoName))
    }
    NoContent
  }

  def githubHook(checkpoint: String) = Action(Parsers.githubHookJson("monkey")) { request =>
    for (repoFullName <- (request.body \ "repository" \ "full_name").validate[String]) {
      Scanner.updateFor(checkpoint, RepoFullName(repoFullName))
    }
    NoContent
  }


  def index = Action { implicit req =>
    Ok(views.html.userPages.index())
  }

}
