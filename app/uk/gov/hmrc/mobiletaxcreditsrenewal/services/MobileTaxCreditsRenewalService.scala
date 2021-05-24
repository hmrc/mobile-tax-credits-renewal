/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.mobiletaxcreditsrenewal.services

import com.google.inject.{Inject, Singleton}

import javax.inject.Named
import play.api.{Configuration, Logger}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobiletaxcreditsrenewal.connectors.{NtcConnector, TaxCreditsBrokerConnector}
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._
import uk.gov.hmrc.mobiletaxcreditsrenewal.utils.ClaimsDateConverter
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.service.Auditor

import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MobileTaxCreditsRenewalService @Inject() (
  val ntcConnector:                      NtcConnector,
  val taxCreditsBrokerConnector:         TaxCreditsBrokerConnector,
  val auditConnector:                    AuditConnector,
  val appNameConfiguration:              Configuration,
  val taxCreditsSubmissionControlConfig: TaxCreditsControl,
  @Named("appName") val appName:         String)
    extends Auditor
    with RenewalStatus {

  private val dateConverter: ClaimsDateConverter = new ClaimsDateConverter
  override val logger: Logger = Logger(this.getClass)

  def authenticateRenewal(
    nino:                Nino,
    tcrRenewalReference: RenewalReference
  )(implicit hc:         HeaderCarrier,
    ex:                  ExecutionContext
  ): Future[Option[TcrAuthenticationToken]] =
    withAudit("authenticateRenewal", Map("nino" -> nino.value)) {
      ntcConnector.authenticateRenewal(TaxCreditsNino(nino.value), tcrRenewalReference)
    }

  def claimantDetails(
    nino:                   Nino
  )(implicit headerCarrier: HeaderCarrier,
    ex:                     ExecutionContext
  ): Future[ClaimantDetails] =
    withAudit("claimantDetails", Map("nino" -> nino.value)) {
      ntcConnector.claimantDetails(TaxCreditsNino(nino.value))
    }

  def employedEarningsRti(
    nino:                   Nino
  )(implicit headerCarrier: HeaderCarrier,
    ex:                     ExecutionContext
  ): Future[Option[EmployedEarningsRti]] =
    withAudit("employedEarningsRti", Map("nino" -> nino.value)) {
      taxCreditsBrokerConnector.employedEarningsRti(TaxCreditsNino(nino.value))
    }

  def legacyClaimantClaims(
    nino:                   Nino
  )(implicit headerCarrier: HeaderCarrier,
    ex:                     ExecutionContext
  ): Future[Claims] =
    withAudit("claimantClaims", Map("nino" -> nino.value)) {

      def claimMatch(claim: Claim): Boolean = {
        val match1 = claim.household.applicant1.nino == nino.value
        val match2 = claim.household.applicant2.fold(false) { found =>
          found.nino == nino.value
        }
        match1 || match2
      }

      def reformatDateAndLogErrors(maybeDateString: Option[String]): Option[String] =
        maybeDateString.flatMap { dateString =>
          dateConverter.convertDateFormat(dateString).orElse {
            logger
              .error(s"Failed to convert input date $dateString for NINO ${nino.value}. Removing date from response!")
            None
          }
        }

      ntcConnector.legacyClaimantClaims(TaxCreditsNino(nino.value)).map { claims: Claims =>
        claims.references.fold(Claims(None)) { items =>
          val references = items
            .filter(a => claimMatch(a))
            .map { claim =>
              Claim(
                claim.household
                  .copy(householdCeasedDate = reformatDateAndLogErrors(claim.household.householdCeasedDate)),
                Renewal(
                  reformatDateAndLogErrors(claim.renewal.awardStartDate),
                  reformatDateAndLogErrors(claim.renewal.awardEndDate),
                  Some(legacyResolveStatus(claim)),
                  reformatDateAndLogErrors(claim.renewal.renewalNoticeIssuedDate),
                  reformatDateAndLogErrors(claim.renewal.renewalNoticeFirstSpecifiedDate)
                )
              )
            }
          Claims(Some(references))
        }
      }
    }

}

case class RenewalStatusTransform(
  name:         String,
  statusValues: Seq[String])

trait RenewalStatus {
  val defaultRenewalStatus = "NOT_SUBMITTED"
  val awaitingBarcode      = "AWAITING_BARCODE"
  val no_barcode           = "000000000000000"

  val logger: Logger = Logger(this.getClass)

  val transformations: List[RenewalStatusTransform] = List[RenewalStatusTransform](
    RenewalStatusTransform("NOT_SUBMITTED", Seq("DISREGARD", "UNKNOWN")),
    RenewalStatusTransform(
      "SUBMITTED_AND_PROCESSING",
      Seq(
        "S17 LOGGED",
        "SUPERCEDED",
        "PARTIAL CAPTURE",
        "AWAITING PROCESS",
        "INHIBITED",
        "AWAITING CHANGE OF CIRCUMSTANCES",
        "1 REPLY FROM 2 APPLICANT HOUSEHOLD",
        "DUPLICATE"
      )
    ),
    RenewalStatusTransform("COMPLETE", Seq("REPLY USED FOR FINALISATION", "SYSTEM FINALISED"))
  )

  def defaultRenewalStatusReturned(returned: String): String = {
    logger.warn(s"Failed to resolve renewalStatus $returned against configuration! Returning default status.")
    defaultRenewalStatus
  }

  def legacyResolveStatus(claim: Claim): String =
    if (claim.household.barcodeReference.equals(no_barcode)) {
      awaitingBarcode
    } else {
      claim.renewal.renewalStatus.fold(defaultRenewalStatus) { renewalStatus =>
        transformations
          .flatMap { item: RenewalStatusTransform =>
            if (item.statusValues.contains(renewalStatus.toUpperCase.trim)) Some(item.name) else None
          }
          .headOption
          .getOrElse(defaultRenewalStatusReturned(renewalStatus))
      }
    }
}
