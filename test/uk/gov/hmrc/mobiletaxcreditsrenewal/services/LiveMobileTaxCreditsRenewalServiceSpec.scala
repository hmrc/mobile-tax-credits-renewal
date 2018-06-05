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

import org.scalamock.scalatest.MockFactory
import org.slf4j
import play.api.{Configuration, Logger, LoggerLike}
import uk.gov.hmrc.api.sandbox.FileResource
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobiletaxcreditsrenewal.connectors.NtcConnector
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._
import uk.gov.hmrc.mobiletaxcreditsrenewal.stubs.{AuditStub, NtcConnectorStub}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global

class LiveMobileTaxCreditsRenewalServiceSpec
  extends UnitSpec with MockFactory with WithFakeApplication with NtcConnectorStub with AuditStub with FileResource{
  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val ntcConnector: NtcConnector = mock[NtcConnector]
  implicit val auditConnector: AuditConnector = mock[AuditConnector]
  implicit val taxCreditsControl: TaxCreditsControl = mock[TaxCreditsControl]
  implicit val configuration: Configuration = fakeApplication.injector.instanceOf[Configuration]
  implicit val logger: TestLoggerLike = new TestLoggerLike()

  val nino = Nino("CS700100A")
  val taxCreditsNino = TaxCreditsNino(nino.nino)

  val service = new LiveMobileTaxCreditsRenewalService(ntcConnector, auditConnector, configuration, taxCreditsControl, logger)

  "Submit renewal" should{
    val incomeDetails = IncomeDetails(Some(10), Some(20), Some(30), Some(40), Some(true))
    val certainBenefits = CertainBenefits(receivedBenefits = false, incomeSupport = false, jsa = false, esa = false, pensionCredit = false)
    val otherIncome = OtherIncome(Some(100), Some(false))
    val renewal = TcrRenewal(RenewalData(Some(incomeDetails), Some(incomeDetails),
      Some(certainBenefits)), None, Some(otherIncome), Some(otherIncome), hasChangeOfCircs = false)

    "audit succeess" in {
      stubSubmitRenewals(taxCreditsNino, 200)
      stubAuditSubmitRenewal(nino)

      await(service.submitRenewal(nino, renewal)) shouldBe 200
    }

    "not audit a failed submission" in {
      stubSubmitRenewalsFailure(taxCreditsNino)

      intercept[RuntimeException]{
        await(service.submitRenewal(nino, renewal)) shouldBe 200
      }
    }
  }

  "renewals" should {
    def whenCurrentSubmissionStateIs(state: String): Unit =
      (taxCreditsControl.toTaxCreditsRenewalsState _).expects().returning(TaxCreditsRenewalsState(state))

    val ntcFormattedDate = Some("2018-05-30")
    val mobileFormattedDate = Some("30/05/2018")
    val barcode1 = RenewalReference("barcode1")
    val barcode2 = RenewalReference("barcode2")
    val barcode3 = RenewalReference("barcode3")
    val awaitingBarcode = RenewalReference("000000000000000")
    val claimantDetails = ClaimantDetails(hasPartner = false, 1, "r", nino.nino, None, availableForCOCAutomation = false, "some-app-id")
    val token = TcrAuthenticationToken("tcrAuthToken")

    def claim(barcodeReference: RenewalReference,
              maybeDate: Option[String],
              claimantDetails: Option[ClaimantDetails] = None,
              renewalState: Option[String] = Some("NOT_SUBMITTED")): Claim = {
      val applicant: Applicant = Applicant(nino.nino, "title", "firstForename", None, "surname")
      val foundHouseHold = Household(barcodeReference.value, "applicationId", applicant, None, maybeDate, Some("householdEndReason"))
      val foundRenewal = Renewal(maybeDate, maybeDate, renewalState, maybeDate, maybeDate, claimantDetails)
      Claim(foundHouseHold, foundRenewal)
    }

    def testMultipleClaimsWhenCurrentSubmissionStateIs(state: String): Unit = {
      whenCurrentSubmissionStateIs(state)

      val claimWithAuthTokenAndClaimantDetails = claim(barcode1, ntcFormattedDate)
      val claimWithNoAuthToken = claim(barcode2, ntcFormattedDate)
      val claimWithAuthTokenButNoClaimantDetails = claim(barcode3, ntcFormattedDate)
      val claimAaitingBarcode = claim(awaitingBarcode, ntcFormattedDate)

      val foundClaims: Claims =
        Claims(Some(Seq(
          claimWithAuthTokenAndClaimantDetails,
          claimWithNoAuthToken,
          claimWithAuthTokenButNoClaimantDetails,
          claimAaitingBarcode)))

      val expectedClaims: Claims = Claims(Some(Seq(
        claim(barcode1, mobileFormattedDate, Some(claimantDetails)),
        claim(barcode2, mobileFormattedDate),
        claim(barcode3, mobileFormattedDate),
        claim(awaitingBarcode, mobileFormattedDate, None, Some("AWAITING_BARCODE")))))

      stubClaimantClaims(taxCreditsNino, foundClaims)
      stubAuditClaimantClaims(nino)

      stubAuthenticateRenewal(taxCreditsNino, barcode1, token)
      stubAuditAuthenticateRenewal(nino)
      stubClaimantDetails(taxCreditsNino, claimantDetails)
      stubAuditClaimantDetails(nino)

      stubAuthenticateRenewalFailure(taxCreditsNino, barcode2)
      stubAuditAuthenticateRenewal(nino)

      stubAuthenticateRenewal(taxCreditsNino, barcode3, token)
      stubAuditAuthenticateRenewal(nino)
      stubClaimantDetailsFailure(taxCreditsNino)
      stubAuditClaimantDetails(nino)

      await(service.renewals(nino, None)) shouldBe RenewalsSummary(state, expectedClaims.references)
    }

    "return no claims details when current renewal sate is closed" in {
      whenCurrentSubmissionStateIs("closed")
      await(service.renewals(nino, None)) shouldBe RenewalsSummary("closed", None)
    }

    "return no claims details when current renewal sate is shuttered" in {
      whenCurrentSubmissionStateIs("shuttered")
      await(service.renewals(nino, None)) shouldBe RenewalsSummary("shuttered", None)
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
      stubAuditClaimantClaims(nino)

      await(service.renewals(nino, None)) shouldBe RenewalsSummary("open", Some(Seq.empty))
    }

    "handle empty claims found" in {
      whenCurrentSubmissionStateIs("open")

      stubClaimantClaims(taxCreditsNino, Claims(Some(Seq.empty)))
      stubAuditClaimantClaims(nino)

      await(service.renewals(nino, None)) shouldBe RenewalsSummary("open", Some(Seq.empty))
    }
  }
}

class TestLoggerLike extends LoggerLike {
  override val logger: slf4j.Logger = Logger("test").logger

  var infoMessages: Seq[String] = Seq()
  var warnMessages: Seq[String] = Seq()

  override def info(message: => String): scala.Unit = {
    infoMessages = infoMessages ++ Seq(message)
  }

  override def warn(message: => String): scala.Unit = {
    warnMessages = warnMessages ++ Seq(message)
  }

  def warnMessageWaslogged(message: String): Boolean = {
    warnMessages.contains(message)
  }

  def infoMessageWasLogged(message: String): Boolean = {
    infoMessages.contains(message)
  }

  def clearMessages(): Unit = {
    infoMessages = Seq()
    warnMessages = Seq()
  }
}

