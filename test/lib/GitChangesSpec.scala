package lib

import com.madgag.git._
import lib.GitChanges.affectedFolders
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.scalatestplus.play._

class GitChangesSpec extends PlaySpec {

  "Multi folder config" must {

    "detect changes in top-level folder" in {
      implicit val localGitRepo: Repository = test.unpackRepo("/simple.git.zip")
      implicit val (revWalk, reader) = localGitRepo.singleThreadedReaderTuple

      def commitAt(revstr: String) = localGitRepo.resolve(revstr).asRevCommit

      val modTopLevel: RevCommit = commitAt("mod-top-level")
      val master: RevCommit = commitAt("master")

      affectedFolders(master, modTopLevel, Set("/")) mustEqual(Set("/"))
      affectedFolders(master, modTopLevel, Set("/bar/")) mustEqual(Set.empty)
    }

    "detect changes in affected folders" in {
      implicit val localGitRepo: Repository = test.unpackRepo("/multi-folder.git.zip")
      implicit val (revWalk, reader) = localGitRepo.singleThreadedReaderTuple

      def commitAt(revstr: String) = localGitRepo.resolve(revstr).asRevCommit

      val fooBarBaz = Set("/foo/", "/bar/", "/baz/")

      affectedFolders(commitAt("master"), commitAt("mod-foo"), fooBarBaz) mustEqual(Set("/foo/"))
      affectedFolders(commitAt("master"), commitAt("mod-foo"), Set("/bar/")) mustEqual(Set.empty)

      affectedFolders(commitAt("master"), commitAt("mod-foo-and-bar"), fooBarBaz) mustEqual(Set("/foo/", "/bar/"))

      affectedFolders(commitAt("master"), commitAt("del-baz-thing"), fooBarBaz) mustEqual(Set("/baz/"))
    }

    "not confuse changes on master with changes on the feature branch" in {
      implicit val localGitRepo: Repository = test.unpackRepo("/multi-project.master-updated-before-feature-merged.git.zip")
      implicit val (revWalk, reader) = localGitRepo.singleThreadedReaderTuple

      def commitAt(revstr: String) = localGitRepo.resolve(revstr).asRevCommit

      affectedFolders(commitAt("master"), commitAt("food-feature"), Set("/food/")) mustEqual(Set("/food/"))

      affectedFolders(commitAt("master"), commitAt("bard-feature"), Set("/food/")) mustBe empty
    }
  }
}