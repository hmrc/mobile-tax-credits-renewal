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

package uk.gov.hmrc.mobiletaxcreditsrenewal.controllers

import eu.timepit.refined.auto._
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.Json.toJson
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.ConfidenceLevel._
import uk.gov.hmrc.auth.core.syntax.retrieved._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobiletaxcreditsrenewal.connectors.ShutteringConnector
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.types.ModelTypes.JourneyId
import uk.gov.hmrc.mobiletaxcreditsrenewal.services.MobileTaxCreditsRenewalService
import uk.gov.hmrc.mobiletaxcreditsrenewal.stubs.{AuthorisationStub, MobileTaxCreditsRenewalServiceStub, ShutteringMock}

import scala.concurrent.ExecutionContext.Implicits.global

class LiveMobileTaxCreditsRenewalControllerSpec
    extends AnyWordSpecLike
    with Matchers
    with MockFactory
    with AuthorisationStub
    with MobileTaxCreditsRenewalServiceStub
    with ShutteringMock {
  implicit val authConnector:       AuthConnector                  = mock[AuthConnector]
  implicit val mockControlConfig:   TaxCreditsControl              = mock[TaxCreditsControl]
  implicit val service:             MobileTaxCreditsRenewalService = mock[MobileTaxCreditsRenewalService]
  implicit val shutteringConnector: ShutteringConnector            = mock[ShutteringConnector]

  private val nino          = Nino("CS700100A")
  private val nino2         = Nino("CS700101A")
  private val incorrectNino = Nino("SC100700A")
  private val journeyId: JourneyId = "87144372-6bda-4cc9-87db-1d52fd96498f"
  private val tcrAuthToken = TcrAuthenticationToken("some-auth-token")

  private val controller =
    new LiveMobileTaxCreditsRenewalController(authConnector,
                                              service,
                                              mockControlConfig,
                                              L200.level,
                                              stubControllerComponents(),
                                              shutteringConnector)

  private val acceptHeader: (String, String) = "Accept" -> "application/vnd.hmrc.1.0+json"

  val renewalReference = RenewalReference("111111111111111")

  lazy val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    .withSession(
      "AuthToken" -> "Some Header"
    )
    .withHeaders(
      acceptHeader,
      "Authorization" -> "Some Header"
    )

  lazy val requestInvalidHeaders: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    .withSession(
      "AuthToken" -> "Some Header"
    )
    .withHeaders(
      "Authorization" -> "Some Header"
    )

  "fullClaimantDetails" should {
    val applicant       = Applicant(nino.nino, "MR", "BOB", None, "ROBSON", None)
    val applicant2      = Applicant(nino2.nino, "MRS", "BOBETTE", None, "ROBSON", None)
    val renewal         = Renewal(None, None, None, None, None, None)
    val renewalFormType = "renewalFormType"

    "return 401 if unauthorised" in {
      stubAuthorisationUnauthorised()
      val result = controller.fullClaimantDetails(nino, journeyId)(fakeRequest)
      status(result) shouldBe 401
    }

    "return Low CL if low confidence level" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L50)
      val result = controller.fullClaimantDetails(nino, journeyId)(fakeRequest)
      status(result) shouldBe 403
    }

    "return ninoNotFound if no nino found" in {
      stubAuthorisationGrantAccess(None and L200)
      val result = controller.fullClaimantDetails(nino, journeyId)(fakeRequest)
      status(result) shouldBe 403
    }

    "return noMatchingNinoFound if different nino returned found" in {
      stubAuthorisationGrantAccess(Some(nino2.nino) and L200)
      val result = controller.fullClaimantDetails(nino, journeyId)(fakeRequest)
      status(result) shouldBe 403
    }

    "return invalidAcceptHeader if invalid headers sent" in {
      val result = controller.fullClaimantDetails(nino, journeyId)(requestInvalidHeaders)
      status(result) shouldBe 406
    }

    "return details with the renewalFormType set" in {
      val household = Household(renewalReference.value, "applicationId", applicant, None, None, None)
      val expectedClaimDetails =
        Claim(household, Renewal(None, None, None, None, None, Some(renewalFormType)))

      mockShutteringResponse(false)
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      stubServiceClaimantClaims(Claims(Some(Seq(Claim(household, renewal)))), nino)
      stubServiceAuthenticateRenewal(tcrAuthToken, nino, renewalReference)
      stubEmployedEarningsRti(None, nino)
      stubClaimantDetailsResponse(ClaimantDetails(hasPartner = false,
                                                  1,
                                                  renewalFormType,
                                                  incorrectNino.value,
                                                  None,
                                                  availableForCOCAutomation = false,
                                                  "some-app-id"),
                                  nino)

      val result = controller.fullClaimantDetails(nino, journeyId)(fakeRequest)
      status(result)        shouldBe 200
      contentAsJson(result) shouldBe toJson(Claims(Some(Seq(expectedClaimDetails))))
    }

    "return details with employed earnings RTI populated for applicant1 and not applicant2 - logged in at applicant 1" in {
      val household = Household(
        renewalReference.value,
        "applicationId",
        applicant.copy(previousYearRtiEmployedEarnings = Some(20000.0)),
        Some(applicant2.copy(previousYearRtiEmployedEarnings = None)),
        None,
        None
      )
      val expectedClaimDetails =
        Claim(household, Renewal(None, None, None, None, None, Some(renewalFormType)))

      mockShutteringResponse(false)
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      stubServiceClaimantClaims(Claims(Some(Seq(Claim(household, renewal)))), nino)
      stubServiceAuthenticateRenewal(tcrAuthToken, nino, renewalReference)
      stubEmployedEarningsRti(Some(EmployedEarningsRti(Some(20000.0), None)), nino)
      stubClaimantDetailsResponse(ClaimantDetails(hasPartner = false,
                                                  1,
                                                  renewalFormType,
                                                  incorrectNino.value,
                                                  None,
                                                  availableForCOCAutomation = false,
                                                  "some-app-id"),
                                  nino)

      val result = controller.fullClaimantDetails(nino, journeyId)(fakeRequest)
      status(result)        shouldBe 200
      contentAsJson(result) shouldBe toJson(Claims(Some(Seq(expectedClaimDetails))))
    }

    "return details with employed earnings RTI populated for applicant2 and not applicant1 - logged in at applicant 1" in {
      val household = Household(
        renewalReference.value,
        "applicationId",
        applicant.copy(previousYearRtiEmployedEarnings = None),
        Some(applicant2.copy(previousYearRtiEmployedEarnings = Some(22000.0))),
        None,
        None
      )
      val expectedClaimDetails =
        Claim(household, Renewal(None, None, None, None, None, Some(renewalFormType)))

      mockShutteringResponse(false)
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      stubServiceClaimantClaims(Claims(Some(Seq(Claim(household, renewal)))), nino)
      stubServiceAuthenticateRenewal(tcrAuthToken, nino, renewalReference)
      stubEmployedEarningsRti(Some(EmployedEarningsRti(None, Some(22000.0))), nino)
      stubClaimantDetailsResponse(ClaimantDetails(hasPartner = false,
                                                  1,
                                                  renewalFormType,
                                                  incorrectNino.value,
                                                  None,
                                                  availableForCOCAutomation = false,
                                                  "some-app-id"),
                                  nino)

      val result = controller.fullClaimantDetails(nino, journeyId)(fakeRequest)
      status(result)        shouldBe 200
      contentAsJson(result) shouldBe toJson(Claims(Some(Seq(expectedClaimDetails))))
    }

    "return details with employed earnings RTI populated both applicant1 and applicant2 - logged in at applicant 1" in {
      val household = Household(
        renewalReference.value,
        "applicationId",
        applicant.copy(previousYearRtiEmployedEarnings = Some(20000.0)),
        Some(applicant2.copy(previousYearRtiEmployedEarnings = Some(22000.0))),
        None,
        None
      )
      val expectedClaimDetails =
        Claim(household, Renewal(None, None, None, None, None, Some(renewalFormType)))

      mockShutteringResponse(false)
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      stubServiceClaimantClaims(Claims(Some(Seq(Claim(household, renewal)))), nino)
      stubServiceAuthenticateRenewal(tcrAuthToken, nino, renewalReference)
      stubEmployedEarningsRti(Some(EmployedEarningsRti(Some(20000.0), Some(22000.0))), nino)
      stubClaimantDetailsResponse(ClaimantDetails(hasPartner = false,
                                                  1,
                                                  renewalFormType,
                                                  incorrectNino.value,
                                                  None,
                                                  availableForCOCAutomation = false,
                                                  "some-app-id"),
                                  nino)

      val result = controller.fullClaimantDetails(nino, journeyId)(fakeRequest)
      status(result)        shouldBe 200
      contentAsJson(result) shouldBe toJson(Claims(Some(Seq(expectedClaimDetails))))
    }

    "return details with employed earnings RTI populated for applicant1 and not applicant2 - logged in at applicant 2" in {
      val household = Household(
        renewalReference.value,
        "applicationId",
        applicant.copy(previousYearRtiEmployedEarnings = Some(20000.0)),
        Some(applicant2.copy(previousYearRtiEmployedEarnings = None)),
        None,
        None
      )
      val expectedClaimDetails =
        Claim(household, Renewal(None, None, None, None, None, Some(renewalFormType)))

      mockShutteringResponse(false)
      stubAuthorisationGrantAccess(Some(nino2.nino) and L200)
      stubServiceClaimantClaims(Claims(Some(Seq(Claim(household, renewal)))), nino2)
      stubServiceAuthenticateRenewal(tcrAuthToken, nino2, renewalReference)
      stubEmployedEarningsRti(Some(EmployedEarningsRti(None, Some(20000.0))), nino2)
      stubClaimantDetailsResponse(ClaimantDetails(hasPartner = false,
                                                  1,
                                                  renewalFormType,
                                                  incorrectNino.value,
                                                  None,
                                                  availableForCOCAutomation = false,
                                                  "some-app-id"),
                                  nino2)

      val result = controller.fullClaimantDetails(nino2, journeyId)(fakeRequest)
      status(result)        shouldBe 200
      contentAsJson(result) shouldBe toJson(Claims(Some(Seq(expectedClaimDetails))))
    }

    "return details with employed earnings RTI populated for applicant2 and not applicant1 - logged in at applicant 2" in {
      val household = Household(
        renewalReference.value,
        "applicationId",
        applicant.copy(previousYearRtiEmployedEarnings = None),
        Some(applicant2.copy(previousYearRtiEmployedEarnings = Some(22000.0))),
        None,
        None
      )
      val expectedClaimDetails =
        Claim(household, Renewal(None, None, None, None, None, Some(renewalFormType)))

      mockShutteringResponse(false)
      stubAuthorisationGrantAccess(Some(nino2.nino) and L200)
      stubServiceClaimantClaims(Claims(Some(Seq(Claim(household, renewal)))), nino2)
      stubServiceAuthenticateRenewal(tcrAuthToken, nino2, renewalReference)
      stubEmployedEarningsRti(Some(EmployedEarningsRti(Some(22000.0), None)), nino2)
      stubClaimantDetailsResponse(ClaimantDetails(hasPartner = false,
                                                  1,
                                                  renewalFormType,
                                                  incorrectNino.value,
                                                  None,
                                                  availableForCOCAutomation = false,
                                                  "some-app-id"),
                                  nino2)

      val result = controller.fullClaimantDetails(nino2, journeyId)(fakeRequest)
      status(result)        shouldBe 200
      contentAsJson(result) shouldBe toJson(Claims(Some(Seq(expectedClaimDetails))))
    }

    "return details with employed earnings RTI populated both applicant1 and applicant2 - logged in at applicant 2" in {
      val household = Household(
        renewalReference.value,
        "applicationId",
        applicant.copy(previousYearRtiEmployedEarnings = Some(20000.0)),
        Some(applicant2.copy(previousYearRtiEmployedEarnings = Some(22000.0))),
        None,
        None
      )
      val expectedClaimDetails =
        Claim(household, Renewal(None, None, None, None, None, Some(renewalFormType)))

      mockShutteringResponse(false)
      stubAuthorisationGrantAccess(Some(nino2.nino) and L200)
      stubServiceClaimantClaims(Claims(Some(Seq(Claim(household, renewal)))), nino2)
      stubServiceAuthenticateRenewal(tcrAuthToken, nino2, renewalReference)
      stubEmployedEarningsRti(Some(EmployedEarningsRti(Some(22000.0), Some(20000.0))), nino2)
      stubClaimantDetailsResponse(ClaimantDetails(hasPartner = false,
                                                  1,
                                                  renewalFormType,
                                                  incorrectNino.value,
                                                  None,
                                                  availableForCOCAutomation = false,
                                                  "some-app-id"),
                                  nino2)

      val result = controller.fullClaimantDetails(nino2, journeyId)(fakeRequest)
      status(result)        shouldBe 200
      contentAsJson(result) shouldBe toJson(Claims(Some(Seq(expectedClaimDetails))))
    }

    "return details with no employed earnings RTI for either applicant" in {
      val household = Household(
        renewalReference.value,
        "applicationId",
        applicant.copy(previousYearRtiEmployedEarnings = None),
        Some(applicant.copy(previousYearRtiEmployedEarnings = None)),
        None,
        None
      )
      val expectedClaimDetails =
        Claim(household, Renewal(None, None, None, None, None, Some(renewalFormType)))

      mockShutteringResponse(false)
      stubAuthorisationGrantAccess(Some(nino2.nino) and L200)
      stubServiceClaimantClaims(Claims(Some(Seq(Claim(household, renewal)))), nino2)
      stubServiceAuthenticateRenewal(tcrAuthToken, nino2, renewalReference)
      stubEmployedEarningsRti(None, nino2)
      stubClaimantDetailsResponse(ClaimantDetails(hasPartner = false,
                                                  1,
                                                  renewalFormType,
                                                  incorrectNino.value,
                                                  None,
                                                  availableForCOCAutomation = false,
                                                  "some-app-id"),
                                  nino2)

      val result = controller.fullClaimantDetails(nino2, journeyId)(fakeRequest)
      status(result)        shouldBe 200
      contentAsJson(result) shouldBe toJson(Claims(Some(Seq(expectedClaimDetails))))
    }

    "return 521 when shuttered" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      mockShutteringResponse(true)

      val result = controller.fullClaimantDetails(nino, journeyId)(fakeRequest)
      status(result) shouldBe 521
      val jsonBody = contentAsJson(result)
      (jsonBody \ "shuttered").as[Boolean] shouldBe true
      (jsonBody \ "title").as[String]      shouldBe "Shuttered"
      (jsonBody \ "message").as[String]    shouldBe "Tax Credits Renewal is currently not available"
    }
  }

}
