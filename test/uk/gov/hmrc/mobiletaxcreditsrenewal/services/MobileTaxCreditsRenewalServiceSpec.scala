/*
 * Copyright 2023 HM Revenue & Customs
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

import eu.timepit.refined.auto._
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.Configuration
import uk.gov.hmrc.api.sandbox.FileResource
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobiletaxcreditsrenewal.connectors.{NtcConnector, TaxCreditsBrokerConnector}
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.types.ModelTypes.JourneyId
import uk.gov.hmrc.mobiletaxcreditsrenewal.stubs.{NtcConnectorStub, TaxCreditsBrokerConnectorStub}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success
import uk.gov.hmrc.play.audit.model.DataEvent

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class MobileTaxCreditsRenewalServiceSpec
    extends AnyWordSpecLike
    with Matchers
    with MockFactory
    with NtcConnectorStub
    with TaxCreditsBrokerConnectorStub
    with FileResource {
  implicit val hc:      HeaderCarrier = HeaderCarrier()
  implicit val request: Request[_]    = FakeRequest()

  implicit val ntcConnector:              NtcConnector              = mock[NtcConnector]
  implicit val taxCreditsBrokerConnector: TaxCreditsBrokerConnector = mock[TaxCreditsBrokerConnector]
  implicit val auditConnector:            AuditConnector            = mock[AuditConnector]
  implicit val auditService:              AuditService              = new AuditService(auditConnector, "mobile-tax-credits-renewal")
  implicit val taxCreditsControl:         TaxCreditsControl         = mock[TaxCreditsControl]
  implicit val configuration:             Configuration             = mock[Configuration]

  val nino           = Nino("CS700100A")
  val taxCreditsNino = TaxCreditsNino(nino.nino)
  val journeyId: JourneyId = "87144372-6bda-4cc9-87db-1d52fd96498f"

  val service = new MobileTaxCreditsRenewalService(ntcConnector,
                                                   taxCreditsBrokerConnector,
                                                   auditConnector,
                                                   configuration,
                                                   taxCreditsControl,
                                                   "mobile-tax-credits-renewal",
                                                   auditService)

  "authenticateRenewal" should {
    val tcrAuthToken     = TcrAuthenticationToken("some-auth-token")
    val renewalReference = RenewalReference("111111111111111")
    "authenticate the renewal and audit the request" in {
      stubAuthenticateRenewal(taxCreditsNino, renewalReference, tcrAuthToken)
      (auditConnector
        .sendEvent(_: DataEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future successful Success)

      await(service.authenticateRenewal(nino, renewalReference)).get shouldBe tcrAuthToken
    }
  }

  "claimantDetails" should {
    "get claimant details and audit the request" in {
      val claimantDetails =
        ClaimantDetails(hasPartner = false, 1, "r", nino.nino, None, availableForCOCAutomation = false, "some-app-id")
      stubClaimantDetails(taxCreditsNino, claimantDetails)
      (auditConnector
        .sendEvent(_: DataEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future successful Success)

      await(service.claimantDetails(nino)) shouldBe claimantDetails
    }
  }

  "employedEarningsRti" should {

    "get employed earnings RTI and audit the request" in {
      val employedEarningsRti = Some(EmployedEarningsRti(Some(20000.0), Some(20000.0)))

      stubEmployedEarningsRti(taxCreditsNino, employedEarningsRti)
      (auditConnector
        .sendEvent(_: DataEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future successful Success)

      await(service.employedEarningsRti(nino)) shouldBe employedEarningsRti
    }

  }

  "legacyClaimantClaims" should {
    val ntcFormattedDate    = Some("2018-05-30")
    val mobileFormattedDate = Some("30/5/2018")
    val barcode1            = RenewalReference("barcode1")
    val barcode2            = RenewalReference("barcode2")
    val barcode3            = RenewalReference("barcode3")
    val awaitingBarcode     = RenewalReference("000000000000000")

    def claim(
      barcodeReference: RenewalReference,
      maybeDate:        Option[String],
      renewalState:     Option[String] = Some("NOT_SUBMITTED")
    ): Claim = {
      val applicant: Applicant = Applicant(nino.nino, "title", "firstForename", None, "surname", None)
      val foundHouseHold =
        Household(barcodeReference.value, "applicationId", applicant, None, maybeDate, Some("householdEndReason"))
      val foundRenewal = Renewal(maybeDate, maybeDate, renewalState, maybeDate, maybeDate)
      Claim(foundHouseHold, foundRenewal)
    }

    val claimWithAuthTokenAndClaimantDetails   = claim(barcode1, ntcFormattedDate)
    val claimWithNoAuthToken                   = claim(barcode2, ntcFormattedDate)
    val claimWithAuthTokenButNoClaimantDetails = claim(barcode3, ntcFormattedDate)
    val claimAaitingBarcode                    = claim(awaitingBarcode, ntcFormattedDate)

    val foundClaims: Claims =
      Claims(
        Some(
          Seq(claimWithAuthTokenAndClaimantDetails,
              claimWithNoAuthToken,
              claimWithAuthTokenButNoClaimantDetails,
              claimAaitingBarcode)
        )
      )

    val expectedClaimsSeq = Seq(
      claim(barcode1, mobileFormattedDate),
      claim(barcode2, mobileFormattedDate),
      claim(barcode3, mobileFormattedDate),
      claim(awaitingBarcode, mobileFormattedDate, Some("AWAITING_BARCODE"))
    )

    val expectedClaims: Claims = Claims(Some(expectedClaimsSeq))

    "get claimant claims and audit the request" in {
      (ntcConnector
        .claimantClaims(_: TaxCreditsNino)(_: HeaderCarrier, _: ExecutionContext))
        .expects(taxCreditsNino, *, *)
        .returning(Future successful foundClaims)
      (auditConnector
        .sendEvent(_: DataEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future successful Success)

      await(service.legacyClaimantClaims(nino)) shouldBe expectedClaims
    }

    "Handle incorrect date format" in {
      val claimWithAuthTokenAndClaimantDetails   = claim(barcode1, ntcFormattedDate)
      val claimWithNoAuthToken                   = claim(barcode2, ntcFormattedDate)
      val claimWithAuthTokenButNoClaimantDetails = claim(barcode3, ntcFormattedDate)
      val claimAaitingBarcode                    = claim(awaitingBarcode, Some("XXXXXXXXX"))

      val expectedClaimsSeq = Seq(
        claim(barcode1, mobileFormattedDate),
        claim(barcode2, mobileFormattedDate),
        claim(barcode3, mobileFormattedDate),
        claim(awaitingBarcode, None, Some("AWAITING_BARCODE"))
      )

      val expectedClaims: Claims = Claims(Some(expectedClaimsSeq))

      val foundClaims: Claims =
        Claims(
          Some(
            Seq(claimWithAuthTokenAndClaimantDetails,
                claimWithNoAuthToken,
                claimWithAuthTokenButNoClaimantDetails,
                claimAaitingBarcode)
          )
        )
      (ntcConnector
        .claimantClaims(_: TaxCreditsNino)(_: HeaderCarrier, _: ExecutionContext))
        .expects(taxCreditsNino, *, *)
        .returning(Future successful foundClaims)
      (auditConnector
        .sendEvent(_: DataEvent)(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *)
        .returning(Future successful Success)

      await(service.legacyClaimantClaims(nino)) shouldBe expectedClaims
    }
  }
}
