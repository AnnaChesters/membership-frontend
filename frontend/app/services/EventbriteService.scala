package services

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.Future

import play.api.Logger
import play.api.Play.current
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.libs.json.Reads
import play.api.libs.ws._

import configuration.Config
import model.EventPortfolio
import model.Eventbrite._
import model.EventbriteDeserializer._
import model.RichEvent._
import monitoring.EventbriteMetrics
import utils.ScheduledTask

trait EventbriteService extends utils.WebServiceHelper[EBObject, EBError] {
  val apiToken: String
  val maxDiscountQuantityAvailable: Int

  val wsUrl = Config.eventbriteApiUrl
  def wsPreExecute(req: WSRequestHolder): WSRequestHolder = req.withQueryString("token" -> apiToken)

  val refreshTimeAllEvents = new FiniteDuration(Config.eventbriteRefreshTime, SECONDS)
  lazy val eventsTask = ScheduledTask[Seq[RichEvent]]("Eventbrite events", Nil, 1.second, refreshTimeAllEvents) {
    for {
      events <- getPaginated[EBEvent]("users/me/owned_events?status=live")
      richEvents <- Future.sequence(events.map(mkRichEvent))
    } yield richEvents
  }

  val refreshTimeArchivedEvents = new FiniteDuration(Config.eventbriteRefreshTime, SECONDS)
  lazy val archivedEventsTask = ScheduledTask[Seq[RichEvent]]("Eventbrite archived events", Nil, 1.second, refreshTimeArchivedEvents) {
    for {
      eventsArchive <- get[EBResponse[EBEvent]]("users/me/owned_events?status=ended&order_by=start_desc")
      richEventsArchive <- Future.sequence(eventsArchive.data.map(mkRichEvent))
    } yield richEventsArchive
  }

  def start() {
    Logger.info("Starting EventbriteService background tasks")
    eventsTask.start()
    archivedEventsTask.start()
  }

  def events: Seq[RichEvent] = eventsTask.get()
  def eventsArchive: Seq[RichEvent] = archivedEventsTask.get()

  def mkRichEvent(event: EBEvent): Future[RichEvent]
  def getEventsOrdering: Seq[String]
  def getEventsTagged(tag: String): Seq[RichEvent]

  private def getPaginated[T](url: String)(implicit reads: Reads[EBResponse[T]]): Future[Seq[T]] = {
    val enumerator = Enumerator.unfoldM(Option(1)) {
      _.map { nextPage =>
        for {
          response <- get[EBResponse[T]](url, "page" -> nextPage.toString)
        } yield Some((response.pagination.nextPageOpt, response.data))
      }.getOrElse(Future.successful(None))
    }

    enumerator(Iteratee.consume()).flatMap(_.run)
  }

  def getEventPortfolio: EventPortfolio = {
    val priorityIds = getEventsOrdering
    val (priorityEvents, normal) = events.partition(e => priorityIds.contains(e.id))
    EventPortfolio(priorityEvents.sortBy(e => priorityIds.indexOf(e.id)), normal, Some(eventsArchive))
  }

  def getBookableEvent(id: String): Option[RichEvent] = events.find(_.id == id)
  def getEvent(id: String): Option[RichEvent] = (events ++ eventsArchive).find(_.id == id)

  def createOrGetAccessCode(event: RichEvent, code: String, ticketClasses: Seq[EBTicketClass]): Future[EBAccessCode] = {
    val uri = s"events/${event.id}/access_codes"

    for {
      discounts <- getPaginated[EBAccessCode](uri)
      discount <- discounts.find(_.code == code).fold {
        post[EBAccessCode](uri, Map(
          "access_code.code" -> Seq(code),
          "access_code.quantity_available" -> Seq(maxDiscountQuantityAvailable.toString),
          "access_code.ticket_ids" -> Seq(ticketClasses.head.id) // TODO: support multiple ticket classes when Eventbrite fix their API
        ))
      }(Future.successful)
    } yield discount
  }

