package model

import com.gu.membership.salesforce.Tier
import com.gu.membership.salesforce.Tier.Tier

case class PaidPlan(monthly: String, annual: String)

trait ProductRatePlan {
  def salesforceTier: String
}

trait TierPlan extends ProductRatePlan {
  val tier: Tier
  def salesforceTier = tier.toString
}

object FriendTierPlan extends TierPlan {
  val tier = Tier.Friend

  override val hashCode = 0 // This is here to give TouchpointBackendConfig a consistent hash over multiple JVM runs
}

case class PaidTierPlan(tier: Tier, annual: Boolean) extends TierPlan {
  assert(tier >= Tier.Partner)
}

object StaffPlan extends ProductRatePlan {
  def salesforceTier = "Staff"
}
