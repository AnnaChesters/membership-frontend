package model

import com.gu.membership.model._
import com.gu.membership.zuora.rest.PricingSummary

sealed trait TierPlanDetails {
  def plan: TierPlan
  def productRatePlanId: String
}

case class FreeTierPlanDetails(plan: FreeTierPlan, productRatePlanId: String) extends TierPlanDetails

case class PaidTierPlanDetails(plan: PaidTierPlan, productRatePlanId: String, pricingByCurrency: PricingSummary) extends TierPlanDetails {
  require(pricingByCurrency.value.contains(GBP), "Paid plans need to contain a GBP price")

  lazy val billingPeriod = plan.billingPeriod
  lazy val priceGBP: Float = pricingByCurrency.getPrice(GBP).get
  lazy val currencies: Set[Currency] = pricingByCurrency.value.keys.toSet
}
