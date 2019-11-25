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

import java.io.File

import com.madgag.git._
import org.eclipse.jgit.api.{Git, GitCommand, TransportCommand}
import org.eclipse.jgit.lib.Constants.DEFAULT_REMOTE_NAME
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.{CredentialsProvider, RemoteConfig}
import play.api.Logging

import scala.jdk.CollectionConverters._

object RepoUtil extends Logging {

  def getGitRepo(dataDirectory: File, uri: String, credentials: Option[CredentialsProvider] = None): Repository = {

    def invoke[C <: GitCommand[_], R](command: TransportCommand[C, R]): R = {
      command.setTimeout(5)
      credentials.foreach(command.setCredentialsProvider)
      command.call()
    }

    def getUpToDateRepo(): Repository = {
      dataDirectory.mkdirs()

      val gitdir = dataDirectory.toPath.resolve("repo.git").toFile

      if (gitdir.exists) {
        val gitDirChildren = gitdir.list().toSeq.sorted
        assert(gitDirChildren.nonEmpty, s"No child files found in ${gitdir.getAbsolutePath}")
        logger.info(s"Updating Git repo with fetch... $uri")
        val repo = FileRepositoryBuilder.create(gitdir)
        val remoteConfig = new RemoteConfig(repo.getConfig, DEFAULT_REMOTE_NAME)
        val remoteUris = remoteConfig.getURIs.asScala

        val remoteUri = remoteUris.headOption.getOrElse(throw new IllegalStateException(s"No remote configured for $uri")).toString
        assert(remoteUri == uri, s"Wrong uri - expected $uri, got $remoteUri")
        invoke(repo.git.fetch())
        repo
      } else {
        gitdir.getParentFile.mkdirs()
        logger.info(s"Cloning new Git repo... $uri")
        invoke(Git.cloneRepository().setBare(true).setDirectory(gitdir).setURI(uri)).getRepository
      }
    }

    getUpToDateRepo()
  }

}
