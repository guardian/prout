package lib

import com.madgag.github.Implicits._
import com.madgag.github.apps.GitHubAppAuth
import com.madgag.playgithub.testkit.TestRepoCreation
import com.madgag.scalagithub.GitHubCredentials
import com.madgag.scalagithub.commands.{CreatePullRequest, MergePullRequest}
import com.madgag.scalagithub.model._
import lib.sentry.SentryApiClient
import org.apache.pekko.actor.ActorSystem
import org.eclipse.jgit.lib.{AbbreviatedObjectId, ObjectId}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{Inside, Inspectors}
import org.scalatestplus.play._
import org.scalatestplus.play.components.OneAppPerSuiteWithComponents
import play.api.routing.Router
import play.api.{BuiltInComponents, BuiltInComponentsFromContext, Logger, NoHttpFiltersComponents}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

case class PRText(title: String, desc: String)

trait Helpers extends PlaySpec with OneAppPerSuiteWithComponents with Inspectors with ScalaFutures with Eventually with Inside with TestRepoCreation {

  val logger = Logger(getClass)
  override def components: BuiltInComponents = new BuiltInComponentsFromContext(context) with NoHttpFiltersComponents {

    override lazy val router: Router = Router.empty
  }

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(20, Seconds)), interval = scaled(Span(2, Seconds)))

  override implicit val actorSystem: ActorSystem = components.actorSystem

  private val gitHubAppAuth: GitHubAppAuth = GitHubAppAuth.fromConfigMap(sys.env, "PROUT")

  val testFixturesInstallationAccess: com.madgag.github.apps.InstallationAccess =
    gitHubAppAuth.accessSoleInstallation().futureValue

  def labelsOnPR()(implicit repoPR: RepoPR): Set[String] = labelsOn(repoPR.pr)

  def labelsOn(pr: PullRequest): Set[String] = pr.labels.list().all().futureValue.map(_.name).toSet

  def lastCommentOn(pr: PullRequest): String =
    pr
      .comments2
      .list()
      .all()
      .futureValue
      .lastOption
      .map(_.body)
      .mkString

  case class RepoPR(pr: PullRequest) {
    val githubRepo = pr.baseRepo

    def currentPR(): PullRequest = {
      def fetchPR(): PullRequest = githubRepo.pullRequests.get(pr.number).futureValue

      eventually {
        val pr = fetchPR()
        Thread.sleep(1000)
        fetchPR() mustEqual pr
        pr
      }
    }

    def listComments(): Seq[Comment] = pr.comments2.list().all().futureValue

    var checkpointCommitFuture: Future[Iterator[AbbreviatedObjectId]] = Future.successful(Iterator.empty)

    def setCheckpointTo(commitId: AbbreviatedObjectId): Unit = {
      checkpointCommitFuture = Future.successful(Iterator(commitId))
    }

    def setCheckpointTo(objectId: ObjectId): Unit = {
      setCheckpointTo(AbbreviatedObjectId.fromObjectId(objectId))
    }

    def setCheckpointTo(branchName: String): Unit = {
      val objectId = githubRepo.refs.get(s"heads/$branchName").futureValue.objectId
      setCheckpointTo(objectId)
      logger.info(s"Set checkpoint to '$branchName' (${objectId.name.take(8)})")
    }

    def setCheckpointToMatchDefaultBranch = setCheckpointTo(githubRepo.default_branch)

    def setCheckpointFailureTo(exception: Exception): Unit = {
      checkpointCommitFuture = Future.failed(exception)
    }

    implicit val checkpointSnapshoter: CheckpointSnapshoter = _ => checkpointCommitFuture
    implicit val sentryApiClient: Option[SentryApiClient] = None

    val delayer = new Delayer(app.actorSystem)

    val bot: Bot = Await.result(Bot.forGithubApp(gitHubAppAuth), 3.seconds)

    val repoSnapshotFactory: RepoSnapshot.Factory = new RepoSnapshot.Factory(bot)

    val droid: Droid = new Droid(
      repoSnapshotFactory,
      new RepoUpdater(),
      new PRUpdater(delayer)
    )

    val scheduler = new ScanScheduler(githubRepo.repoId, droid, actorSystem = app.actorSystem, delayer)

    override val toString: String = pr.html_url
  }

  def scan[T](shouldAddComment: Boolean)(issueFun: PullRequest => T)(implicit repoPR: RepoPR): Unit = {
    val commentsBeforeScan = repoPR.listComments()
    whenReady(repoPR.scheduler.scan()) { s =>
      eventually {
        inside(repoPR) { case _ =>
          val commentsAfterScan = repoPR.listComments()
          commentsAfterScan must have size (commentsBeforeScan.size+(if (shouldAddComment) 1 else 0))
          issueFun(repoPR.currentPR())
        }
      }
    }
  }

  def waitUntil[T](shouldAddComment: Boolean)(issueFun: PullRequest => T)(implicit repoPR: RepoPR): Unit = {
    val commentsBeforeScan = repoPR.listComments()
    eventually {
      val commentsAfterScan = repoPR.listComments()
      commentsAfterScan must have size (commentsBeforeScan.size + (if (shouldAddComment) 1 else 0))
      issueFun(repoPR.currentPR())
    }
  }

  def scanShouldNotChangeAnything()(implicit meat: RepoPR): Unit = {
    scanShouldNotChange { pr => (pr.labels.list().all().futureValue, pr.comments) }
  }

  def scanShouldNotChange[S](issueState: PullRequest => S)(implicit repoPR: RepoPR): Unit = {
    val beforeState = issueState(repoPR.currentPR())

    for (_ <- 1 to 3) { // Scan repo several times...
      whenReady(repoPR.scheduler.scan()) { _ =>
        issueState(repoPR.currentPR()) mustEqual beforeState // ... nothing should change
      }
    }
  }

  def mergePullRequestIn(
    repo: Repo,
    merging: String,
    prText: PRText = PRText("title", "desc"),
    userLabels: Set[String] = Set.empty
  ) = {
    eventually {
      whenReady(repo.refs.get(s"heads/$merging")) { _.ref must endWith(merging) }
    }

    val createPullRequest = CreatePullRequest(
      title = prText.title,
      head = merging,
      base = repo.default_branch
    )

    val pr = repo.pullRequests.create(createPullRequest).futureValue

    if (userLabels.nonEmpty) {
      // GitHub API doesn't seem to let us set PR labels on creation
      whenReady(pr.labels.replace(userLabels.toSeq)) { _ =>
        eventually { labelsOn(pr) must equal(userLabels) }
      }
    }

    eventually {
      whenReady(repo.pullRequests.get(pr.number)) {
        _.mergeable.value mustBe true
      }
    }

    eventually {
      whenReady(pr.merge(MergePullRequest())) { _.merged must be(true) }
    }

    val mergedPR = eventually {
      whenReady(repo.pullRequests.get(pr.number)) {
        pr => pr.merged_by mustBe defined ;
          logger.info("Created and merged PR: "+pr.html_url)
          pr }
    }

    RepoPR(mergedPR)
  }

  def shaForDefaultBranchOf(repo: Repo): ObjectId = {
    repo.refs.get("heads/" + repo.default_branch).futureValue.objectId
  }
}
