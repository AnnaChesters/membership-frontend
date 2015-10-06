package model

import com.gu.membership.salesforce.Tier
import com.gu.membership.salesforce.Tier.{Partner, Patron}
import configuration.Config.zuoraFreeEventTicketsAllowance

case class Benefits(tier: Tier, pricing: Option[Pricing]) {

  import Benefits._
  import Tier._

  val list = tier match {
    case Friend => Benefits.friendBenefitsList
    case Supporter => Benefits.supporterBenefitsList
    case Partner => Benefits.partnerBenefitsList
    case Patron => Benefits.patronBenefitsList
    case _ => Seq.empty
  }
  val cta = s"Become a ${tier.name.toLowerCase}"

  val leadin = tier match {
    case Supporter => "Supporter benefits, plus…"
    case Partner => "Partner benefits, plus…"
    case _ => "Benefits"
  }

  val highlights = tier match {
    case Friend => List(Highlight(
        """Receive regular updates from the membership community,
        | book tickets to Guardian Live events and access the Guardian members area""".stripMargin))

    case Supporter => List(Highlight("Support our journalism and keep theguardian.com free of charge"),
                           Highlight("Get access to tickets and to the live broadcast of events"))

    case Partner => List(Highlight("Get priority booking and 20% discount on Guardian Live, Guardian Local and most Guardian Masterclasses"),
                         Highlight(s"Includes $zuoraFreeEventTicketsAllowance tickets to Guardian Live events (or 4 Guardian-published books) per year", isNew = true))

    case Patron => List(Highlight("Show deep support for keeping the Guardian free, open and independent."),
                        Highlight("Get invited to a small number of exclusive, behind-the-scenes functions"))
    case _ => Nil
  }

  val detailsLimited = tier match {
    case Tier.Friend => friendBenefitsList
    case Tier.Supporter => supporterBenefitsList
    case Tier.Partner => uniqueBenefits(partnerBenefitsList, supporterBenefitsList)
    case Tier.Patron => uniqueBenefits(patronBenefitsList, partnerBenefitsList)
    case Tier.Staff => uniqueBenefits(partnerBenefitsList, supporterBenefitsList)
                         .filterNot(_.identifier == "books_or_tickets")
  }
}

object Benefits {
  val DiscountTicketTiers = Set[Tier](Partner, Patron)
  case class Item(identifier: String, title: String, isNew: Boolean = false)
  case class Highlight(description: String, isNew: Boolean = false)

  val allBenefits = Seq(
    Item("welcome_pack", "Welcome pack, card and gift"),
    Item("access_tickets", "Access to tickets"),
    Item("live_stream", "Access to live stream"),
    Item("email_updates", "Regular events email"),
    Item("offers_competitions", "Offers and competitions"),
    Item("priority_booking", "48hrs priority booking"),
    Item("books_or_tickets", s"$zuoraFreeEventTicketsAllowance tickets or 4 books", isNew = true),
    Item("books_and_tickets", s"$zuoraFreeEventTicketsAllowance tickets and 4 books", isNew = true),
    Item("discount", "20% discount for you and a guest"),
    Item("unique_experiences", "Exclusive behind-the-scenes functions")
  )

  private def benefitsFilter(identifiers: String*) = identifiers.flatMap { id =>
    allBenefits.find(_.identifier == id)
  }

  private def uniqueBenefits(benefits: Seq[Item], exclude: Seq[Item]) = {
    benefits.filterNot(exclude.toSet)
  }

  val friendBenefitsList = benefitsFilter(
    "access_tickets",
    "offers_competitions",
    "email_updates"
  )
  val supporterBenefitsList = benefitsFilter(
    "welcome_pack",
    "live_stream",
    "access_tickets",
    "offers_competitions",
    "email_updates"
  )
  val partnerBenefitsList = benefitsFilter(
    "books_or_tickets",
    "priority_booking",
    "discount",
    "welcome_pack",
    "live_stream",
    "offers_competitions",
    "email_updates"
  )
  val patronBenefitsList = benefitsFilter(
    "books_and_tickets",
    "unique_experiences",
    "priority_booking",
    "discount",
    "welcome_pack",
    "live_stream",
    "offers_competitions",
    "email_updates"
  )

  val comparisonBasicList = benefitsFilter(
    "welcome_pack",
    "live_stream",
    "access_tickets",
    "offers_competitions",
    "email_updates"
  )

  val comparisonHiglightsList = benefitsFilter(
    "books_or_tickets",
    "priority_booking",
    "discount",
    "unique_experiences"
  )
}
