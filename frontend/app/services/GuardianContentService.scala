package services

import com.gu.contentapi.client.{GuardianContentApiError, GuardianContentClient}
import com.gu.contentapi.client.model._
import configuration.Config
import monitoring.ContentApiMetrics
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.iteratee.{Iteratee, Enumerator}
import utils.ScheduledTask
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Failure
import scala.concurrent.duration._

case class ContentAPIPagination(currentPage: Int, pages: Int) {
  lazy val nextPageOpt = Some(currentPage + 1).filter(_ <= pages)
}

trait GuardianContentService extends GuardianContent {

  private def eventbrite: Future[Seq[Content]] = {
    val enumerator = Enumerator.unfoldM(Option(1)) {
      _.map { nextPage =>
        for {
          response <- eventbriteQuery(nextPage)
        } yield {
          val pagination = ContentAPIPagination(response.currentPage, response.pages)
          Some(pagination.nextPageOpt, response.results)

        }
      }.getOrElse(Future.successful(None))
    }

    enumerator(Iteratee.consume()).flatMap(_.run)
  }

  private def masterclasses: Future[Seq[MasterclassData]] = {
    val enumerator = Enumerator.unfoldM(Option(1)) {
      _.map { nextPage =>
        for {
          response <- masterclassesQuery(nextPage)
        } yield {
          val masterclassData = response.results.flatMap(MasterclassDataExtractor.extractEventbriteInformation)
          val pagination = ContentAPIPagination(response.currentPage.getOrElse(0), response.pages.getOrElse(0))
          Some(pagination.nextPageOpt, masterclassData)

        }
      }.getOrElse(Future.successful(None))
    }

    enumerator(Iteratee.consume()).flatMap(_.run)
  }

  private def offersAndCompetitions: Future[Seq[Content]] = offersAndCompetitionsContentQuery(1).map(_.results)

  private def membershipFront: Future[Seq[Content]] = membershipFrontContentQuery(1).map(_.results)

  def masterclassContent(eventId: String): Option[MasterclassData] = masterclassContentTask.get().find(mc => mc.eventId.equals(eventId))

  def content(eventId: String): Option[Content] = contentTask.get().find(c => c.references.map(_.id).contains(s"eventbrite/$eventId"))

  def offersAndCompetitionsContent: Seq[Content] = offersAndCompetitionsContentTask.get()

  def membershipFrontContent: Seq[Content] = membershipFrontContentTask.get()

  val masterclassContentTask = ScheduledTask[Seq[MasterclassData]]("GuardianContentService - Masterclass content", Nil, 2.seconds, 2.minutes)(masterclasses)

  val contentTask = ScheduledTask[Seq[Content]]("GuardianContentService - Content with Eventbrite reference", Nil, 1.millis, 2.minutes)(eventbrite)

  val offersAndCompetitionsContentTask = ScheduledTask[Seq[Content]]("GuardianContentService - Content with Guardian Members Only", Nil, 1.second, 2.minutes)(offersAndCompetitions)

  val membershipFrontContentTask = ScheduledTask[Seq[Content]]("GuardianContentService - Content from Membership front", Nil, 1.second, 2.minutes)(membershipFront)

  def start() {
    masterclassContentTask.start()
    offersAndCompetitionsContentTask.start()
    contentTask.start()
    membershipFrontContentTask.start()
  }

}

object GuardianContentService extends GuardianContentService

trait GuardianContent {

  val client = new GuardianContentClient(Config.contentApiKey)

  def masterclassesQuery(page: Int): Future[ItemResponse] = {
    val date = new DateTime(2014, 1, 1, 0, 0)
    val itemQuery = ItemQuery("guardian-masterclasses")
      .fromDate(date)
      .pageSize(100)
      .page(page)
      .showReferences("eventbrite")
      .showFields("body")
      .showElements("image")
    client.getResponse(itemQuery).andThen {
      case Failure(GuardianContentApiError(status, message)) =>
        logAndRecordError(status)
    }
  }

  def eventbriteQuery(page: Int): Future[SearchResponse] = {
    val searchQuery = SearchQuery()
      .referenceType("eventbrite")
      .showReferences("eventbrite")
      .showElements("image")
      .pageSize(100)
      .page(page)
    client.getResponse(searchQuery).andThen {
      case Failure(GuardianContentApiError(status, message)) =>
        logAndRecordError(status)
    }
  }

  def offersAndCompetitionsContentQuery(page: Int): Future[ItemResponse] = {
    val itemQuery = ItemQuery("membership")
      .showElements("image")
      .showTags("keyword")
      .pageSize(100)
      .page(page)
      .tag("membership/membership-offers|membership/membership-competitions")

    client.getResponse(itemQuery).andThen {
      case Failure(GuardianContentApiError(status, message)) =>
        logAndRecordError(status)
    }
  }

  def membershipFrontContentQuery(page: Int): Future[ItemResponse] = {
    val itemQuery = ItemQuery("/membership")
      .showElements("image")
      .tag("type/article")
      .pageSize(20)
      .page(page)

    client.getResponse(itemQuery).andThen {
      case Failure(GuardianContentApiError(status, message)) =>
        logAndRecordError(status)
    }
  }

  def contentItemQuery(path: String): Future[ItemResponse] = {
    val itemQuery = ItemQuery(path)
      .showFields("trailText")
      .showElements("all")
      .showTags("all")
    client.getResponse(itemQuery).andThen {
      case Failure(GuardianContentApiError(status, message)) =>
        logAndRecordError(status)
    }
  }

  def logAndRecordError(status: Int) {
    ContentApiMetrics.putResponseCode(status, "GET content")
    Logger.error(s"Error response from Content API $status")
  }
}
