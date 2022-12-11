package lib

import java.time.Instant.now
import com.madgag.scalagithub.model.PullRequest
import com.madgag.time.Implicits._
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.format.{PeriodFormat, PeriodFormatter}

object Responsibility extends LazyLogging {

  val pf: PeriodFormatter = PeriodFormat.getDefault

  def responsibilityAndRecencyFor(pr: PullRequest): String = {
    val mergeToNow = java.time.Duration.between(pr.merged_at.get.toInstant, now)
    val timeSinceMerge = mergeToNow.toPeriod().withMillis(0).toString(pf)

    logger.info(s"mergedByOpt=${pr.merged_by} merged_at=${pr.merged_at}")
    s"${createdByAndMergedByFor(pr)} $timeSinceMerge ago"
  }

  def createdByAndMergedByFor(pr: PullRequest): String = {
    val mergedByOpt = pr.merged_by
    val mergedByText = s"merged by ${mergedByOpt.get.atLogin}"

    if (pr.user.id == mergedByOpt.get.id) mergedByText else s"created by ${pr.user.atLogin} and $mergedByText"
  }
}
