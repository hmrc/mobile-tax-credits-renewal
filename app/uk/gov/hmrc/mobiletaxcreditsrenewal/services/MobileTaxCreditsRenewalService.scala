/*
 * Copyright 2018 HM Revenue & Customs
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
import com.ning.http.util.Base64
import play.api.libs.json.Json
import play.api.{Configuration, Logger, LoggerLike}
import uk.gov.hmrc.api.sandbox._
import uk.gov.hmrc.api.service._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobiletaxcreditsrenewal.connectors._
import uk.gov.hmrc.mobiletaxcreditsrenewal.controllers.HeaderKeys
import uk.gov.hmrc.mobiletaxcreditsrenewal.controllers.HeaderKeys.tcrAuthToken
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._
import uk.gov.hmrc.mobiletaxcreditsrenewal.utils.ClaimsDateConverter
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

trait MobileTaxCreditsRenewalService {
  def renewals(nino: Nino, journeyId: Option[String])(implicit headerCarrier: HeaderCarrier, ex: ExecutionContext): Future[RenewalsSummary]

  def submitRenewal(nino: Nino, tcrRenewal: TcrRenewal)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Int]
}

@Singleton
class LiveMobileTaxCreditsRenewalService @Inject()(
  val ntcConnector: NtcConnector,
  val auditConnector: AuditConnector,
  val appNameConfiguration: Configuration,
  val taxCreditsSubmissionControlConfig: TaxCreditsControl,
  val logger: LoggerLike ) extends MobileTaxCreditsRenewalService with Auditor with RenewalStatus {

  private val dateConverter: ClaimsDateConverter = new ClaimsDateConverter

  override def renewals(nino: Nino, journeyId: Option[String] = None)(implicit headerCarrier: HeaderCarrier, ex: ExecutionContext): Future[RenewalsSummary] = {
    val currentState: TaxCreditsRenewalsState = taxCreditsSubmissionControlConfig.toTaxCreditsRenewalsState

    if (currentState.showSummaryData) {
      claimsDetails(nino,journeyId).map{ claims =>
        RenewalsSummary(currentState.submissionsState, Some(claims))
      }
    } else {
      Future successful RenewalsSummary(currentState.submissionsState, None)
    }
  }

  private def claimsDetails(nino: Nino, journeyId: Option[String] = None)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Seq[Claim]] = {
    def fullClaimantDetails(claim: Claim): Future[Claim] = {
      def getClaimantDetail(token:TcrAuthenticationToken, hc: HeaderCarrier): Future[Claim] = {
        implicit val hcWithToken: HeaderCarrier = hc.copy(extraHeaders = Seq(tcrAuthToken -> token.tcrAuthToken))

          withAudit("claimantDetails", Map("nino" -> nino.value)) {
            ntcConnector.claimantDetails(TaxCreditsNino(nino.value)).map { claimantDetails =>
              claim.copy(
                household = claim.household.copy(),
                renewal = claim.renewal.copy(claimantDetails = Some(claimantDetails)))
            }
          }
      }

        withAudit("authenticateRenewal", Map("nino" -> nino.value)) {
          ntcConnector.authenticateRenewal(TaxCreditsNino(nino.value), RenewalReference(claim.household.barcodeReference)).flatMap {
            maybeToken =>
            if (maybeToken.nonEmpty)
              getClaimantDetail(maybeToken.get,hc)
            else
              Future successful claim
          }.recover{
            case e: Exception =>
              logger.warn(s"${e.getMessage} for ${claim.household.barcodeReference}")
              claim
          }
        }
    }

    val eventualClaims: Future[Seq[Claim]] = claimantClaims(nino).flatMap { claimantClaims =>
      val claims: Seq[Claim] = claimantClaims.references.getOrElse(Seq.empty[Claim])

      if ( claims.isEmpty ) logger.warn(s"Empty claims list for journeyId $journeyId")

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
    withAudit("claimantClaims", Map("nino" -> nino.value)) {

      def claimMatch(claim: Claim) = {
        val match1 = claim.household.applicant1.nino == nino.value
        val match2 = claim.household.applicant2.fold(false) { found => found.nino == nino.value }
        match1 || match2
      }

      def reformatDateAndLogErrors(maybeDateString: Option[String]): Option[String] = {
        maybeDateString.flatMap { dateString =>
          dateConverter.convertDateFormat(dateString).orElse {
            Logger.error(s"Failed to convert input date $dateString for NINO ${nino.value}. Removing date from response!")
            None
          }
        }
      }

      ntcConnector.claimantClaims(TaxCreditsNino(nino.value)).map { claims =>
        claims.references.fold(Claims(None)) { items => {
          val references = items.filter(a => claimMatch(a)).map { claim =>
              Claim(claim.household.copy(householdCeasedDate = reformatDateAndLogErrors(claim.household.householdCeasedDate)),
                Renewal(
                  reformatDateAndLogErrors(claim.renewal.awardStartDate),
                  reformatDateAndLogErrors(claim.renewal.awardEndDate),
                  Some(resolveStatus(claim)),
                  reformatDateAndLogErrors(claim.renewal.renewalNoticeIssuedDate),
                  reformatDateAndLogErrors(claim.renewal.renewalNoticeFirstSpecifiedDate)))
              }
            Claims(Some(references))
          }
        }
      }
    }
  }

  override def submitRenewal(nino: Nino, tcrRenewal: TcrRenewal)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Int] = {
      ntcConnector.submitRenewal(TaxCreditsNino(nino.value), tcrRenewal).map{ status =>
        audit("submitRenewal", Map("nino" -> nino.value))
        status
      }
  }
}

case class RenewalStatusTransform(name: String, statusValues: Seq[String])

trait RenewalStatus {
  val defaultRenewalStatus = "NOT_SUBMITTED"
  val awaitingBarcode = "AWAITING_BARCODE"
  val no_barcode = "000000000000000"

  val transformations: List[RenewalStatusTransform] = List[RenewalStatusTransform](
    RenewalStatusTransform("NOT_SUBMITTED", Seq("DISREGARD", "UNKNOWN")),
    RenewalStatusTransform("SUBMITTED_AND_PROCESSING",
      Seq(
        "S17 LOGGED",
        "SUPERCEDED",
        "PARTIAL CAPTURE",
        "AWAITING PROCESS",
        "INHIBITED",
        "AWAITING CHANGE OF CIRCUMSTANCES",
        "1 REPLY FROM 2 APPLICANT HOUSEHOLD",
        "DUPLICATE")),
    RenewalStatusTransform("COMPLETE", Seq("REPLY USED FOR FINALISATION", "SYSTEM FINALISED"))
  )


  def defaultRenewalStatusReturned(returned: String): String = {
    Logger.warn(s"Failed to resolve renewalStatus $returned against configuration! Returning default status.")
    defaultRenewalStatus
  }

  def resolveStatus(claim: Claim): String = {
    if (claim.household.barcodeReference.equals(no_barcode)) {
      awaitingBarcode
    } else {
      claim.renewal.renewalStatus.fold(defaultRenewalStatus) { renewalStatus =>
        transformations.flatMap { (item: RenewalStatusTransform) =>
          if (item.statusValues.contains(renewalStatus.toUpperCase.trim)) Some(item.name) else None
        }.headOption.getOrElse(defaultRenewalStatusReturned(renewalStatus))
      }
    }
  }
}

class SandboxMobileTaxCreditsRenewalService(val taxCreditsSubmissionControlConfig: TaxCreditsControl) extends MobileTaxCreditsRenewalService with FileResource {
  private def basicAuthString(encodedAuth: String): String = "Basic " + encodedAuth

  private def encodedAuth(nino: Nino, tcrRenewalReference: RenewalReference): String =
    new String(Base64.encode(s"${nino.value}:${tcrRenewalReference.value}".getBytes))

  private def getTcrAuthHeader[T](func: TcrAuthenticationToken => T)(implicit headerCarrier: HeaderCarrier): T = {
    headerCarrier.extraHeaders.headOption match {
      case Some((HeaderKeys.tcrAuthToken, t@TcrAuthCheck(_))) => func(TcrAuthenticationToken(t))
      case _ => throw new IllegalArgumentException("Failed to locate tcrAuthToken")
    }
  }

  override def submitRenewal(nino: Nino, tcrRenewal: TcrRenewal)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Int] =
    Future.successful(200)

  override def renewals(nino: Nino, journeyId: Option[String])(implicit headerCarrier: HeaderCarrier, ex: ExecutionContext): Future[RenewalsSummary] = {
    val resource: String =
      findResource(s"/resources/claimantdetails/renewals-response-open.json").getOrElse(
        throw new IllegalArgumentException("Resource not found!"))
    val renewals = Json.parse(resource).as[RenewalsSummary]
    Future successful renewals
  }
}
