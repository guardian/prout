package lib

import com.madgag.scalagithub.model.{Label, PullRequest}

case class PRSnapshot(pr: PullRequest, labels: Seq[Label])
