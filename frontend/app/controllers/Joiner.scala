package controllers

import actions.Functions._
import actions._
import com.github.nscala_time.time.Imports._
import com.gu.i18n.{CountryGroup, GBP}
import com.gu.identity.play.{IdMinimalUser, PrivateFields, StatusFields}
import com.gu.membership.salesforce.Tier.Friend
import com.gu.membership.salesforce._
import com.gu.membership.stripe.Stripe
import com.gu.membership.stripe.Stripe.Serializer._
import com.gu.membership.zuora.soap.models.errors.ResultError
import com.netaporter.uri.dsl._
import com.typesafe.scalalogging.LazyLogging
import configuration.{Config, CopyConfig}
import forms.MemberForm._
import model._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc._
import services.{GuardianContentService, _}
import services.EventbriteService._
import tracking.{ActivityTracking, EventActivity, EventData, MemberData}
import utils.CampaignCode.extractCampaignCode
import views.support
import views.support.PageInfo
import views.support.PageInfo.FormI18n

import scala.concurrent.Future

trait Joiner extends Controller with ActivityTracking with LazyLogging {
  val JoinReferrer = "join-referrer"

  val contentApiService = GuardianContentService

  val memberService: MemberService

  val subscriberOfferDelayPeriod = 6.months

  val casService = CASService

  val EmailMatchingGuardianAuthenticatedStaffNonMemberAction = AuthenticatedStaffNonMemberAction andThen matchingGuardianEmail()

  def tierChooser = NoCacheAction.async { implicit request =>
    val eventOpt = PreMembershipJoiningEventFromSessionExtractor.eventIdFrom(request).flatMap(EventbriteService.getBookableEvent)
    val accessOpt = request.getQueryString("membershipAccess").map(MembershipAccess)
    val contentRefererOpt = request.headers.get(REFERER)

    val signInUrl = contentRefererOpt.map { referer =>
      ((Config.idWebAppUrl / "signin") ? ("returnUrl" -> referer) ? ("skipConfirmation" -> "true")).toString
    }.getOrElse(Config.idWebAppSigninUrl(""))

    val pageInfo = PageInfo(
      title=CopyConfig.copyTitleChooseTier,
      url=request.path,
      description=Some(CopyConfig.copyDescriptionChooseTier),
      customSignInUrl=Some(signInUrl)
    )

    implicit val currency = countryGroup.currency
    TouchpointBackend.Normal.catalog.map(cat =>
      Ok(views.html.joiner.tierChooser(cat, pageInfo, eventOpt, accessOpt, signInUrl))
        .withSession(request.session.copy(data = request.session.data ++ contentRefererOpt.map(JoinReferrer -> _)))
    )
  }

  def staff = PermanentStaffNonMemberAction.async { implicit request =>
    val flashMsgOpt = request.flash.get("error").map(FlashMessage.error)
    val userSignedIn = AuthenticationService.authenticatedUserFor(request)
    val catalogF = TouchpointBackend.Normal.catalog
    implicit val currency = GBP

    userSignedIn match {
      case Some(user) => for {
        fullUser <- IdentityService(IdentityApi).getFullUserDetails(user, IdentityRequest(request))
        catalog <- catalogF
        primaryEmailAddress = fullUser.primaryEmailAddress
        displayName = fullUser.publicFields.displayName
        avatarUrl = fullUser.privateFields.flatMap(_.socialAvatarUrl)
      } yield {
        Ok(views.html.joiner.staff(catalog, new StaffEmails(request.user.email, Some(primaryEmailAddress)), displayName, avatarUrl, flashMsgOpt))
      }
      case _ => catalogF.map(cat => Ok(views.html.joiner.staff(cat, new StaffEmails(request.user.email, None), None, None, flashMsgOpt)))
    }
  }

  def NonMemberAction(tier: Tier) = AuthenticatedAction andThen onlyNonMemberFilter(onMember = redirectMemberAttemptingToSignUp(tier))

  def enterPaidDetails(tier: PaidTier) = NonMemberAction(tier).async { implicit request =>
    for {
      (privateFields, marketingChoices, passwordExists) <- identityDetails(request.user, request)
      catalog <- request.catalog
    } yield {
      val cg = countryGroup
      val paidDetails = catalog.paidTierDetails(tier)
      val pageInfo = PageInfo(
        stripePublicKey = Some(request.touchpointBackend.stripeService.publicKey),
        formI18n = FormI18n.bindingCurrency(cg)
      )

      Ok(views.html.joiner.form.payment(
         details = paidDetails,
         userFields = setCountry(privateFields, cg),
         marketingChoices = marketingChoices,
         passwordExists = passwordExists,
         pageInfo = pageInfo))
    }
  }

  private def countryGroup(implicit req: RequestHeader): CountryGroup = (for {
    queryValue <- req.getQueryString(countryGroupKey)
    countryGroup <- CountryGroup.byId(queryValue)
  } yield countryGroup).getOrElse(CountryGroup.UK)

