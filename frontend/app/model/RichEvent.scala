package model

import configuration.Config
import model.Eventbrite.EBEvent
import services.MasterclassData

object RichEvent {
  case class Metadata(
    identifier: String,
    title: String,
    shortTitle: String,
    eventListUrl: String,
    termsUrl: String,
    largeImg: Boolean,
    highlightsUrlOpt: Option[String],
    chooseTier: ChooseTierMetadata
  )

  case class ChooseTierMetadata(title: String, sectionTitle: String)

  val guLiveMetadata = Metadata(
    identifier="guardian-live",
    title="Guardian Live events",
    shortTitle="Events",
    eventListUrl=controllers.routes.Event.list.url,
    termsUrl=Config.guardianLiveEventsTermsUrl,
    largeImg=true,
    highlightsUrlOpt=Some(Config.guardianMembershipUrl + "#video"),
    chooseTier=ChooseTierMetadata(
      "Guardian Live events are exclusively for Guardian members",
      "Choose a membership tier to continue with your booking"
    )
  )

  val masterclassMetadata = Metadata(
    identifier="masterclasses",
    title="Guardian Masterclasses",
    shortTitle="Masterclasses",
    eventListUrl=controllers.routes.Event.masterclasses.url,
    termsUrl=Config.guardianMasterclassesTermsUrl,
    largeImg=false,
    highlightsUrlOpt=None,
    chooseTier=ChooseTierMetadata(
      "Choose a membership tier to continue with your booking",
      "Become a Partner or Patron to save 20% on your masterclass"
    )
  )

  case class EventImage(assets: List[Grid.Asset], metadata: Grid.Metadata)

  trait RichEvent {
    val event: EBEvent
    val imageUrl: String
    val tags: Seq[String]

    val metadata: Metadata

    val imageMetadata: Option[Grid.Metadata]

    val availableWidths: String
    val fallbackImage = views.support.Asset.at("images/event-placeholder.gif")
  }

  case class GuLiveEvent(event: EBEvent, image: Option[EventImage]) extends RichEvent {
    val imageUrl = image.filter(_.assets.nonEmpty).map(_.assets.maxBy(_.dimensions.width)).fold(fallbackImage) { asset =>
      asset.secureUrl.getOrElse(asset.file)
    }

    val imagerUrl = "\\d+.jpg".r.replaceFirstIn(imageUrl, "{width}.jpg")

    private val widths = image.fold(List.empty[Int])(_.assets.map(_.dimensions.width))

    val availableWidths = widths.mkString(",")

    val imageMetadata = image.map(_.metadata)

    val tags = Nil

    val metadata = guLiveMetadata
  }

  case class MasterclassEvent(event: EBEvent, data: Option[MasterclassData]) extends RichEvent {
    val imageUrl = data.flatMap(_.images.headOption).flatMap(_.file)
      .getOrElse(fallbackImage)
      .replace("http://static", "https://static-secure")

    val availableWidths = ""

    val imageMetadata = None
    val tags = event.description.map(_.html).flatMap(MasterclassEvent.extractTags).getOrElse(Nil)

    val metadata = masterclassMetadata
  }


  object MasterclassEvent {
    case class tagItem(categoryName: String, subCategories: Seq[String] = Seq())

    val tags = Seq(
      tagItem("Writing", Seq("Copywriting", "Creative writing", "Research", "Fiction", "Non-fiction")),
      tagItem("Publishing"),
      tagItem("Journalism"),
      tagItem("Business"),
      tagItem("Digital"),
      tagItem("Culture"),
      tagItem("Food and drink"),
      tagItem("Media")
    )

    // if a tag is hyphenated (Non-fiction) then weirdness/duplication happens here
    // so we replace it with an underscore in URLs (ugly, but limited alternatives)
    def encodeTag(tag: String) = tag.toLowerCase.replace("-", "_").replace(" ", "-")
    def decodeTag(tag: String) = tag.capitalize.replace("-", " ").replace("_", "-")

    def extractTags(s: String): Option[Seq[String]] =
      "<!--\\s*tags:(.*?)-->".r.findFirstMatchIn(s).map(_.group(1).split(",").toSeq.map(_.trim.toLowerCase))
  }

  implicit def eventToEBEvent(event: RichEvent): EBEvent = event.event

}

