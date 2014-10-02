package lib

import com.madgag.git._
import lib.GitChanges.affectedFolders
import org.eclipse.jgit.lib.Repository
import org.scalatestplus.play._

class GitChangesSpec extends PlaySpec {

  "Multi folder config" must {

    implicit val localGitRepo: Repository = test.unpackRepo("/multi-folder.git.zip")

    "detect changes in affected folders" in {
      implicit val (revWalk, reader) = localGitRepo.singleThreadedReaderTuple

      def commitAt(revstr: String) = localGitRepo.resolve(revstr).asRevCommit

      val fooBarBaz = Set("foo", "bar", "baz")

      affectedFolders(commitAt("master"), commitAt("mod-foo"), fooBarBaz) mustEqual(Set("foo"))
      affectedFolders(commitAt("master"), commitAt("mod-foo"), Set("bar")) mustEqual(Set.empty)


      affectedFolders(commitAt("master"), commitAt("mod-foo-and-bar"), fooBarBaz) mustEqual(Set("foo", "bar"))

      affectedFolders(commitAt("master"), commitAt("del-baz-thing"), fooBarBaz) mustEqual(Set("baz"))
    }
  }
}