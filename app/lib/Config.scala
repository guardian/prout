package lib

import com.madgag.git._
import com.netaporter.uri.Uri
import com.github.nscala_time.time.Implicits._
import org.eclipse.jgit.lib.{ObjectReader, ObjectId}
import org.joda.time.Period
import play.api.data.validation.ValidationError
import play.api.libs.json._
import play.api.libs.json.Json

object Config {

  def readsParseableString[T](parser: String => T): Reads[T] = new Reads[T] {
    def reads(json: JsValue): JsResult[T] = json match {
      case JsString(s) => parse(s) match {
        case Some(d) => JsSuccess(d)
        case None => JsError(Seq(JsPath() -> Seq(ValidationError("Error parsing string"))))
      }
      case _ => JsError(Seq(JsPath() -> Seq(ValidationError("Expected string"))))
    }

    private def parse(input: String): Option[T] =
    scala.util.control.Exception.allCatch[T] opt (parser(input))

  }

  implicit val readsPeriod: Reads[Period] = readsParseableString(input => Period.parse("PT"+input))

  implicit val readsUri: Reads[Uri] = readsParseableString(input => Uri.parse(input))

  implicit val readsCheckpointDetails: Reads[CheckpointDetails] = Json.reads[CheckpointDetails]

//  implicit val readsCheckpoints: Reads[Set[Checkpoint]] = Json.reads[Map[String,CheckpointDetails]].map { m =>
//    m.map {
//      case (name, details) => Checkpoint(name, details)
//    }.toSet
//  }

  def readConfigFrom(configFileObjectId: ObjectId)(implicit objectReader : ObjectReader): Set[Checkpoint] = {
    val parsedMapResult: JsResult[Map[String, CheckpointDetails]] =
      Json.fromJson[Map[String, CheckpointDetails]](Json.parse(configFileObjectId.open.getCachedBytes(4096)))

    println(parsedMapResult)

    parsedMapResult.map[Set[Checkpoint]] { m =>
      m.map {
        case (name, details) => Checkpoint(name, details)
      }.toSet
    }.get
  }

  case class CheckpointDetails(url: Uri, overdue: Period)

  object Checkpoint {
    implicit def checkpointToDetails(c: Checkpoint) = c.details
  }

  case class Checkpoint(name: String, details: CheckpointDetails) {
    lazy val nameMarkdown = s"[$name](${details.url})"
  }

  case class RepoConfig(checkpointsByFolder: Map[String, Set[Checkpoint]]) {
    val folders: Set[String] = checkpointsByFolder.keySet

    val foldersByCheckpointName: Map[String, Seq[String]] = (for {
      (folder, checkpointNames) <- checkpointsByFolder.mapValues(_.map(_.name)).toSeq
      checkpointName <- checkpointNames
    } yield checkpointName -> folder).groupBy(_._1).mapValues(_.map(_._2))

    val checkpointsNamedInMultipleFolders: Map[String, Seq[String]] = foldersByCheckpointName.filter(_._2.size > 1)

    require(checkpointsNamedInMultipleFolders.isEmpty, s"Duplicate checkpoints defined in multiple config files: $checkpointsNamedInMultipleFolders")
  }
}
