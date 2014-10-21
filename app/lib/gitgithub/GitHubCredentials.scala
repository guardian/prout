package lib.gitgithub

import java.io.File

import com.squareup.okhttp
import com.squareup.okhttp.{OkHttpClient, OkUrlFactory}
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.kohsuke.github.GitHub
import org.kohsuke.github.extras.OkHttpConnector
import play.api.Logger

import scalax.file.ImplicitConversions._


class GitHubCredentials(oauthAccessToken: String, okHttpClient: OkHttpClient) {

  def conn() = {
    val gh = GitHub.connectUsingOAuth(oauthAccessToken)
    gh.setConnector(new OkHttpConnector(new OkUrlFactory(okHttpClient)))
    gh
  }

  val git = new UsernamePasswordCredentialsProvider(oauthAccessToken, "")
}
