/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.libs.json.Json.{obj, toJson}
import play.api.mvc.Request
import play.api.{Configuration, Logger, LoggerLike}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobiletaxcreditsrenewal.connectors._
import uk.gov.hmrc.mobiletaxcreditsrenewal.controllers.HeaderKeys.tcrAuthToken
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._
import uk.gov.hmrc.mobiletaxcreditsrenewal.utils.ClaimsDateConverter
import uk.gov.hmrc.play.audit.AuditExtensions.auditHeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import uk.gov.hmrc.service.Auditor

import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MobileTaxCreditsRenewalService @Inject()(
  val ntcConnector:                      NtcConnector,
  val auditConnector:                    AuditConnector,
  val appNameConfiguration:              Configuration,
  val taxCreditsSubmissionControlConfig: TaxCreditsControl,
  val logger:                            LoggerLike,
  @Named("appName") val appName:         String
) extends Auditor
    with RenewalStatus {

  private val dateConverter: ClaimsDateConverter = new ClaimsDateConverter

  def renewals(
    nino:      Nino,
    journeyId: Option[String] = None)(implicit headerCarrier: HeaderCarrier, ex: ExecutionContext, request: Request[_]): Future[RenewalsSummary] = {
    val currentState: TaxCreditsRenewalsState = taxCreditsSubmissionControlConfig.toTaxCreditsRenewalsState

    def auditAndReturnRenewalsData(maybeClaims: Option[Seq[Claim]]): RenewalsSummary = {
      val renewalsData = RenewalsSummary(currentState.submissionsState, maybeClaims)
      auditConnector.sendExtendedEvent(
        ExtendedDataEvent(
          appName,
          "Renewals",
          tags   = headerCarrier.toAuditTags("retrieve-tax-credit-renewal", request.path),
          detail = toJson(renewalsData)))
      renewalsData
    }

    if (currentState.showSummaryData) {
      claimsDetails(nino, journeyId).map { claims =>
        auditAndReturnRenewalsData(Some(claims))
      }
    } else {
      Future successful auditAndReturnRenewalsData(None)
    }
  }

  def authenticateRenewal(nino: Nino, tcrRenewalReference: RenewalReference)(
    implicit hc:                HeaderCarrier,
    ex:                         ExecutionContext): Future[Option[TcrAuthenticationToken]] =
    withAudit("authenticateRenewal", Map("nino" -> nino.value)) {
      ntcConnector.authenticateRenewal(TaxCreditsNino(nino.value), tcrRenewalReference)
    }

  def claimantDetails(nino: Nino)(implicit headerCarrier: HeaderCarrier, ex: ExecutionContext): Future[ClaimantDetails] =
    withAudit("claimantDetails", Map("nino" -> nino.value)) {
      ntcConnector.claimantDetails(TaxCreditsNino(nino.value))
    }

  def legacyClaimantClaims(nino: Nino)(implicit headerCarrier: HeaderCarrier, ex: ExecutionContext): Future[LegacyClaims] =
    withAudit("claimantClaims", Map("nino" -> nino.value)) {

      def claimMatch(claim: LegacyClaim): Boolean = {
        val match1 = claim.household.applicant1.nino == nino.value
        val match2 = claim.household.applicant2.fold(false) { found =>
          found.nino == nino.value
        }
        match1 || match2
      }

      def reformatDateAndLogErrors(maybeDateString: Option[String]): Option[String] =
        maybeDateString.flatMap { dateString =>
          dateConverter.convertDateFormat(dateString).orElse {
            Logger.error(s"Failed to convert input date $dateString for NINO ${nino.value}. Removing date from response!")
            None
          }
        }

      ntcConnector.legacyClaimantClaims(TaxCreditsNino(nino.value)).map { claims =>
        claims.references.fold(LegacyClaims(None)) { items =>
          val references = items
            .filter(a => claimMatch(a))
            .map { claim =>
              LegacyClaim(
                claim.household.copy(householdCeasedDate = reformatDateAndLogErrors(claim.household.householdCeasedDate)),
                LegacyRenewal(
                  reformatDateAndLogErrors(claim.renewal.awardStartDate),
                  reformatDateAndLogErrors(claim.renewal.awardEndDate),
                  Some(legacyResolveStatus(claim)),
                  reformatDateAndLogErrors(claim.renewal.renewalNoticeIssuedDate),
                  reformatDateAndLogErrors(claim.renewal.renewalNoticeFirstSpecifiedDate)
                )
              )
            }
          LegacyClaims(Some(references))
        }
      }
    }

  private def claimsDetails(nino: Nino, journeyId: Option[String] = None)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Seq[Claim]] = {
    def fullClaimantDetails(claim: Claim): Future[Claim] = {
      def getClaimantDetail(token: TcrAuthenticationToken, hc: HeaderCarrier): Future[Claim] = {
        implicit val hcWithToken: HeaderCarrier = hc.copy(extraHeaders = Seq(tcrAuthToken -> token.tcrAuthToken))

        ntcConnector.claimantDetails(TaxCreditsNino(nino.value)).map { claimantDetails =>
          claim.copy(household = claim.household.copy(), renewal = claim.renewal.copy(claimantDetails = Some(claimantDetails)))
        }
      }

      ntcConnector
        .authenticateRenewal(TaxCreditsNino(nino.value), RenewalReference(claim.household.barcodeReference))
        .flatMap { maybeToken =>
          if (maybeToken.nonEmpty)
            getClaimantDetail(maybeToken.get, hc)
          else
            Future successful claim
        }
        .recover {
          case e: Exception =>
            logger.warn(s"${e.getMessage} for ${claim.household.barcodeReference}")
            claim
        }
    }

    val eventualClaims: Future[Seq[Claim]] = claimantClaims(nino).flatMap { claimantClaims =>
      val claims: Seq[Claim] = claimantClaims.references.getOrElse(Seq.empty[Claim])

      if (claims.isEmpty) logger.warn(s"Empty claims list for journeyId $journeyId")

      Future sequence claims.map { claim =>
        val barcodeReference = claim.household.barcodeReference

        if (barcodeReference.equals("000000000000000")) {
          logger.warn(s"Invalid barcode reference $barcodeReference for journeyId $journeyId applicationId ${claim.household.applicationID}")
          Future successful claim
        } else fullClaimantDetails(claim)
      }
    }

    eventualClaims
  }

  private def claimantClaims(nino: Nino)(implicit headerCarrier: HeaderCarrier, ex: ExecutionContext): Future[Claims] = {
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
          Logger.error(s"Failed to convert input date $dateString for NINO ${nino.value}. Removing date from response!")
          None
        }
      }

    ntcConnector.claimantClaims(TaxCreditsNino(nino.value)).map { claims =>
      claims.references.fold(Claims(None)) { items =>
        {
          val references = items.filter(a => claimMatch(a)).map { claim =>
            Claim(
              claim.household.copy(householdCeasedDate = reformatDateAndLogErrors(claim.household.householdCeasedDate)),
              Renewal(
                reformatDateAndLogErrors(claim.renewal.awardStartDate),
                reformatDateAndLogErrors(claim.renewal.awardEndDate),
                Some(resolveStatus(claim)),
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

  def submitRenewal(nino: Nino, tcrRenewal: TcrRenewal)(implicit hc: HeaderCarrier, ex: ExecutionContext, request: Request[_]): Future[Int] =
    ntcConnector.submitRenewal(TaxCreditsNino(nino.value), tcrRenewal).map { status =>
      auditConnector.sendExtendedEvent(
        ExtendedDataEvent(
          appName,
          "SubmitDeclaration",
          tags   = hc.toAuditTags("submit-tax-credit-renewal", request.path),
          detail = obj("nino" -> nino, "declaration" -> tcrRenewal)))
      status
    }
}

case class RenewalStatusTransform(name: String, statusValues: Seq[String])

trait RenewalStatus {
  val defaultRenewalStatus = "NOT_SUBMITTED"
  val awaitingBarcode      = "AWAITING_BARCODE"
  val no_barcode           = "000000000000000"

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
    Logger.warn(s"Failed to resolve renewalStatus $returned against configuration! Returning default status.")
    defaultRenewalStatus
  }

  def resolveStatus(claim: Claim): String =
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

  def legacyResolveStatus(claim: LegacyClaim): String =
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
