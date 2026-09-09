package lib.sentry.model

import com.madgag.scalagithub.model.RepoId
import lib.sentry.model.Sentry.*
import org.eclipse.jgit.lib.ObjectId
import play.api.libs.json.{JsString, Json, OWrites, Writes}
import sttp.model.*

import java.time.Instant

object Sentry {
  implicit val writesObjectId: Writes[ObjectId] = (oid: ObjectId) => JsString(oid.name)

}

/**
commits (array) – an optional list of commit data to be associated with the release.
Commits must include parameters id (the sha of the commit), and can optionally include
repository, message, author_name, author_email, and timestamp.
*/
case class Commit(
  id: ObjectId,
  repository: Option[String],
  message: Option[String],
  author_name: Option[String],
  author_email: Option[String],
  timestamp: Option[Instant]
)

object Commit {
  implicit val writesCommit: OWrites[Commit] = Json.writes[Commit]
}

case class Ref(
  repository: RepoId,
  commit: ObjectId,
  previousCommit: Option[ObjectId] = None
)

object Ref {
  implicit val writesRepoId: Writes[RepoId] = new Writes[RepoId] {
    def writes(repoId: RepoId) = JsString(repoId.fullName)
  }
  implicit val writesRef: OWrites[Ref] = Json.writes[Ref]
}

/*
https://docs.sentry.io/api/releases/post-organization-releases/

version (string) – a version identifier for this release. Can be a version number, a commit hash etc.
ref (string) – an optional commit reference. This is useful if a tagged version has been provided.
url (url) – a URL that points to the release. This can be the path to an online interface to the sourcecode for instance.
projects (array) – a list of project slugs that are involved in this release
dateReleased (datetime) – an optional date that indicates when the release went live. If not provided the current time is assumed.
commits (array) – an optional list of commit data to be associated with the release. Commits must include parameters id (the sha of the commit), and can optionally include repository, message, author_name, author_email, and timestamp.
refs (array) – an optional way to indicate the start and end commits for each repository included in a release. Head commits must include parameters repository and commit (the HEAD sha). They can optionally include previousCommit (the sha of the HEAD of the previous release), which should be specified if this is the first time you’ve sent commit data.
 */
case class CreateRelease(
  version: String,
  ref: Option[String] = None,
  url: Option[Uri] = None,
  projects: Seq[String],
  dateReleased: Option[Instant] = None,
  commits: Seq[Commit] = Seq.empty,
  refs: Seq[Ref] = Seq.empty
)

object CreateRelease {
  implicit val writesUri: Writes[Uri] = new Writes[Uri] {
    def writes(uri: Uri) = JsString(uri.toString)
  }

  implicit val writesCreateRelease: OWrites[CreateRelease] = Json.writes[CreateRelease]
}