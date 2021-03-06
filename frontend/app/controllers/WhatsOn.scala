package controllers

import com.github.nscala_time.time.Imports._
import com.gu.membership.model.{Currency, GBP}
import configuration.CopyConfig
import model.RichEvent.MasterclassEvent._
import model.RichEvent._
import model._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.Controller
import services._
import tracking.ActivityTracking

trait WhatsOn extends Controller with ActivityTracking {

  val guLiveEvents: EventbriteService
  val localEvents: EventbriteService
  val masterclassEvents: EventbriteService

  implicit val currency: Currency = GBP

  private def allEvents = {
    guLiveEvents.events ++ localEvents.events
  }

  private def allEventsByLocation(location: String) = {
    guLiveEvents.getEventsByLocation(location) ++ localEvents.getEventsByLocation(location)
  }

  private def allEventsInArchive = {
    guLiveEvents.getEventsArchive.toList.flatten ++ localEvents.getEventsArchive.toList.flatten
  }

  private def locationFilterItems = {
    getCitiesWithCount(allEvents).map(FilterItem.tupled)
  }

  def list = CachedAction.async { implicit request =>
    val pageInfo = PageInfo(
      CopyConfig.copyTitleEvents,
      request.path,
      Some(CopyConfig.copyDescriptionEvents)
    )

    val locationOpt = request.getQueryString("location").filter(_.trim.nonEmpty)
    val featuredEvents = EventGroup("Featured", guLiveEvents.getFeaturedEvents)
    val events = EventGroup("What's on", chronologicalSort(locationOpt.fold(allEvents)(allEventsByLocation)))

    TouchpointBackend.Normal.catalog.map(cat =>
      Ok(views.html.event.eventsList(
        cat,
        pageInfo,
        events,
        featuredEvents,
        locationFilterItems,
        locationOpt
      ))
    )
  }

  def calendar = CachedAction { implicit request =>
    val locationOpt = request.getQueryString("location").filter(_.trim.nonEmpty)

    val calendarEvents =
      CalendarMonthDayGroup("Calendar", groupEventsByDayAndMonth(locationOpt.fold(allEvents)(allEventsByLocation)))

    Ok(views.html.event.calendar(
      PageInfo(s"${calendarEvents.title} | Events", request.path, None),
      calendarEvents,
      locationFilterItems,
      locationOpt
    ))
  }

  def listArchive = CachedAction { implicit request =>
    val calendarArchive =
      CalendarMonthDayGroup("Archive", groupEventsByDayAndMonth(allEventsInArchive)(implicitly[Ordering[LocalDate]].reverse))

    Ok(views.html.event.eventsListArchive(
      PageInfo(s"${calendarArchive.title} | Events", request.path, None),
      calendarArchive
    ))
  }

  def masterclassesList = CachedAction.async { implicit request =>
    val pageInfo = PageInfo(
      CopyConfig.copyTitleMasterclasses,
      request.path,
      Some(CopyConfig.copyDescriptionMasterclasses)
    )
    val eventGroup = EventGroup("Masterclasses", masterclassEvents.events)

    TouchpointBackend.Normal.catalog.map(cat =>
      Ok(views.html.event.masterclassesList(cat, pageInfo, eventGroup)))
  }

  def masterclassesListFilteredBy(rawTag: String, rawSubTag: String = "") = CachedAction.async { implicit request =>
    val pageInfo = PageInfo(
      CopyConfig.copyTitleMasterclasses,
      request.path,
      Some(CopyConfig.copyDescriptionMasterclasses)
    )
    val tag = decodeTag( if(rawSubTag.nonEmpty) rawSubTag else rawTag )
    val eventGroup = EventGroup("Masterclasses", masterclassEvents.getTaggedEvents(tag))

    TouchpointBackend.Normal.catalog.map { cat =>
      Ok(views.html.event.masterclassesList(cat, pageInfo, eventGroup, decodeTag(rawTag), decodeTag(rawSubTag)))
    }
  }
}

object WhatsOn extends WhatsOn {
  val guLiveEvents = GuardianLiveEventService
  val localEvents = LocalEventService
  val masterclassEvents = MasterclassEventService
}
