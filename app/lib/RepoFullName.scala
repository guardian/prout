package lib

object RepoFullName {
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
