package lib

import org.eclipse.jgit.lib.{AbbreviatedObjectId, Repository, ObjectId}

import com.madgag.git._

object TextCommitIdExtractor {

  val hexRegex = """\b\p{XDigit}{40}\b""".r

  def extractCommit(text: String)(implicit gitRepo: Repository): Option[ObjectId] = {
    val reader = gitRepo.newObjectReader()
    hexRegex.findAllIn(text).flatMap(t => reader.resolveExistingUniqueId(AbbreviatedObjectId.fromString(t)).toOption).toTraversable.headOption
  }
}
