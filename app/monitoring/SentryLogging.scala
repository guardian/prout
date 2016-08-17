package monitoring

import ch.qos.logback.classic.filter.ThresholdFilter
import ch.qos.logback.classic.{Logger, LoggerContext}
import com.getsentry.raven.RavenFactory.ravenInstance
import com.getsentry.raven.dsn.Dsn
import com.getsentry.raven.logback.SentryAppender
import org.slf4j.Logger.ROOT_LOGGER_NAME
import org.slf4j.LoggerFactory
import play.api
import play.api.Play.configuration

object SentryLogging {
  import play.api.Play.current

  val dsnOpt = configuration.getString("sentry.dsn").map(new Dsn(_))

  def init() {
    dsnOpt match {
      case None =>
        api.Logger.warn("No Sentry logging configured (OK for dev)")
      case Some(dsn) =>
        api.Logger.info(s"Initialising Sentry logging for ${dsn.getHost}")
        val tags = Map("gitCommitId" -> app.BuildInfo.gitCommitId)
        val tagsString = tags.map { case (key, value) => s"$key:$value" }.mkString(",")

        val filter = new ThresholdFilter { setLevel("ERROR") }
        filter.start() // OMG WHY IS THIS NECESSARY LOGBACK?

        val sentryAppender = new SentryAppender(ravenInstance(dsn)) {
          addFilter(filter)
          setTags(tagsString)
          setContext(LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext])
        }
        sentryAppender.start()
        LoggerFactory.getLogger(ROOT_LOGGER_NAME).asInstanceOf[Logger].addAppender(sentryAppender)
    }
  }
}
