package views.support

import com.gu.membership.model.{PaidTierPlan, TierPlan}
import com.gu.membership.zuora.rest.PricingSummary
import model.MembershipCatalog.Val
import model.{MembershipCatalog, PaidTierPlanDetails, TierPlanDetails}

import scalaz.NonEmptyList
import scalaz.syntax.std.option._

object Catalog {
  case class Diagnostic(comparisonTable: Option[ComparisonTable], errorTables: Seq[ErrorTable])

  object Diagnostic {
    import MembershipCatalog.Val
    def fromCatalogs(testCat: Val[MembershipCatalog], normalCat: Val[MembershipCatalog]): Diagnostic = {
      val test = "test"
      val normal = "normal"

      val comparisonTable =
        (ComparisonTable.fromCatalog(testCat, test), ComparisonTable.fromCatalog(normalCat, normal)) match {
          case (Some(t), Some(n)) => Some(t.interleave(n))
          case (Some(t), _) => Some(t)
          case (_, Some(n)) => Some(n)
          case _ => None
        }

      val errorsTables =
        Seq(ErrorTable.fromCatalog(testCat, test), ErrorTable.fromCatalog(normalCat, normal)).flatten

      Diagnostic(comparisonTable, errorsTables)
    }
  }

  case class TierPlanDescription(name: String, env: String, productRatePlanId: String, prices: String)

  object TierPlanDescription {
    def fromPlanDetails(env: String)(tpd: TierPlanDetails): TierPlanDescription = {
      val prices = tpd match {
        case paid: PaidTierPlanDetails =>
          paid.pricingByCurrency.value.toSeq.map { case (c, p) => s"$c: $p" }.mkString(", ")
        case _ => "FREE"
      }
      TierPlanDescription(tierPlanName(tpd.plan), env, tpd.productRatePlanId, prices)
    }
  }

  case class TierPlanError(name: String, errorMsg: String)

  case class ComparisonTable(rows: Seq[TierPlanDescription]) {
    def interleave(other: ComparisonTable) =
      ComparisonTable(
        rows.zip(other.rows)
          .flatMap { case p => Seq(p._1, p._2) }
          .sortBy { r => r.name + r.env }
      )
  }

  object ComparisonTable {
    def fromCatalog(catalog: Val[MembershipCatalog], env: String): Option[ComparisonTable] =
      catalog.fold(_ => None, c =>
        ComparisonTable(c.allTierPlanDetails.map(TierPlanDescription.fromPlanDetails(env))).some
      )
  }

  case class ErrorTable(env: String, rows: NonEmptyList[TierPlanError])

  object ErrorTable {
    def fromCatalog(catalog: Val[MembershipCatalog], env: String): Option[ErrorTable] =
      catalog.fold(errs =>
        ErrorTable(
          env, errs.map { case (tp, msg) => TierPlanError(tierPlanName(tp), msg)}
        ).some, _ => None)
  }

  private def tierPlanName(tp: TierPlan): String = {
    val basic  = s"${tp.tier} ${tp.status}"
    val suffix = tp match {
      case PaidTierPlan(_, billingPeriod, _) => s" - ${billingPeriod.adverb}"
      case _ => ""
    }
    basic + suffix
  }
}
