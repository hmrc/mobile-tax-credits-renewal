/*
 * Copyright 2020 HM Revenue & Customs
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
import org.apache.commons.codec.binary.Base64.encodeBase64
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpecLike}
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import play.api.LoggerLike
import play.api.libs.json.Json.{parse, toJson}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.ConfidenceLevel._
import uk.gov.hmrc.auth.core.syntax.retrieved._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.mobiletaxcreditsrenewal.connectors.ShutteringConnector
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.types.ModelTypes.JourneyId
import uk.gov.hmrc.mobiletaxcreditsrenewal.services.MobileTaxCreditsRenewalService
import uk.gov.hmrc.mobiletaxcreditsrenewal.stubs.{AuthorisationStub, MobileTaxCreditsRenewalServiceStub, ShutteringMock}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class LiveLegacyMobileTaxCreditsRenewalControllerSpec
    extends WordSpecLike
    with Matchers
    with MockFactory
    with AuthorisationStub
    with MobileTaxCreditsRenewalServiceStub
    with ClaimsJson
    with ShutteringMock {
  implicit val authConnector:       AuthConnector                  = mock[AuthConnector]
  implicit val mockControlConfig:   TaxCreditsControl              = mock[TaxCreditsControl]
  implicit val service:             MobileTaxCreditsRenewalService = mock[MobileTaxCreditsRenewalService]
  implicit val shutteringConnector: ShutteringConnector            = mock[ShutteringConnector]

  private val logger = new LoggerLike {
    override val logger: Logger = getLogger("LiveMobileTaxCreditsRenewalControllerSpec")
  }

  private val nino          = Nino("CS700100A")
  private val nino2         = Nino("CS700101A")
  private val incorrectNino = Nino("SC100700A")
  private val journeyId: JourneyId = "87144372-6bda-4cc9-87db-1d52fd96498f"
  private val tcrAuthToken = TcrAuthenticationToken("some-auth-token")
  private val notFoundException: Future[Nothing] = Future failed new NotFoundException("cant find it")
  private val forbidden:         JsValue         = parse("""{"code":"FORBIDDEN","message":"Forbidden"}""")

  private val controller =
    new LiveLegacyMobileTaxCreditsRenewalController(authConnector,
                                                    logger,
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

  def basicAuthString(encodedAuth: String): String = "Basic " + encodedAuth

  def encodedAuth(
    nino:                Nino,
    tcrRenewalReference: RenewalReference
  ): String =
    new String(encodeBase64(s"${nino.value}:${tcrRenewalReference.value}".getBytes))

  def emptyRequestWithAcceptHeaderAndAuthHeader(
    renewalsRef: RenewalReference,
    nino:        Nino
  ): FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest().withHeaders(acceptHeader, HeaderKeys.tcrAuthToken -> basicAuthString(encodedAuth(nino, renewalsRef)))

  "getRenewalAuthentication" should {
    "process the authentication successfully" in {
      mockShutteringResponse(false)
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      stubAuthRenewalResponse(Some(tcrAuthToken), nino, renewalReference)

      val result = controller.getRenewalAuthentication(nino, renewalReference, journeyId).apply(fakeRequest)
      status(result)        shouldBe 200
      contentAsJson(result) shouldBe toJson(tcrAuthToken)
    }

    "return 403 when the nino in the request does not match the authority nino" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      status(controller.getRenewalAuthentication(incorrectNino, renewalReference, journeyId).apply(fakeRequest)) shouldBe 403
    }

    "process the authentication successful when journeyId is supplied" in {
      mockShutteringResponse(false)
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      stubAuthRenewalResponse(Some(tcrAuthToken), nino, renewalReference)

      val result = controller.getRenewalAuthentication(nino, renewalReference, journeyId).apply(fakeRequest)
      status(result)        shouldBe 200
      contentAsJson(result) shouldBe toJson(tcrAuthToken)
    }

    "return Http 404 (NotFound) response when hod returns either a (BadRequest) 400 or (NotFound) 404 status" in {
      mockShutteringResponse(false)
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      (service
        .authenticateRenewal(_: Nino, _: RenewalReference)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, renewalReference, *, *)
        .returns(notFoundException)

      status(controller.getRenewalAuthentication(nino, renewalReference, journeyId).apply(fakeRequest)) shouldBe 404
    }

    "return forbidden when authority record does not contain a NINO" in {
      stubAuthorisationGrantAccess(None and L200)

      val result = controller.getRenewalAuthentication(nino, renewalReference, journeyId).apply(fakeRequest)
      status(result)        shouldBe 403
      contentAsJson(result) shouldBe forbidden
    }

    "return forbidden when authority record has a low CL" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L100)

      val result = controller.getRenewalAuthentication(nino, renewalReference, journeyId).apply(fakeRequest)
      status(result)        shouldBe 403
      contentAsJson(result) shouldBe forbidden
    }

    "return status code 406 when the headers are invalid" in {
      status(controller.getRenewalAuthentication(nino, renewalReference, journeyId).apply(requestInvalidHeaders)) shouldBe 406
    }

    "return 521 when shuttered" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      mockShutteringResponse(true)

      val result = controller.getRenewalAuthentication(nino, renewalReference, journeyId).apply(fakeRequest)
      status(result) shouldBe 521
      val jsonBody = contentAsJson(result)
      (jsonBody \ "shuttered").as[Boolean] shouldBe true
      (jsonBody \ "title").as[String]      shouldBe "Shuttered"
      (jsonBody \ "message").as[String]    shouldBe "Tax Credits Renewal is currently not available"
    }
  }

  "claimantDetails" should {
    "return claimant details successfully" in {
      mockShutteringResponse(false)
      val claimantDetails =
        ClaimantDetails(hasPartner = false, 1, "r", nino.nino, None, availableForCOCAutomation = false, "some-app-id")
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      stubClaimantDetailsResponse(claimantDetails, nino)

      val result = controller
        .claimantDetails(nino, journeyId)
        .apply(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, nino))

      status(result)                  shouldBe 200
      contentAsJson(result)           shouldBe toJson(claimantDetails.copy(mainApplicantNino = "true"))
      header("Cache-Control", result) shouldBe None
    }

    "return claimant details successfully when NINO does not match mainApplicantNino" in {
      mockShutteringResponse(false)
      val claimantDetails = ClaimantDetails(hasPartner = false,
                                            1,
                                            "r",
                                            incorrectNino.value,
                                            None,
                                            availableForCOCAutomation = false,
                                            "some-app-id")
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      stubClaimantDetailsResponse(claimantDetails, nino)

      val result =
        controller.claimantDetails(nino, journeyId)(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, nino))
      status(result)                  shouldBe 200
      contentAsJson(result)           shouldBe toJson(claimantDetails.copy(mainApplicantNino = "false"))
      header("Cache-Control", result) shouldBe None
    }

    "return claimant claims successfully" in {
      mockShutteringResponse(false)
      val matchedClaims: LegacyClaims = Json.parse(matchedClaimsJson).as[LegacyClaims]
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      stubClaimantClaimsResponse(matchedClaims, nino)

      val result = controller.claimantDetails(nino, journeyId, Some("claims"))(fakeRequest)
      status(result)                  shouldBe 200
      contentAsJson(result)           shouldBe Json.parse(matchedClaimsJson)
      header("Cache-Control", result) shouldBe None
    }

    "return 404 when no claims matched the supplied nino" in {
      mockShutteringResponse(false)
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      (service
        .legacyClaimantClaims(_: Nino)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, *, *)
        .returns(notFoundException)

      status(controller.claimantDetails(nino, journeyId, Some("claims"))(fakeRequest)) shouldBe 404
    }

    "return 403 when no tcrAuthHeader is supplied to claimant details API" in {
      mockShutteringResponse(false)
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)

      val result = controller.claimantDetails(nino, journeyId, None)(fakeRequest)
      status(result) shouldBe 403
      contentAsJson(result) shouldBe Json.parse(
        """{"code":"NTC_RENEWAL_AUTH_ERROR","message":"No auth header supplied in http request"}"""
      )
    }

    "return 403 when tcrAuthHeader is supplied to claims API" in {
      mockShutteringResponse(false)
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)

      val result = controller.claimantDetails(nino, journeyId, Some("claims"))(
        emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, nino)
      )
      status(result) shouldBe 403
      contentAsJson(result) shouldBe Json.parse(
        """{"code":"NTC_RENEWAL_AUTH_ERROR","message":"Auth header is not required in the request"}"""
      )
    }

    "return 403 when the nino in the request does not match the authority nino" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)

      status(
        controller.claimantDetails(incorrectNino, journeyId)(
          emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, incorrectNino)
        )
      ) shouldBe 403
    }

    "return the claimant details successfully when journeyId is supplied" in {
      mockShutteringResponse(false)
      val claimantDetails =
        ClaimantDetails(hasPartner = false, 1, "r", nino.nino, None, availableForCOCAutomation = false, "some-app-id")
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      stubClaimantDetailsResponse(claimantDetails, nino)

      val result =
        controller.claimantDetails(nino, journeyId)(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, nino))
      status(result)        shouldBe 200
      contentAsJson(result) shouldBe toJson(claimantDetails.copy(mainApplicantNino = "true"))
    }

    "return forbidden when authority record does not contain a NINO" in {
      stubAuthorisationGrantAccess(None and L200)

      val result =
        controller.claimantDetails(nino, journeyId)(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, nino))
      status(result)        shouldBe 403
      contentAsJson(result) shouldBe forbidden
    }

    "return forbidden when authority record has a low CL" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L50)

      val result =
        controller.claimantDetails(nino, journeyId)(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, nino))
      status(result)        shouldBe 403
      contentAsJson(result) shouldBe forbidden
    }

    "return 403 response when the tcr auth header is not supplied in the request" in {
      mockShutteringResponse(false)
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)

      val result = controller.claimantDetails(nino, journeyId)(fakeRequest)
      status(result)        shouldBe 403
      contentAsJson(result) shouldBe toJson(ErrorNoAuthToken)
    }

    "return status code 406 when the Accept header is invalid" in {
      status(controller.claimantDetails(nino, journeyId)(requestInvalidHeaders)) shouldBe 406
    }

    "return 521 when shuttered" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      mockShutteringResponse(true)

      val result = controller.claimantDetails(nino, journeyId)(fakeRequest)
      status(result) shouldBe 521
      val jsonBody = contentAsJson(result)
      (jsonBody \ "shuttered").as[Boolean] shouldBe true
      (jsonBody \ "title").as[String]      shouldBe "Shuttered"
      (jsonBody \ "message").as[String]    shouldBe "Tax Credits Renewal is currently not available"
    }
  }

  "fullClaimantDetails" should {
    val applicant       = Applicant(nino.nino, "MR", "BOB", None, "ROBSON", None)
    val applicant2      = Applicant(nino2.nino, "MRS", "BOBETTE", None, "ROBSON", None)
    val renewal         = LegacyRenewal(None, None, None, None, None, None)
    val renewalFormType = "renewalFormType"

    "return details with the renewalFormType set" in {
      val household = Household(renewalReference.value, "applicationId", applicant, None, None, None)
      val expectedClaimDetails =
        LegacyClaim(household, LegacyRenewal(None, None, None, None, None, Some(renewalFormType)))

      mockShutteringResponse(false)
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      stubServiceClaimantClaims(LegacyClaims(Some(Seq(LegacyClaim(household, renewal)))), nino)
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
      contentAsJson(result) shouldBe toJson(LegacyClaims(Some(Seq(expectedClaimDetails))))
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
        LegacyClaim(household, LegacyRenewal(None, None, None, None, None, Some(renewalFormType)))

      mockShutteringResponse(false)
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      stubServiceClaimantClaims(LegacyClaims(Some(Seq(LegacyClaim(household, renewal)))), nino)
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
      contentAsJson(result) shouldBe toJson(LegacyClaims(Some(Seq(expectedClaimDetails))))
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
        LegacyClaim(household, LegacyRenewal(None, None, None, None, None, Some(renewalFormType)))

      mockShutteringResponse(false)
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      stubServiceClaimantClaims(LegacyClaims(Some(Seq(LegacyClaim(household, renewal)))), nino)
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
      contentAsJson(result) shouldBe toJson(LegacyClaims(Some(Seq(expectedClaimDetails))))
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
        LegacyClaim(household, LegacyRenewal(None, None, None, None, None, Some(renewalFormType)))

      mockShutteringResponse(false)
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      stubServiceClaimantClaims(LegacyClaims(Some(Seq(LegacyClaim(household, renewal)))), nino)
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
      contentAsJson(result) shouldBe toJson(LegacyClaims(Some(Seq(expectedClaimDetails))))
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
        LegacyClaim(household, LegacyRenewal(None, None, None, None, None, Some(renewalFormType)))

      mockShutteringResponse(false)
      stubAuthorisationGrantAccess(Some(nino2.nino) and L200)
      stubServiceClaimantClaims(LegacyClaims(Some(Seq(LegacyClaim(household, renewal)))), nino2)
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
      contentAsJson(result) shouldBe toJson(LegacyClaims(Some(Seq(expectedClaimDetails))))
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
        LegacyClaim(household, LegacyRenewal(None, None, None, None, None, Some(renewalFormType)))

      mockShutteringResponse(false)
      stubAuthorisationGrantAccess(Some(nino2.nino) and L200)
      stubServiceClaimantClaims(LegacyClaims(Some(Seq(LegacyClaim(household, renewal)))), nino2)
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
      contentAsJson(result) shouldBe toJson(LegacyClaims(Some(Seq(expectedClaimDetails))))
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
        LegacyClaim(household, LegacyRenewal(None, None, None, None, None, Some(renewalFormType)))

      mockShutteringResponse(false)
      stubAuthorisationGrantAccess(Some(nino2.nino) and L200)
      stubServiceClaimantClaims(LegacyClaims(Some(Seq(LegacyClaim(household, renewal)))), nino2)
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
      contentAsJson(result) shouldBe toJson(LegacyClaims(Some(Seq(expectedClaimDetails))))
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
        LegacyClaim(household, LegacyRenewal(None, None, None, None, None, Some(renewalFormType)))

      mockShutteringResponse(false)
      stubAuthorisationGrantAccess(Some(nino2.nino) and L200)
      stubServiceClaimantClaims(LegacyClaims(Some(Seq(LegacyClaim(household, renewal)))), nino2)
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
      contentAsJson(result) shouldBe toJson(LegacyClaims(Some(Seq(expectedClaimDetails))))
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

  "submitRenewal" should {
    val incomeDetails = IncomeDetails(Some(10), Some(20), Some(30), Some(40), Some(true))
    val certainBenefits =
      CertainBenefits(receivedBenefits = false, incomeSupport = false, jsa = false, esa = false, pensionCredit = false)
    val otherIncome = OtherIncome(Some(100), Some(false))
    val renewal = TcrRenewal(RenewalData(Some(incomeDetails), Some(incomeDetails), Some(certainBenefits)),
                             None,
                             Some(otherIncome),
                             Some(otherIncome),
                             hasChangeOfCircs = false)
    val submitRenewalRequest: FakeRequest[JsValue] = FakeRequest()
      .withBody(toJson(renewal))
      .withHeaders(
        acceptHeader,
        HeaderKeys.tcrAuthToken -> "some-auth-token"
      )

    val requestIncorrectNoHeader: FakeRequest[JsValue] = FakeRequest()
      .withBody(toJson(renewal))
      .withHeaders(
        HeaderKeys.tcrAuthToken -> "some-auth-token"
      )

    "process the renewal successfully if renewals are enabled" in {
      mockShutteringResponse(false)
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      (service
        .submitRenewal(_: Nino, _: TcrRenewal)(_: HeaderCarrier, _: ExecutionContext, _: Request[_]))
        .expects(nino, renewal, *, *, *)
        .returns(Future.successful(200))

      status(controller.submitRenewal(nino, journeyId).apply(submitRenewalRequest)) shouldBe 200
    }

    "return 403 when the nino in the request does not match the authority nino" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      status(controller.submitRenewal(incorrectNino, journeyId).apply(submitRenewalRequest)) shouldBe 403
    }

    "process returns a 200 successfully when journeyId is supplied" in {
      mockShutteringResponse(false)
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      (service
        .submitRenewal(_: Nino, _: TcrRenewal)(_: HeaderCarrier, _: ExecutionContext, _: Request[_]))
        .expects(nino, renewal, *, *, *)
        .returns(Future.successful(200))

      status(controller.submitRenewal(nino, journeyId).apply(submitRenewalRequest)) shouldBe 200
    }

    "return 403 result when no tcr auth header has been supplied" in {
      mockShutteringResponse(false)
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)

      val invalidRequest: FakeRequest[JsValue] = FakeRequest().withBody(toJson(renewal)).withHeaders(acceptHeader)
      status(controller.submitRenewal(nino, journeyId).apply(invalidRequest)) shouldBe 403
    }

    "return bad request when invalid json is submitted" in {
      mockShutteringResponse(false)
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)

      val badRequest: FakeRequest[JsObject] =
        FakeRequest().withBody(Json.obj()).withHeaders(acceptHeader, "tcrAuthToken" -> "some-auth-token")
      status(controller.submitRenewal(nino, journeyId).apply(badRequest)) shouldBe 400
    }

    "return 403 result when authority record does not contain a NINO" in {
      stubAuthorisationGrantAccess(None and L200)
      val result = controller.submitRenewal(nino, journeyId).apply(submitRenewalRequest)
      status(result)        shouldBe 403
      contentAsJson(result) shouldBe forbidden
    }

    "return forbidden when authority record has a low CL" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L50)
      val result = controller.submitRenewal(nino, journeyId).apply(submitRenewalRequest)
      status(result) shouldBe 403
    }

    "return 406 result when the headers are invalid" in {
      status(controller.submitRenewal(nino, journeyId).apply(requestIncorrectNoHeader)) shouldBe 406
    }

    "return 521 when shuttered" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      mockShutteringResponse(true)

      val result   = controller.submitRenewal(nino, journeyId).apply(submitRenewalRequest)
      val jsonBody = contentAsJson(result)
      (jsonBody \ "shuttered").as[Boolean] shouldBe true
      (jsonBody \ "title").as[String]      shouldBe "Shuttered"
      (jsonBody \ "message").as[String]    shouldBe "Tax Credits Renewal is currently not available"
    }

  }

  "taxCreditsSubmissionStateEnabled" should {
    "return the current submission state" in {
      (mockControlConfig.toTaxCreditsRenewalsState _).expects().returning(TaxCreditsRenewalsState("open"))

      val result = controller.taxCreditsSubmissionStateEnabled(journeyId).apply(fakeRequest)
      status(result)        shouldBe 200
      contentAsJson(result) shouldBe Json.parse("""{"submissionsState":"open"}""")
    }
  }

}
