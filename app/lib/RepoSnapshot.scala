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

package lib

import com.madgag.git._
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.{RevWalk, RevCommit}
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.kohsuke.github._
import play.api.Logger

import scala.collection.convert.wrapAsScala._
import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scalax.file.ImplicitConversions._

object RepoSnapshot {

  def apply(githubRepo: GHRepository): Future[RepoSnapshot] = {
    val conn = Bot.conn()

    def isMergedToMaster(pr: GHPullRequest): Boolean = pr.isMerged && pr.getBase.getRef == githubRepo.getMasterBranch

    val mergedPullRequestsF = Future {
      githubRepo.listPullRequests(GHIssueState.CLOSED).iterator().filter(isMergedToMaster).take(50).toList
    } andThen { case cprs => Logger.info(s"Merged Pull Requests fetched: ${cprs.map(_.map(_.getNumber).sorted.reverse)}") }

    val gitRepoF = Future {
      RepoUtil.getGitRepo(
        Bot.parentWorkDir,
        githubRepo.gitHttpTransportUrl,
        Some(Bot.gitCredentials))
    } andThen { case r => Logger.info(s"Git Repo ref count: ${r.map(_.getAllRefs.size)}") }

    for {
      mergedPullRequests <- mergedPullRequestsF
      gitRepo <- gitRepoF
    } yield RepoSnapshot(githubRepo, gitRepo, mergedPullRequests)
  }
}

case class RepoSnapshot(
  repo: GHRepository,
  gitRepo: Repository,
  mergedPullRequests: Seq[GHPullRequest]) {

  implicit val revWalk = new RevWalk(gitRepo)

  lazy val masterCommit:RevCommit = gitRepo.resolve(repo.getMasterBranch).asRevCommit

  lazy val config = ConfigFinder.config(masterCommit)
}
