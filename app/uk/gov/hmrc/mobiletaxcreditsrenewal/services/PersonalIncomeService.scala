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
import play.api.{Configuration, Logger}
import uk.gov.hmrc.api.sandbox._
import uk.gov.hmrc.api.service._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobiletaxcreditsrenewal.config.{AppContext, RenewalStatusTransform}
import uk.gov.hmrc.mobiletaxcreditsrenewal.connectors._
import uk.gov.hmrc.mobiletaxcreditsrenewal.controllers.HeaderKeys
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._
import uk.gov.hmrc.mobiletaxcreditsrenewal.utils.ClaimsDateConverter
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.{ExecutionContext, Future}

trait PersonalIncomeService {
  // Renewal specific - authenticateRenewal must be called first to retrieve the authToken before calling claimantDetails, submitRenewal.
  def authenticateRenewal(nino: Nino, tcrRenewalReference: RenewalReference)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[TcrAuthenticationToken]]

  def claimantDetails(nino: Nino)(implicit headerCarrier: HeaderCarrier, ex: ExecutionContext): Future[ClaimantDetails]

  def claimantClaims(nino: Nino)(implicit headerCarrier: HeaderCarrier, ex: ExecutionContext): Future[Claims]

  def submitRenewal(nino: Nino, tcrRenewal: TcrRenewal)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Response]
}

@Singleton
class LivePersonalIncomeService @Inject()(ntcConnector: NtcConnector,
                                          val auditConnector: AuditConnector,
                                          val appNameConfiguration: Configuration,
                                          val appContext: AppContext) extends PersonalIncomeService with Auditor with RenewalStatus {
  private val dateConverter: ClaimsDateConverter = new ClaimsDateConverter

  // Note: The TcrAuthenticationToken must be supplied to claimantDetails and submitRenewal.
  override def authenticateRenewal(nino: Nino, tcrRenewalReference: RenewalReference)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[TcrAuthenticationToken]] = {
    withAudit("authenticateRenewal", Map("nino" -> nino.value)) {
      ntcConnector.authenticateRenewal(TaxCreditsNino(nino.value), tcrRenewalReference)
    }
  }

  override def claimantDetails(nino: Nino)(implicit headerCarrier: HeaderCarrier, ex: ExecutionContext): Future[ClaimantDetails] = {
    withAudit("claimantDetails", Map("nino" -> nino.value)) {
      ntcConnector.claimantDetails(TaxCreditsNino(nino.value))
    }
  }

  override def claimantClaims(nino: Nino)(implicit headerCarrier: HeaderCarrier, ex: ExecutionContext): Future[Claims] = {
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
          val references = items.filter(a => claimMatch(a))
            .map { claim =>
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

  override def submitRenewal(nino: Nino, tcrRenewal: TcrRenewal)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Response] = {
    withAudit("submitRenewal", Map("nino" -> nino.value)) {
      ntcConnector.submitRenewal(TaxCreditsNino(nino.value), tcrRenewal)
    }
  }
}

trait RenewalStatus {
  val defaultRenewalStatus = "NOT_SUBMITTED"
  val awaitingBarcode = "AWAITING_BARCODE"
  val no_barcode = "000000000000000"

  val appContext: AppContext

  lazy val config: List[RenewalStatusTransform] = appContext.renewalStatusTransform
    .map(transform => transform.copy(statusValues = transform.statusValues.map(_.toUpperCase)))

  def defaultRenewalStatusReturned(returned: String): String = {
    Logger.warn(s"Failed to resolve renewalStatus $returned against configuration! Returning default status.")
    defaultRenewalStatus
  }

  def resolveStatus(claim: Claim): String = {
    if (claim.household.barcodeReference.equals(no_barcode)) {
      awaitingBarcode
    } else {
      claim.renewal.renewalStatus.fold(defaultRenewalStatus) { renewalStatus =>
        config.flatMap { (item: RenewalStatusTransform) =>
          if (item.statusValues.contains(renewalStatus.toUpperCase.trim)) Some(item.name) else None
        }.headOption.getOrElse(defaultRenewalStatusReturned(renewalStatus))
      }
    }
  }
}

object SandboxPersonalIncomeService extends PersonalIncomeService with FileResource {
  private def basicAuthString(encodedAuth: String): String = "Basic " + encodedAuth

  private def encodedAuth(nino: Nino, tcrRenewalReference: RenewalReference): String =
    new String(Base64.encode(s"${nino.value}:${tcrRenewalReference.value}".getBytes))

  private def getTcrAuthHeader[T](func: TcrAuthenticationToken => T)(implicit headerCarrier: HeaderCarrier): T = {
    headerCarrier.extraHeaders.headOption match {
      case Some((HeaderKeys.tcrAuthToken, t@TcrAuthCheck(_))) => func(TcrAuthenticationToken(t))
      case _ => throw new IllegalArgumentException("Failed to locate tcrAuthToken")
    }
  }

  override def authenticateRenewal(nino: Nino, tcrRenewalReference: RenewalReference)
                                  (implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[TcrAuthenticationToken]] =
    Future.successful(Some(TcrAuthenticationToken(basicAuthString(encodedAuth(nino, tcrRenewalReference)))))

  override def claimantDetails(nino: Nino)(implicit headerCarrier: HeaderCarrier, ex: ExecutionContext): Future[ClaimantDetails] = {
    getTcrAuthHeader { header =>
      try {
        val resource: String = findResource(s"/resources/claimantdetails/${nino.value}-${header.extractRenewalReference.get}.json")
          .getOrElse(throw new IllegalArgumentException("Resource not found!"))
        Future.successful(Json.parse(resource).as[ClaimantDetails])
      } catch {
        case _: Exception => Future.successful(ClaimantDetails(hasPartner = false, 1, "r", "false", None, availableForCOCAutomation = false, "some-app-id"))
      }
    }
  }

  override def claimantClaims(nino: Nino)(implicit headerCarrier: HeaderCarrier, ex: ExecutionContext): Future[Claims] = {
    val resource: String = findResource(s"/resources/claimantdetails/claims.json").getOrElse(throw new IllegalArgumentException("Resource not found!"))
    Future.successful(Json.parse(resource).as[Claims])
  }

  override def submitRenewal(nino: Nino, tcrRenewal: TcrRenewal)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Response] =
    Future.successful(Success(200))
}
