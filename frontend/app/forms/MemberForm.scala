package forms

import com.gu.membership.model._
import com.gu.membership.salesforce.{PaidTier, Tier}
import com.gu.membership.zuora.{Address, Countries, Country}
import model.FeatureChoice
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.{Form, FormError, Mapping}

object MemberForm {
  case class NameForm(first: String, last: String)

  case class PaymentForm(billingPeriod: BillingPeriod, token: String)

  case class MarketingChoicesForm(gnm: Option[Boolean], thirdParty: Option[Boolean])

  trait JoinForm {
    val name: NameForm
    val deliveryAddress: Address
    val marketingChoices: MarketingChoicesForm
    val password: Option[String]
    val plan: TierPlan
    val featureChoice: Set[FeatureChoice]
  }

  case class FriendJoinForm(name: NameForm, deliveryAddress: Address, marketingChoices: MarketingChoicesForm,
                            password: Option[String]) extends JoinForm {
    override val plan = FriendTierPlan.current
    override val featureChoice = Set.empty[FeatureChoice]
  }

  case class StaffJoinForm(name: NameForm, deliveryAddress: Address, marketingChoices: MarketingChoicesForm,
                            password: Option[String]) extends JoinForm {
    override val plan = StaffPlan
    override val featureChoice = Set.empty[FeatureChoice]
  }

  case class PaidMemberJoinForm(tier: PaidTier, name: NameForm, payment: PaymentForm, deliveryAddress: Address,
                                billingAddress: Option[Address], marketingChoices: MarketingChoicesForm,
                                password: Option[String], casId: Option[String], subscriberOffer: Boolean,
                                featureChoice: Set[FeatureChoice]) extends JoinForm {
    override val plan = PaidTierPlan(tier, payment.billingPeriod, Current)
  }

  case class AddressDetails(deliveryAddress: Address, billingAddress: Option[Address])

  trait MemberChangeForm {
    val featureChoice: Set[FeatureChoice]
    val addressDetails: Option[AddressDetails]
  }

  case class PaidMemberChangeForm(password: String, featureChoice: Set[FeatureChoice]) extends MemberChangeForm {
    val addressDetails = None
  }

  case class FreeMemberChangeForm(payment: PaymentForm,
                                  deliveryAddress: Address,
                                  billingAddress: Option[Address],
                                  featureChoice: Set[FeatureChoice]) extends MemberChangeForm {
    val addressDetails = Some(AddressDetails(deliveryAddress, billingAddress))
  }

  case class FeedbackForm(category: String, page: String, feedback: String, name: String, email: String)

  implicit val productFeaturesFormatter: Formatter[Set[FeatureChoice]] = new Formatter[Set[FeatureChoice]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Set[FeatureChoice]] = {
      val inputVal = data.getOrElse(key, "")
      Right(FeatureChoice.setFromString(inputVal))
    }

    override def unbind(key: String, choices: Set[FeatureChoice]): Map[String, String] =
      Map(key -> FeatureChoice.setToString(choices))
  }

  private val productFeature = of[Set[FeatureChoice]] as productFeaturesFormatter

  val countryText = nonEmptyText.verifying(Countries.allCodes.contains _)
    .transform[Country](Countries.allCodes.apply, _.alpha2)

  val nonPaidAddressMapping: Mapping[Address] = mapping(
    "lineOne" -> text,
    "lineTwo" -> text,
    "town" -> text,
    "countyOrState" -> text,
    "postCode" -> text(maxLength=20),
    "country" -> countryText
  )(Address.apply)(Address.unapply).verifying(_.valid)

  val paidAddressMapping: Mapping[Address] = mapping(
    "lineOne" -> nonEmptyText,
    "lineTwo" -> text,
    "town" -> nonEmptyText,
    "countyOrState" -> text,
    "postCode" -> text(maxLength=20),
    "country" -> countryText
  )(Address.apply)(Address.unapply).verifying(_.valid)

  val nameMapping: Mapping[NameForm] = mapping(
    "first" -> nonEmptyText,
    "last" -> nonEmptyText
  )(NameForm.apply)(NameForm.unapply)

  val marketingChoicesMapping: Mapping[MarketingChoicesForm] = mapping(
    "gnnMarketing" -> optional(boolean),
    "thirdParty" -> optional(boolean)
  )(MarketingChoicesForm.apply)(MarketingChoicesForm.unapply)

  val paymentMapping: Mapping[PaymentForm] = mapping(
    "type" -> nonEmptyText.transform[BillingPeriod](b =>
      if (Seq("annual","subscriberOfferAnnual").contains(b)) Year else Month, _.noun),
    "token" -> nonEmptyText
  )(PaymentForm.apply)(PaymentForm.unapply)

  val feedbackMapping: Mapping[FeedbackForm] =   mapping(
    "category" -> nonEmptyText,
    "page" -> text,
    "feedback" -> nonEmptyText,
    "name" -> nonEmptyText,
    "email" -> email
  )(FeedbackForm.apply)(FeedbackForm.unapply)

  val friendJoinForm: Form[FriendJoinForm] = Form(
    mapping(
      "name" -> nameMapping,
      "deliveryAddress" -> nonPaidAddressMapping,
      "marketingChoices" -> marketingChoicesMapping,
      "password" -> optional(nonEmptyText)
    )(FriendJoinForm.apply)(FriendJoinForm.unapply)
  )

  val staffJoinForm: Form[StaffJoinForm] = Form(
    mapping(
      "name" -> nameMapping,
      "deliveryAddress" -> nonPaidAddressMapping,
      "marketingChoices" -> marketingChoicesMapping,
      "password" -> optional(nonEmptyText)
    )(StaffJoinForm.apply)(StaffJoinForm.unapply)
  )

  val paidMemberJoinForm: Form[PaidMemberJoinForm] = Form(
    mapping(
      "tier" -> nonEmptyText.transform[PaidTier](PaidTier.slugMap, _.slug),
      "name" -> nameMapping,
      "payment" -> paymentMapping,
      "deliveryAddress" -> paidAddressMapping,
      "billingAddress" -> optional(paidAddressMapping),
      "marketingChoices" -> marketingChoicesMapping,
      "password" -> optional(nonEmptyText),
      "casId" -> optional(nonEmptyText),
      "subscriberOffer" -> default(boolean, false),
      "featureChoice" -> productFeature
    )(PaidMemberJoinForm.apply)(PaidMemberJoinForm.unapply)
  )

  val freeMemberChangeForm: Form[FreeMemberChangeForm] = Form(
    mapping(
      "payment" -> paymentMapping,
      "deliveryAddress" -> paidAddressMapping,
      "billingAddress" -> optional(paidAddressMapping),
      "featureChoice" -> productFeature
    )(FreeMemberChangeForm.apply)(FreeMemberChangeForm.unapply)
  )

  val paidMemberChangeForm: Form[PaidMemberChangeForm] = Form(
    mapping(
      "password" -> nonEmptyText,
      "featureChoice" -> productFeature

    )(PaidMemberChangeForm.apply)(PaidMemberChangeForm.unapply)
  )

  val feedbackForm: Form[FeedbackForm] = Form(
    mapping(
      "category" -> nonEmptyText,
      "page" -> text,
      "feedback" -> nonEmptyText,
      "name" -> nonEmptyText,
      "email" -> email
    )(FeedbackForm.apply)(FeedbackForm.unapply)
  )
}
