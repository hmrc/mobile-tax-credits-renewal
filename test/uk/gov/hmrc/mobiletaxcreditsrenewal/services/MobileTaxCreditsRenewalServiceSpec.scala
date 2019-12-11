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

import eu.timepit.refined.auto._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpecLike}
import org.slf4j
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Configuration, Logger, LoggerLike, MarkerContext}
import uk.gov.hmrc.api.sandbox.FileResource
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobiletaxcreditsrenewal.connectors.{NtcConnector, TaxCreditsBrokerConnector}
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.types.ModelTypes.JourneyId
import uk.gov.hmrc.mobiletaxcreditsrenewal.stubs.{AuditStub, NtcConnectorStub, TaxCreditsBrokerConnectorStub}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success
import uk.gov.hmrc.play.audit.model.DataEvent

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class MobileTaxCreditsRenewalServiceSpec
    extends WordSpecLike
    with Matchers
    with MockFactory
    with NtcConnectorStub
    with TaxCreditsBrokerConnectorStub
    with AuditStub
    with FileResource {
  implicit val hc:      HeaderCarrier = HeaderCarrier()
  implicit val request: Request[_]    = FakeRequest()

  implicit val ntcConnector:              NtcConnector              = mock[NtcConnector]
  implicit val taxCreditsBrokerConnector: TaxCreditsBrokerConnector = mock[TaxCreditsBrokerConnector]
  implicit val auditConnector:            AuditConnector            = mock[AuditConnector]
  implicit val taxCreditsControl:         TaxCreditsControl         = mock[TaxCreditsControl]
  implicit val configuration:             Configuration             = mock[Configuration]
  implicit val logger:                    TestLoggerLike            = new TestLoggerLike()

  val nino           = Nino("CS700100A")
  val taxCreditsNino = TaxCreditsNino(nino.nino)
  val journeyId: JourneyId = "87144372-6bda-4cc9-87db-1d52fd96498f"
  val service = new MobileTaxCreditsRenewalService(
    ntcConnector,
    taxCreditsBrokerConnector,
    auditConnector,
    configuration,
    taxCreditsControl,
    logger,
    "mobile-tax-credits-renewal")

  "Submit renewal" should {
    val incomeDetails   = IncomeDetails(Some(10), Some(20), Some(30), Some(40), Some(true))
    val certainBenefits = CertainBenefits(receivedBenefits = false, incomeSupport = false, jsa = false, esa = false, pensionCredit = false)
    val otherIncome     = OtherIncome(Some(100), Some(false))
    val renewal = TcrRenewal(
      RenewalData(Some(incomeDetails), Some(incomeDetails), Some(certainBenefits)),
      None,
      Some(otherIncome),
      Some(otherIncome),
      hasChangeOfCircs = false)

    "audit succeess" in {
      stubSubmitRenewals(taxCreditsNino, 200)
      stubAuditSubmitRenewal(nino, renewal)

      await(service.submitRenewal(nino, renewal)) shouldBe 200
    }

    "not audit a failed submission" in {
      stubSubmitRenewalsFailure(taxCreditsNino)

      intercept[RuntimeException] {
        await(service.submitRenewal(nino, renewal)) shouldBe 200
      }
    }
  }

  "renewals" should {
    def whenCurrentSubmissionStateIs(state: String): Unit =
      (taxCreditsControl.toTaxCreditsRenewalsState _).expects().returning(TaxCreditsRenewalsState(state))

    val ntcFormattedDate    = Some("2018-05-30")
    val mobileFormattedDate = Some("30/5/2018")
    val barcode1            = RenewalReference("barcode1")
    val barcode2            = RenewalReference("barcode2")
    val barcode3            = RenewalReference("barcode3")
    val awaitingBarcode     = RenewalReference("000000000000000")
    val claimantDetails     = ClaimantDetails(hasPartner = false, 1, "r", nino.nino, None, availableForCOCAutomation = false, "some-app-id")
    val token               = TcrAuthenticationToken("tcrAuthToken")
    val summaryWithNoClaims = RenewalsSummary("open", Some(Seq.empty))

    def claim(
      barcodeReference: RenewalReference,
      maybeDate:        Option[String],
      claimantDetails:  Option[ClaimantDetails] = None,
      renewalState:     Option[String] = Some("NOT_SUBMITTED")): Claim = {
      val applicant: Applicant = Applicant(nino.nino, "title", "firstForename", None, "surname", None)
      val foundHouseHold = Household(barcodeReference.value, "applicationId", applicant, None, maybeDate, Some("householdEndReason"))
      val foundRenewal   = Renewal(maybeDate, maybeDate, renewalState, maybeDate, maybeDate, claimantDetails)
      Claim(foundHouseHold, foundRenewal)
    }

    def testMultipleClaimsWhenCurrentSubmissionStateIs(state: String): Unit = {
      whenCurrentSubmissionStateIs(state)

      val claimWithAuthTokenAndClaimantDetails   = claim(barcode1, ntcFormattedDate)
      val claimWithNoAuthToken                   = claim(barcode2, ntcFormattedDate)
      val claimWithAuthTokenButNoClaimantDetails = claim(barcode3, ntcFormattedDate)
      val claimAaitingBarcode                    = claim(awaitingBarcode, ntcFormattedDate)

      val foundClaims: Claims =
        Claims(Some(Seq(claimWithAuthTokenAndClaimantDetails, claimWithNoAuthToken, claimWithAuthTokenButNoClaimantDetails, claimAaitingBarcode)))

      val expectedClaimsSeq = Seq(
        claim(barcode1, mobileFormattedDate, Some(claimantDetails)),
        claim(barcode2, mobileFormattedDate),
        claim(barcode3, mobileFormattedDate),
        claim(awaitingBarcode, mobileFormattedDate, None, Some("AWAITING_BARCODE"))
      )

      val expectedClaims: Claims = Claims(Some(expectedClaimsSeq))
      val summary = RenewalsSummary(state, expectedClaims.references)

      stubClaimantClaims(taxCreditsNino, foundClaims)

      stubAuthenticateRenewal(taxCreditsNino, barcode1, token)
      stubClaimantDetails(taxCreditsNino, claimantDetails)

      stubAuthenticateRenewalFailure(taxCreditsNino, barcode2)

      stubAuthenticateRenewal(taxCreditsNino, barcode3, token)
      stubClaimantDetailsFailure(taxCreditsNino)
      stubAuditClaims(nino, summary)

      await(service.renewals(nino, journeyId)) shouldBe summary
    }

    "return no claims details when current renewal sate is closed" in {
      val closedSummary = RenewalsSummary("closed", None)

      whenCurrentSubmissionStateIs("closed")
      stubAuditClaims(nino, closedSummary)

      await(service.renewals(nino, journeyId)) shouldBe closedSummary
    }

    "return multiple claims when the current renewal state is open" in {
      testMultipleClaimsWhenCurrentSubmissionStateIs("open")
    }

    "return multiple claims when the current renewal state is check_status_only" in {
      testMultipleClaimsWhenCurrentSubmissionStateIs("check_status_only")
    }

    "handle no claims found" in {
      whenCurrentSubmissionStateIs("open")

      stubClaimantClaims(taxCreditsNino, Claims(None))
      stubAuditClaims(nino, summaryWithNoClaims)
      await(service.renewals(nino, journeyId)) shouldBe summaryWithNoClaims
    }

    "handle empty claims found" in {
      whenCurrentSubmissionStateIs("open")

      stubClaimantClaims(taxCreditsNino, Claims(Some(Seq.empty)))
      stubAuditClaims(nino, summaryWithNoClaims)

      await(service.renewals(nino, journeyId)) shouldBe summaryWithNoClaims
    }
  }

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

    def claim(barcodeReference: RenewalReference, maybeDate: Option[String], renewalState: Option[String] = Some("NOT_SUBMITTED")): LegacyClaim = {
      val applicant: Applicant = Applicant(nino.nino, "title", "firstForename", None, "surname", None)
      val foundHouseHold = Household(barcodeReference.value, "applicationId", applicant, None, maybeDate, Some("householdEndReason"))
      val foundRenewal   = LegacyRenewal(maybeDate, maybeDate, renewalState, maybeDate, maybeDate)
      LegacyClaim(foundHouseHold, foundRenewal)
    }

    val claimWithAuthTokenAndClaimantDetails   = claim(barcode1, ntcFormattedDate)
    val claimWithNoAuthToken                   = claim(barcode2, ntcFormattedDate)
    val claimWithAuthTokenButNoClaimantDetails = claim(barcode3, ntcFormattedDate)
    val claimAaitingBarcode                    = claim(awaitingBarcode, ntcFormattedDate)

    val foundClaims: LegacyClaims =
      LegacyClaims(Some(Seq(claimWithAuthTokenAndClaimantDetails, claimWithNoAuthToken, claimWithAuthTokenButNoClaimantDetails, claimAaitingBarcode)))

    val expectedClaimsSeq = Seq(
      claim(barcode1, mobileFormattedDate),
      claim(barcode2, mobileFormattedDate),
      claim(barcode3, mobileFormattedDate),
      claim(awaitingBarcode, mobileFormattedDate, Some("AWAITING_BARCODE"))
    )

    val expectedClaims: LegacyClaims = LegacyClaims(Some(expectedClaimsSeq))

    "get claimant claims and audit the request" in {
      (ntcConnector
        .legacyClaimantClaims(_: TaxCreditsNino)(_: HeaderCarrier, _: ExecutionContext))
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

      val expectedClaims: LegacyClaims = LegacyClaims(Some(expectedClaimsSeq))

      val foundClaims: LegacyClaims =
        LegacyClaims(
          Some(Seq(claimWithAuthTokenAndClaimantDetails, claimWithNoAuthToken, claimWithAuthTokenButNoClaimantDetails, claimAaitingBarcode)))
      (ntcConnector
        .legacyClaimantClaims(_: TaxCreditsNino)(_: HeaderCarrier, _: ExecutionContext))
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

class TestLoggerLike extends LoggerLike {
  override val logger: slf4j.Logger = Logger("test").logger

  var infoMessages: Seq[String] = Seq()
  var warnMessages: Seq[String] = Seq()

  override def info(message: => String)(implicit m: MarkerContext): scala.Unit =
    infoMessages = infoMessages ++ Seq(message)

  override def warn(message: => String)(implicit m: MarkerContext): scala.Unit =
    warnMessages = warnMessages ++ Seq(message)

  def warnMessageWaslogged(message: String): Boolean =
    warnMessages.contains(message)

  def infoMessageWasLogged(message: String): Boolean =
    infoMessages.contains(message)

  def clearMessages(): Unit = {
    infoMessages = Seq()
    warnMessages = Seq()
  }
}
