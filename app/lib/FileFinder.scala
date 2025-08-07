package lib

import com.madgag.git._
import org.eclipse.jgit.lib.{ObjectId, ObjectReader}
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.treewalk.TreeWalk

import scala.util.Try

class FileFinder(commit: RevCommit)(implicit repoThreadLocal: ThreadLocalObjectDatabaseResources) {

  implicit val reader: ObjectReader = repoThreadLocal.reader()

  private[lib] def objectIdForPath(path: String): Option[ObjectId] =
    Try(
      TreeWalk
        .forPath(reader, path, commit.getTree)
        .getObjectId(0)
    ).toOption

  def read(path: String): Option[String] = {
    objectIdForPath(path).map { objectId =>
      val bytes: Array[Byte] = objectId.open.getCachedBytes(4096)
      new String(bytes, "UTF-8")
    }
  }
}

