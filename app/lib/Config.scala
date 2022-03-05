package lib

import java.time.Instant

import com.madgag.git._
import com.madgag.scalagithub.model.PullRequest
import com.madgag.time.Implicits._
import com.netaporter.uri.Uri
import lib.Config.{Checkpoint, CheckpointDetails, Sentry}
import lib.labels.{Overdue, PullRequestCheckpointStatus, Seen}
import org.eclipse.jgit.lib.ObjectId
import org.joda
import org.joda.time.Period
import play.api.data.validation.ValidationError
import play.api.libs.json.{Json, _}
import play.api.libs.functional.syntax._

case class ConfigFile(checkpoints: Map[String, CheckpointDetails],
  sentry: Option[Sentry] = None) {

  lazy val checkpointsByName: Map[String, Checkpoint] = checkpoints.map {
    case (name, details) => name -> Checkpoint(name, details)
  }

  lazy val checkpointSet = checkpointsByName.values.toSet
}

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

  case class Sentry(projects: Seq[String])

  case class CheckpointMessages(filePaths: Map[PullRequestCheckpointStatus, String]) {
    def filePathforStatus(status: PullRequestCheckpointStatus): Option[String] = filePaths.get(status)
  }

  object Sentry {
    implicit val readsSentry = Json.reads[Sentry]
  }

  object CheckpointMessages {

    def apply(messages: (PullRequestCheckpointStatus, String)*): CheckpointMessages = CheckpointMessages(messages.toMap)

    implicit val readsMessages: Reads[CheckpointMessages] = (
      (JsPath \ "seen").readNullable[String].map(_.map(Seen -> _)) and
        (JsPath \ "overdue").readNullable[String].map(_.map(Overdue -> _))
      ) { (seen, overdue) =>
        val messages = Map.empty[PullRequestCheckpointStatus, String] ++ seen.toMap ++ overdue.toMap
        CheckpointMessages(messages)
    }

    val defaults: Map[PullRequestCheckpointStatus, String] = Map(
        Seen -> "Please check your changes!",
        Overdue -> "What's gone wrong?"
    )
  }

  implicit val readsCheckpointDetails = Json.reads[CheckpointDetails]

  implicit val readsConfig = Json.reads[ConfigFile]

  def readConfigFrom(configFileObjectId: ObjectId)(implicit repoThreadLocal: ThreadLocalObjectDatabaseResources): JsResult[ConfigFile] = {
    implicit val reader = repoThreadLocal.reader()
    val fileJson = Json.parse(configFileObjectId.open.getCachedBytes(4096))
    Json.fromJson[ConfigFile](fileJson)
  }

  case class CheckpointDetails(
    url: Uri,
    overdue: joda.time.Period,
    disableSSLVerification: Option[Boolean] = None,
    messages: Option[CheckpointMessages] = None
  ) {
    val sslVerification = !disableSSLVerification.contains(true)

    def overdueInstantFor(pr: PullRequest): Option[Instant] = pr.merged_at.map(_.plus(overdue).toInstant)
  }

  object Checkpoint {
    implicit def checkpointToDetails(c: Checkpoint) = c.details
  }

  case class Checkpoint(name: String, details: CheckpointDetails) {
    lazy val nameMarkdown = s"[$name](${details.url})"
  }

  case class RepoConfig(
   configByFolder: Map[String, JsResult[ConfigFile]]
  ) {
    val validConfigByFolder: Map[String, ConfigFile] = configByFolder.collect {
      case (folder, JsSuccess(config, _)) => folder -> config
    }
    
    val foldersWithValidConfig: Set[String] = validConfigByFolder.keySet

    val foldersByCheckpointName: Map[String, Seq[String]] = (for {
      (folder, checkpointNames) <- validConfigByFolder.mapValues(_.checkpoints.keySet).toSeq
      checkpointName <- checkpointNames
    } yield checkpointName -> folder).groupBy(_._1).mapValues(_.map(_._2))

    val checkpointsNamedInMultipleFolders: Map[String, Seq[String]] = foldersByCheckpointName.filter(_._2.size > 1)

    require(checkpointsNamedInMultipleFolders.isEmpty, s"Duplicate checkpoints defined in multiple config files: ${checkpointsNamedInMultipleFolders.mapValues(_.mkString("(",", ",")"))}")

    val checkpointsByName: Map[String, Checkpoint] = validConfigByFolder.values.map(_.checkpointsByName).fold(Map.empty)(_ ++ _)
  }
}
