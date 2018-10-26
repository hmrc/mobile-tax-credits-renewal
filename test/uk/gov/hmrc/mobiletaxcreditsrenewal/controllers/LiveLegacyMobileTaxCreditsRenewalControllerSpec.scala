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

package uk.gov.hmrc.mobiletaxcreditsrenewal.controllers

import org.apache.commons.codec.binary.Base64.encodeBase64
import org.scalamock.scalatest.MockFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import play.api.LoggerLike
import play.api.libs.json.Json.{parse, toJson}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{AnyContentAsEmpty, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.ConfidenceLevel._
import uk.gov.hmrc.auth.core.syntax.retrieved._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._
import uk.gov.hmrc.mobiletaxcreditsrenewal.services.MobileTaxCreditsRenewalService
import uk.gov.hmrc.mobiletaxcreditsrenewal.stubs.{AuthorisationStub, LegacyPersonalIncomeServiceStub}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.{ExecutionContext, Future}

class LiveLegacyMobileTaxCreditsRenewalControllerSpec
  extends UnitSpec with MockFactory with WithFakeApplication with AuthorisationStub with LegacyPersonalIncomeServiceStub with ClaimsJson {
  implicit val authConnector: AuthConnector = mock[AuthConnector]
  implicit val mockControlConfig: TaxCreditsControl = mock[TaxCreditsControl]
  implicit val service: MobileTaxCreditsRenewalService = mock[MobileTaxCreditsRenewalService]

  private val logger = new LoggerLike {
    override val logger: Logger = getLogger("LiveMobileTaxCreditsRenewalControllerSpec")
  }

  private val nino = Nino("CS700100A")
  private val incorrectNino = Nino("SC100700A")
  private val journeyId = "journeyId"
  private val tcrAuthToken = TcrAuthenticationToken("some-auth-token")
  private val notFoundException: Future[Nothing] = Future failed new NotFoundException("cant find it")
  private val forbidden: JsValue = parse("""{"code":"FORBIDDEN","message":"Forbidden"}""")

  private val controller = new LiveLegacyMobileTaxCreditsRenewalController(authConnector, logger, service, mockControlConfig, L200.level)

  private val acceptHeader: (String, String) = "Accept" -> "application/vnd.hmrc.1.0+json"

  val renewalReference = RenewalReference("111111111111111")

  lazy val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(
    "AuthToken" -> "Some Header"
  ).withHeaders(
    acceptHeader,
    "Authorization" -> "Some Header"
  )
  lazy val requestInvalidHeaders: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(
    "AuthToken" -> "Some Header"
  ).withHeaders(
    "Authorization" -> "Some Header"
  )

  def basicAuthString(encodedAuth: String): String = "Basic " + encodedAuth

  def encodedAuth(nino: Nino, tcrRenewalReference: RenewalReference): String = new String(encodeBase64(s"${nino.value}:${tcrRenewalReference.value}".getBytes))

  def emptyRequestWithAcceptHeaderAndAuthHeader(renewalsRef: RenewalReference, nino: Nino): FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withHeaders(
    acceptHeader, HeaderKeys.tcrAuthToken -> basicAuthString(encodedAuth(nino, renewalsRef)))

  "getRenewalAuthentication" should {
    "process the authentication successfully" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      stubAuthRenewalResponse(Some(tcrAuthToken), nino, renewalReference)

      val result: Result = await(controller.getRenewalAuthentication(nino, renewalReference).apply(fakeRequest))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe toJson(tcrAuthToken)
    }

    "return 403 when the nino in the request does not match the authority nino" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      status(await(controller.getRenewalAuthentication(incorrectNino, renewalReference).apply(fakeRequest))) shouldBe 403
    }

    "process the authentication successful when journeyId is supplied" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      stubAuthRenewalResponse(Some(tcrAuthToken), nino, renewalReference)

      val result: Result = await(controller.getRenewalAuthentication(nino, renewalReference, Some("some-unique-journey-id")).apply(fakeRequest))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe toJson(tcrAuthToken)
    }

    "return Http 404 (NotFound) response when hod returns either a (BadRequest) 400 or (NotFound) 404 status" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      (service.authenticateRenewal(_: Nino, _: RenewalReference)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, renewalReference, *, *).returns(notFoundException)

      status(await(controller.getRenewalAuthentication(nino, renewalReference).apply(fakeRequest))) shouldBe 404
    }

    "return forbidden when authority record does not contain a NINO" in {
      stubAuthorisationGrantAccess(None and L200)

      val result: Result = await(controller.getRenewalAuthentication(nino, renewalReference, Some(journeyId)).apply(fakeRequest))
      status(result) shouldBe 403
      contentAsJson(result) shouldBe forbidden
    }

    "return forbidden when authority record has a low CL" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L100)

      val result: Result = await(controller.getRenewalAuthentication(nino, renewalReference, Some(journeyId)).apply(fakeRequest))
      status(result) shouldBe 403
      contentAsJson(result) shouldBe forbidden
    }

    "return status code 406 when the headers are invalid" in {
      status(await(controller.getRenewalAuthentication(nino, renewalReference, Some(journeyId)).apply(requestInvalidHeaders))) shouldBe 406
    }
  }

  "claimantDetails" should {
    "return claimant details successfully" in {
      val claimantDetails = ClaimantDetails(hasPartner = false, 1, "r", nino.nino, None, availableForCOCAutomation = false, "some-app-id")
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      stubClaimantDetailsResponse(claimantDetails, nino)

      val result: Result = await(controller.claimantDetails(nino).apply(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, nino)))

      status(result) shouldBe 200
      contentAsJson(result) shouldBe toJson(claimantDetails.copy(mainApplicantNino = "true"))
      result.header.headers.get("Cache-Control") shouldBe None
    }

    "return claimant details successfully when NINO does not match mainApplicantNino" in {
      val claimantDetails = ClaimantDetails(hasPartner = false, 1, "r", incorrectNino.value, None, availableForCOCAutomation = false, "some-app-id")
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      stubClaimantDetailsResponse(claimantDetails, nino)

      val result: Result = await(controller.claimantDetails(nino)(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, nino)))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe toJson(claimantDetails.copy(mainApplicantNino = "false"))
      result.header.headers.get("Cache-Control") shouldBe None
    }

    "return claimant claims successfully" in {
      val matchedClaims: LegacyClaims = Json.parse(matchedClaimsJson).as[LegacyClaims]
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      stubClaimantClaimsResponse(matchedClaims, nino)

      val result: Result = await(controller.claimantDetails(nino, None, Some("claims"))(fakeRequest))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe Json.parse(matchedClaimsJson)
      result.header.headers.get("Cache-Control") shouldBe None
    }

    "return 404 when no claims matched the supplied nino" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      (service.legacyClaimantClaims(_: Nino)(_: HeaderCarrier, _: ExecutionContext)).expects(nino, *, *).
        returns(notFoundException)

      status(await(controller.claimantDetails(nino, None, Some("claims"))(fakeRequest))) shouldBe 404
    }

    "return 403 when no tcrAuthHeader is supplied to claimant details API" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)

      val result: Result = await(controller.claimantDetails(nino, None, None)(fakeRequest))
      status(result) shouldBe 403
      contentAsJson(result) shouldBe Json.parse("""{"code":"NTC_RENEWAL_AUTH_ERROR","message":"No auth header supplied in http request"}""")
    }

    "return 403 when tcrAuthHeader is supplied to claims API" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)

      val result: Result = await(controller.claimantDetails(nino, None, Some("claims"))(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, nino)))
      status(result) shouldBe 403
      contentAsJson(result) shouldBe Json.parse("""{"code":"NTC_RENEWAL_AUTH_ERROR","message":"Auth header is not required in the request"}""")
    }

    "return 403 when the nino in the request does not match the authority nino" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)

      status(await(controller.claimantDetails(incorrectNino)(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, incorrectNino)))) shouldBe 403
    }

    "return the claimant details successfully when journeyId is supplied" in {
      val claimantDetails = ClaimantDetails(hasPartner = false, 1, "r", nino.nino, None, availableForCOCAutomation = false, "some-app-id")
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      stubClaimantDetailsResponse(claimantDetails, nino)

      val result: Result = await(controller.claimantDetails(nino, Some("unique-journey-id"))(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, nino)))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe toJson(claimantDetails.copy(mainApplicantNino = "true"))
    }

    "return forbidden when authority record does not contain a NINO" in {
      stubAuthorisationGrantAccess(None and L200)

      val result: Result = await(controller.claimantDetails(nino)(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, nino)))
      status(result) shouldBe 403
      contentAsJson(result) shouldBe forbidden
    }

    "return forbidden when authority record has a low CL" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L50)

      val result: Result = await(controller.claimantDetails(nino)(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, nino)))
      status(result) shouldBe 403
      contentAsJson(result) shouldBe forbidden
    }

    "return 403 response when the tcr auth header is not supplied in the request" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)

      val result: Result = await(controller.claimantDetails(nino)(fakeRequest))
      status(result) shouldBe 403
      contentAsJson(result) shouldBe toJson(ErrorNoAuthToken)
    }

    "return status code 406 when the Accept header is invalid" in {
      status(await(controller.claimantDetails(nino)(requestInvalidHeaders))) shouldBe 406
    }
  }

  "full claimant details" should {
    val applicant = Applicant(nino.nino, "MR", "BOB", None, "ROBSON")
    val renewal = LegacyRenewal(None, None, None, None, None, None)
    val renewalFormType = "renewalFormType"

    "return details with the renewalFormType set" in {
      val household = Household(renewalReference.value, "applicationId", applicant, None, None, None)
      val expectedClaimDetails = LegacyClaim(household, LegacyRenewal(None, None, None, None, None, Some(renewalFormType)))

      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      stubServiceClaimantClaims(LegacyClaims(Some(Seq(LegacyClaim(household, renewal)))), nino)
      stubServiceAuthenticateRenewal(tcrAuthToken, nino, renewalReference)
      stubClaimantDetailsResponse(
        ClaimantDetails(
          hasPartner = false, 1, renewalFormType, incorrectNino.value,
          None, availableForCOCAutomation = false, "some-app-id"), nino)

      val result: Result = await(controller.fullClaimantDetails(nino, Some(journeyId))(fakeRequest))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe toJson(LegacyClaims(Some(Seq(expectedClaimDetails))))
    }
  }

  "submitRenewal" should {
    val incomeDetails = IncomeDetails(Some(10), Some(20), Some(30), Some(40), Some(true))
    val certainBenefits = CertainBenefits(receivedBenefits = false, incomeSupport = false, jsa = false, esa = false, pensionCredit = false)
    val otherIncome = OtherIncome(Some(100), Some(false))
    val renewal = TcrRenewal(RenewalData(Some(incomeDetails), Some(incomeDetails),
      Some(certainBenefits)), None, Some(otherIncome), Some(otherIncome), hasChangeOfCircs = false)
    val submitRenewalRequest: FakeRequest[JsValue] = FakeRequest().withBody(toJson(renewal)).withHeaders(
      acceptHeader,
      HeaderKeys.tcrAuthToken -> "some-auth-token"
    )

    val requestIncorrectNoHeader: FakeRequest[JsValue] = FakeRequest().withBody(toJson(renewal)).withHeaders(
      HeaderKeys.tcrAuthToken -> "some-auth-token"
    )

    "process the renewal successfully if renewals are enabled" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      (service.submitRenewal(_: Nino, _: TcrRenewal)(_: HeaderCarrier, _: ExecutionContext, _: Request[_]))
        .expects(nino, renewal, *, *, *).returns(200)

      status(await(controller.submitRenewal(nino).apply(submitRenewalRequest))) shouldBe 200
    }

    "return 403 when the nino in the request does not match the authority nino" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      status(await(controller.submitRenewal(incorrectNino).apply(submitRenewalRequest))) shouldBe 403
    }

    "process returns a 200 successfully when journeyId is supplied" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      (service.submitRenewal(_: Nino, _: TcrRenewal)(_: HeaderCarrier, _: ExecutionContext, _: Request[_]))
        .expects(nino, renewal, *, *, *).returns(200)

      status(await(controller.submitRenewal(nino, Some("unique-journey-id")).apply(submitRenewalRequest))) shouldBe 200
    }

    "return 403 result when no tcr auth header has been supplied" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)

      val invalidRequest: FakeRequest[JsValue] = FakeRequest().withBody(toJson(renewal)).withHeaders(acceptHeader)
      status(await(controller.submitRenewal(nino).apply(invalidRequest))) shouldBe 403
    }

    "return bad request when invalid json is submitted" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)

      val badRequest: FakeRequest[JsObject] = FakeRequest().withBody(Json.obj()).withHeaders(acceptHeader, "tcrAuthToken" -> "some-auth-token")
      status(await(controller.submitRenewal(nino).apply(badRequest))) shouldBe 400
    }

    "return 403 result when authority record does not contain a NINO" in {
      stubAuthorisationGrantAccess(None and L200)
      val result: Result = await(controller.submitRenewal(nino).apply(submitRenewalRequest))
      status(result) shouldBe 403
      contentAsJson(result) shouldBe forbidden
    }

    "return forbidden when authority record has a low CL" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L50)
      val result: Result = await(controller.submitRenewal(nino).apply(submitRenewalRequest))
      status(result) shouldBe 403
    }

    "return 406 result when the headers are invalid" in {
      status(await(controller.submitRenewal(nino).apply(requestIncorrectNoHeader))) shouldBe 406
    }

  }

  "taxCreditsSubmissionStateEnabled" should {
    "return the current submission state" in {
      (mockControlConfig.toTaxCreditsRenewalsState _).expects().returning(TaxCreditsRenewalsState("open"))

      val result = await(controller.taxCreditsSubmissionStateEnabled().apply(fakeRequest))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe Json.parse("""{"submissionsState":"open"}""")
    }
  }

}
