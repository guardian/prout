package lib

import org.kohsuke.github.GHRepository

object RepoFullName {
  def apply(repo: GHRepository): RepoFullName = apply(repo.getFullName)

  def apply(fullName: String): RepoFullName = {
    fullName.split("/").toList match {
      case owner :: name :: Nil => RepoFullName(owner, name)
      case _ => throw new IllegalArgumentException
    }
  }
}

case class RepoFullName(owner: String, name: String) {
  val text = s"$owner/$name"
}
