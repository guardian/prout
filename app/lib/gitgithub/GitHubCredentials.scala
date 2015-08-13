package lib.gitgithub

import com.squareup.okhttp.{OkHttpClient, OkUrlFactory}
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.kohsuke.github.GitHub
import org.kohsuke.github.extras.OkHttpConnector


class GitHubCredentials(val oauthAccessToken: String, val okHttpClient: OkHttpClient) {

  def conn() = {
    val gh = GitHub.connectUsingOAuth(oauthAccessToken)
    gh.setConnector(new OkHttpConnector(new OkUrlFactory(okHttpClient)))
    gh
  }

  val git = new UsernamePasswordCredentialsProvider(oauthAccessToken, "")
}