  def createOrGetDiscount(event: RichEvent, code: String): Future[EBDiscount] = {
    val uri = s"events/${event.id}/discounts"

    for {
      discounts <- getPaginated[EBDiscount](uri)
      discount <- discounts.find(_.code == code).map(Future.successful).getOrElse {
        post[EBDiscount](uri, Map(
          "discount.code" -> Seq(code),
          "discount.percent_off" -> Seq("20"),
          "discount.quantity_available" -> Seq(maxDiscountQuantityAvailable.toString)
        ))
      }
    } yield discount
  }

  def getOrder(id: String): Future[EBOrder] = get[EBOrder](s"orders/$id")
}

object GuardianLiveEventService extends EventbriteService {
  val apiToken = Config.eventbriteApiToken
  val maxDiscountQuantityAvailable = 2

  val wsMetrics = new EventbriteMetrics("Guardian Live")

  val gridService = GridService(Config.gridConfig.url)

  val refreshTimePriorityEvents = new FiniteDuration(Config.eventbriteRefreshTimeForPriorityEvents, SECONDS)
  lazy val eventsOrderingTask = ScheduledTask[Seq[String]]("Event ordering", Nil, 1.second, refreshTimePriorityEvents) {
    for {
      ordering <- WS.url(Config.eventOrderingJsonUrl).get()
    } yield (ordering.json \ "order").as[Seq[String]]
  }

  def mkRichEvent(event: EBEvent): Future[RichEvent] = {
    event.mainImageUrl.fold(Future.successful(GuLiveEvent(event, None))) { url =>
      gridService.getRequestedCrop(url).map(GuLiveEvent(event, _))
    }
  }

  override def getEventsOrdering: Seq[String] = eventsOrderingTask.get()
  override def getEventsTagged(tag: String): Seq[RichEvent] = events.filter(_.name.text.toLowerCase.contains(tag))

  override def start() {
    super.start()
    eventsOrderingTask.start()
  }
}

case class MasterclassEventServiceError(s: String) extends Throwable {
  override def getMessage: String = s
}

object MasterclassEventService extends EventbriteService {
  import MasterclassEventServiceHelpers._

  val apiToken = Config.eventbriteMasterclassesApiToken
  val maxDiscountQuantityAvailable = 1

  val wsMetrics = new EventbriteMetrics("Masterclasses")

  val masterclassDataService = MasterclassDataService

  override def events: Seq[RichEvent] = availableEvents(super.events)

  def mkRichEvent(event: EBEvent): Future[RichEvent] = {
    val masterclassData = masterclassDataService.getData(event.id)
    Future.successful(MasterclassEvent(event, masterclassData))
  }

  override def getEventsOrdering: Seq[String] = Nil
  override def getEventsTagged(tag: String): Seq[RichEvent] = events.filter(_.tags.contains(tag.toLowerCase))

  // This should never happen as we only display masterclasses with access codes enabled
  override def createOrGetDiscount(event: RichEvent, code: String): Future[EBDiscount] =
    Future.failed(MasterclassEventServiceError(s"Masterclasses aren't allowed discount codes, attempted on event ${event.id}"))
}

object MasterclassEventServiceHelpers {
  def availableEvents(events: Seq[RichEvent]): Seq[RichEvent] =
    events.filter(_.memberTickets.exists { t => t.quantity_sold < t.quantity_total } )
}

object EventbriteService {
  val services = Seq(GuardianLiveEventService, MasterclassEventService)

  implicit class RichEventProvider(event: RichEvent) {
    val service = event match {
      case _: GuLiveEvent => GuardianLiveEventService
      case _: MasterclassEvent => MasterclassEventService
    }
  }

  def searchServices(fn: EventbriteService => Option[RichEvent]): Option[RichEvent] =
    services.flatMap { service => fn(service) }.headOption

  def getBookableEvent(id: String) = searchServices(_.getBookableEvent(id))
  def getEvent(id: String) = searchServices(_.getEvent(id))
}