  private def setCountry(fields: PrivateFields, cg: CountryGroup): PrivateFields = {
    val country = fields.country.orElse(cg.defaultCountry.map(_.alpha2))
    fields.copy(billingCountry = country)
  }

  def enterFriendDetails = NonMemberAction(Friend).async { implicit request =>
    for {
      (privateFields, marketingChoices, passwordExists) <- identityDetails(request.user, request)
    } yield {
      Ok(views.html.joiner.form.friendSignup(
        privateFields,
        marketingChoices,
        passwordExists,
        support.PageInfo().bindingCurrency))
    }
  }


  def enterStaffDetails = EmailMatchingGuardianAuthenticatedStaffNonMemberAction.async { implicit request =>
    val flashMsgOpt = request.flash.get("success").map(FlashMessage.success)
    for {
      (privateFields, marketingChoices, passwordExists) <- identityDetails(request.identityUser, request)
    } yield {
      Ok(views.html.joiner.form.addressWithWelcomePack(privateFields, marketingChoices, passwordExists, flashMsgOpt))
    }
  }


  private def identityDetails(user: IdMinimalUser, request: Request[_]) = {
    val identityService = IdentityService(IdentityApi)
    val identityRequest = IdentityRequest(request)
    for {
      user <- identityService.getFullUserDetails(user, identityRequest)
      passwordExists <- identityService.doesUserPasswordExist(identityRequest)
    } yield (user.privateFields.getOrElse(PrivateFields()), user.statusFields.getOrElse(StatusFields()), passwordExists)
  }

  def joinFriend = AuthenticatedNonMemberAction.async { implicit request =>
    friendJoinForm.bindFromRequest.fold(redirectToUnsupportedBrowserInfo,
      makeMember(Tier.Friend, Redirect(routes.Joiner.thankyou(Tier.Friend))) )
  }

  def joinStaff = AuthenticatedNonMemberAction.async { implicit request =>
    staffJoinForm.bindFromRequest.fold(redirectToUnsupportedBrowserInfo,
        makeMember(Tier.Partner, Redirect(routes.Joiner.thankyouStaff())) )
  }

  def joinPaid(tier: PaidTier) = AuthenticatedNonMemberAction.async { implicit request =>
    paidMemberJoinForm.bindFromRequest.fold(redirectToUnsupportedBrowserInfo,
      makeMember(tier, Ok(Json.obj("redirect" -> routes.Joiner.thankyou(tier).url))) )
  }

  def updateEmailStaff() = AuthenticatedStaffNonMemberAction.async { implicit request =>
    val googleEmail = request.googleUser.email
    for {
      responseCode <- IdentityService(IdentityApi).updateEmail(request.identityUser, googleEmail, IdentityRequest(request))
    }
    yield {
      responseCode match {
        case 200 => Redirect(routes.Joiner.enterStaffDetails())
                  .flashing("success" ->
          s"Your email address has been changed to $googleEmail")
        case _ => Redirect(routes.Joiner.staff())
                  .flashing("error" ->
          s"There has been an error in updating your email. You may already have an Identity account with $googleEmail. Please try signing in with that email.")
      }
    }
  }

  def unsupportedBrowser = CachedAction(Ok(views.html.joiner.unsupportedBrowser()))

  private def makeMember(tier: Tier, result: Result)(formData: JoinForm)(implicit request: AuthRequest[_]) =
    MemberService.createMember(request.user, formData, IdentityRequest(request), extractCampaignCode(request))
      .map { member =>
        for {
          eventId <- PreMembershipJoiningEventFromSessionExtractor.eventIdFrom(request)
          event <- EventbriteService.getBookableEvent(eventId)
        } {
          event.service.wsMetrics.put(s"join-$tier-event", 1)
          val memberData = MemberData(member.salesforceContactId, request.user.id, tier.name, campaignCode=extractCampaignCode(request))
          track(EventActivity("membershipRegistrationViaEvent", Some(memberData), EventData(event)), request.user)
        }
        result
      } recover {
      case error: Stripe.Error => Forbidden(Json.toJson(error))
      case _: ResultError | _: ScalaforceError | _: MemberServiceError => Forbidden
    }

  def thankyou(tier: Tier, upgrade: Boolean = false) = MemberAction.async { implicit request =>
    implicit val currency = countryGroup.currency

    val futureCustomerOpt = request.member.paymentMethod match {
      case StripePayment(id) =>
        request.touchpointBackend.stripeService.Customer.read(id).map(Some(_))
      case _ => Future.successful(None)
    }

    for {
      paymentSummary <- request.touchpointBackend.subscriptionService.getMembershipSubscriptionSummary(request.member)
      customerOpt <- futureCustomerOpt
      destinationOpt <- DestinationService.returnDestinationFor(request)
    } yield Ok(views.html.joiner.thankyou(
        request.member,
        paymentSummary,
        customerOpt.map(_.card),
        destinationOpt,
        upgrade
    )).discardingCookies(DiscardingCookie("GU_MEM"))
  }

  def thankyouStaff = thankyou(Tier.Partner)
}

object Joiner extends Joiner {
  val memberService = MemberService
}
