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

import java.time.LocalDate._

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.LoggerLike
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.api.sandbox.FileResource
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.ConfidenceLevel._
import uk.gov.hmrc.auth.core.syntax.retrieved._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import uk.gov.hmrc.mobiletaxcreditsrenewal.connectors._
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.userdata._
import uk.gov.hmrc.mobiletaxcreditsrenewal.services.PersonalIncomeService
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.{ExecutionContext, Future}

class TestPersonalIncomeController(val authConnector: AuthConnector,
                                   val service: PersonalIncomeService,
                                   val confLevel: Int,
                                   val logger: LoggerLike,
                                   taxCreditsSubmissions: TaxCreditsSubmissions = new TaxCreditsSubmissions(
                                     submissionShuttered = false,
                                     inSubmitRenewalsPeriod = true,
                                     inViewRenewalsPeriod = true)) extends PersonalIncomeController {
  override val taxCreditsSubmissionControlConfig: TaxCreditsControl = new TaxCreditsControl {
    override def toTaxCreditsSubmissions: TaxCreditsSubmissions = taxCreditsSubmissions

    override def toTaxCreditsRenewalsState: TaxCreditsRenewalsState = taxCreditsSubmissions.toTaxCreditsRenewalsState
  }

  override def getConfigForClaimsMaxAge: Option[Long] = Some(1800)
}

class TestSummarySpec extends TestSetup with WithFakeApplication {

  "getSummary Live" should {

    "return 401 when the nino in the request does not match the authority nino" in new mocks {
      val incorrectNino = Nino("SC100700A")
      stubAuthorisationGrantAccess(Some(nino) and L200)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      status(await(controller.getSummary(incorrectNino, now().getYear, Option("unique-journey-id")).apply(fakeRequest))) shouldBe 401
    }

    "return 404 when summary returned is None" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L200)
      stubGetSummaryResponse(None)
      val controller = new TestPersonalIncomeController(mockAuthConnector, mockLivePersonalIncomeService, 200, logger)
      status(await(controller.getSummary(Nino(nino), now().getYear, Option("unique-journey-id")).apply(fakeRequest))) shouldBe 404
    }

    "return unauthorized when authority record does not contain a NINO" in new mocks {
      stubAuthorisationGrantAccess(None and L200)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      val result: Result = await(controller.getSummary(Nino(nino), now().getYear, Option("unique-journey-id")).apply(fakeRequest))
      status(result) shouldBe 401
      contentAsJson(result) shouldBe noNinoFoundOnAccount
    }

    "return unauthorized when authority record has a low CL" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L100)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      val result: Result = await(controller.getSummary(Nino(nino), now().getYear, Option("unique-journey-id")).apply(fakeRequest))
      status(result) shouldBe 401
      contentAsJson(result) shouldBe lowConfidenceLevelError
    }

    "return status code 406 when the headers are invalid" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L100)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      status(await(controller.getSummary(Nino(nino), now().getYear, Option("unique-journey-id")).apply(requestInvalidHeaders))) shouldBe 406
    }
  }
}

class TestPersonalIncomeRenewalAuthenticateSpec extends TestSetup with WithFakeApplication {

  "authenticate Live" should {

    "process the authentication successfully" in new mocks {
      val tcrAuthToken = TcrAuthenticationToken("some-auth-token")
      stubAuthorisationGrantAccess(Some(nino) and L200)
      stubAuthRenewalResponse(Some(tcrAuthToken))
      val controller = new TestPersonalIncomeController(mockAuthConnector, mockLivePersonalIncomeService, 200, logger)
      val result: Result = await(controller.getRenewalAuthentication(Nino(nino), renewalReference).apply(fakeRequest))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe toJson(tcrAuthToken)
    }

    "return 401 when the nino in the request does not match the authority nino" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L200)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      status(await(controller.getRenewalAuthentication(incorrectNino, renewalReference).apply(fakeRequest))) shouldBe 401
    }

    "process the authentication successfully when journeyId is supplied" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L200)
      stubAuthRenewalResponse(Some(tcrAuthToken))
      val controller = new TestPersonalIncomeController(mockAuthConnector, mockLivePersonalIncomeService, 200, logger)
      val result: Result = await(controller.getRenewalAuthentication(Nino(nino), renewalReference, Some("some-unique-journey-id")).apply(fakeRequest))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe toJson(tcrAuthToken)
    }

    "return Http 404 (NotFound) response when hod returns either a (BadRequest) 400 or (NotFound) 404 status" in new mocks {
      val renewalReferenceNines = RenewalReference("999999999999999")
      stubAuthenticateRenewal(None)(mockNtcConnector)
      stubAuthorisationGrantAccess(Some(nino) and L200)

      override val mockLivePersonalIncomeService: TestPersonalIncomeService = new TestPersonalIncomeService(mockNtcConnector,
        mockTaiConnector, mockPersonalTaxSummaryConnector, mockTaxCreditsBrokerConnector, mockAuditConnector, mockConfiguration, mockAppContext)

      val controller = new TestPersonalIncomeController(mockAuthConnector, mockLivePersonalIncomeService, 200, logger)
      status(await(controller.getRenewalAuthentication(Nino(nino), renewalReferenceNines)(fakeRequest))) shouldBe 404
    }

    "return unauthorized when authority record does not contain a NINO" in new mocks {
      stubAuthorisationGrantAccess(None and L200)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      val result: Result = await(controller.getRenewalAuthentication(Nino(nino), renewalReference, Option("unique-journey-id")).apply(fakeRequest))
      status(result) shouldBe 401
      contentAsJson(result) shouldBe noNinoFoundOnAccount
    }

    "return unauthorized when authority record has a low CL" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L100)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      val result: Result = await(controller.getRenewalAuthentication(Nino(nino), renewalReference, Option("unique-journey-id")).apply(fakeRequest))
      status(result) shouldBe 401
      contentAsJson(result) shouldBe lowConfidenceLevelError
    }

    "return status code 406 when the headers are invalid" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L100)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      status(await(controller.getRenewalAuthentication(Nino(nino), renewalReference, Option("unique-journey-id")).apply(requestInvalidHeaders))) shouldBe 406
    }
  }
}

class TestPersonalIncomeRenewalClaimantDetailsSpec extends TestSetup with WithFakeApplication with ClaimsJson {

  "requesting claimant details Live" should {

    "return claimant details successfully" in new mocks {
      val claimantDetails = ClaimantDetails(hasPartner = false, 1, "r", nino, None, availableForCOCAutomation = false, "some-app-id")
      stubAuthorisationGrantAccess(Some(nino) and L200)
      stubClaimantDetailsResponse(claimantDetails)
      val controller = new TestPersonalIncomeController(mockAuthConnector, mockLivePersonalIncomeService, 200, logger)
      val result: Result = await(controller.claimantDetails(Nino(nino)).apply(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, Nino(nino))))

      status(result) shouldBe 200
      contentAsJson(result) shouldBe toJson(claimantDetails.copy(mainApplicantNino = "true"))
      result.header.headers.get("Cache-Control") shouldBe None
    }

    "return claimant details successfully when NINO does not match mainApplicantNino" in new mocks {
      val claimantDetails = ClaimantDetails(hasPartner = false, 1, "r", incorrectNino.value, None, availableForCOCAutomation = false, "some-app-id")
      stubAuthorisationGrantAccess(Some(nino) and L200)
      stubClaimantDetailsResponse(claimantDetails)
      val controller = new TestPersonalIncomeController(mockAuthConnector, mockLivePersonalIncomeService, 200, logger)
      val result: Result = await(controller.claimantDetails(Nino(nino))(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, Nino(nino))))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe toJson(claimantDetails.copy(mainApplicantNino = "false"))
      result.header.headers.get("Cache-Control") shouldBe None
    }

    "return claimant claims successfully" in new mocks {
      val matchedClaims: Claims = Json.parse(matchedClaimsJson).as[Claims]
      stubAuthorisationGrantAccess(Some(nino) and L200)
      stubClaimantClaimsResponse(matchedClaims)
      val controller = new TestPersonalIncomeController(mockAuthConnector, mockLivePersonalIncomeService, 200, logger)
      val result: Result = await(controller.claimantDetails(Nino(nino), None, Some("claims"))(fakeRequest))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe Json.parse(matchedClaimsJson)
      result.header.headers.get("Cache-Control") shouldBe Some("max-age=1800")
    }

    "return claimant claims successfully and drop invalid dates from the response" in new mocks {
      stubClaimantClaims(Json.parse(claimsJsonWithInvalidDates).as[Claims])
      stubAuthorisationGrantAccess(Some(nino) and L200)
      override val mockLivePersonalIncomeService: TestPersonalIncomeService = new TestPersonalIncomeService(mockNtcConnector,
        mockTaiConnector, mockPersonalTaxSummaryConnector, mockTaxCreditsBrokerConnector, mockAuditConnector, mockConfiguration, mockAppContext)
      val controller = new TestPersonalIncomeController(mockAuthConnector, mockLivePersonalIncomeService, 200, logger)
      val result: Result = await(controller.claimantDetails(Nino(nino), None, Some("claims"))(fakeRequest))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe Json.parse(matchedClaimsJsonWithInvalidDates)
    }

    "return claimant claims successfully where dates are formatted yyyyMMdd" in new mocks {
      stubClaimantClaims(Json.parse(claimsJsonWithDatesFormattedYYYYMMDD).as[Claims])
      stubAuthorisationGrantAccess(Some(nino) and L200)
      override val mockLivePersonalIncomeService: TestPersonalIncomeService = new TestPersonalIncomeService(mockNtcConnector,
        mockTaiConnector, mockPersonalTaxSummaryConnector, mockTaxCreditsBrokerConnector, mockAuditConnector, mockConfiguration, mockAppContext)
      val controller = new TestPersonalIncomeController(mockAuthConnector, mockLivePersonalIncomeService, 200, logger)
      val result: Result = await(controller.claimantDetails(Nino(nino), None, Some("claims"))(fakeRequest))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe Json.parse(matchedClaimsJsonWithAllDates)
    }

    "return claimant claims successfully where dates are formatted yyyy-MM-dd" in new mocks {
      stubClaimantClaims(Json.parse(claimsJsonWithDatesFormattedHyphenatedYYYYMMDD).as[Claims])
      stubAuthorisationGrantAccess(Some(nino) and L200)
      override val mockLivePersonalIncomeService: TestPersonalIncomeService = new TestPersonalIncomeService(mockNtcConnector,
        mockTaiConnector, mockPersonalTaxSummaryConnector, mockTaxCreditsBrokerConnector, mockAuditConnector, mockConfiguration, mockAppContext)
      val controller = new TestPersonalIncomeController(mockAuthConnector, mockLivePersonalIncomeService, 200, logger)
      val result: Result = await(controller.claimantDetails(Nino(nino), None, Some("claims"))(fakeRequest))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe Json.parse(matchedClaimsJsonWithAllDates)
    }

    "return 404 when no claims matched the supplied nino" in new mocks {
      stubClaimantClaims(buildClaims(toJson(Json.parse(claimsJson)).as[Claims]))
      stubAuthorisationGrantAccess(Some(nino) and L200)
      override val mockLivePersonalIncomeService: TestPersonalIncomeService = new TestPersonalIncomeService(mockNtcConnector,
        mockTaiConnector, mockPersonalTaxSummaryConnector, mockTaxCreditsBrokerConnector, mockAuditConnector, mockConfiguration, mockAppContext)
      val controller = new TestPersonalIncomeController(mockAuthConnector, mockLivePersonalIncomeService, 200, logger)
      status(await(controller.claimantDetails(Nino(nino), None, Some("claims"))(fakeRequest))) shouldBe 404
    }

    "return 403 when no tcrAuthHeader is supplied to claimant details API" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L200)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      val result: Result = await(controller.claimantDetails(Nino(nino), None, None)(fakeRequest))
      status(result) shouldBe 403
      contentAsJson(result) shouldBe Json.parse("""{"code":"NTC_RENEWAL_AUTH_ERROR","message":"No auth header supplied in http request"}""")
    }

    "return 403 when tcrAuthHeader is supplied to claims API" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L200)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      val result: Result = await(controller.claimantDetails(Nino(nino), None, Some("claims"))(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, Nino(nino))))
      status(result) shouldBe 403
      contentAsJson(result) shouldBe Json.parse("""{"code":"NTC_RENEWAL_AUTH_ERROR","message":"Auth header is not required in the request"}""")
    }

    "return 401 when the nino in the request does not match the authority nino" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L200)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      status(await(controller.claimantDetails(incorrectNino)(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, incorrectNino)))) shouldBe 401
    }

    "return the claimant details successfully when journeyId is supplied" in new mocks {
      val claimantDetails = ClaimantDetails(hasPartner = false, 1, "r", nino, None, availableForCOCAutomation = false, "some-app-id")
      stubAuthorisationGrantAccess(Some(nino) and L200)
      stubClaimantDetailsResponse(claimantDetails)
      val controller = new TestPersonalIncomeController(mockAuthConnector, mockLivePersonalIncomeService, 200, logger)
      val result: Result = await(controller.claimantDetails(Nino(nino), Some("unique-journey-id"))(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, Nino(nino))))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe toJson(claimantDetails.copy(mainApplicantNino = "true"))
    }

    "return unauthorized when authority record does not contain a NINO" in new mocks {
      stubAuthorisationGrantAccess(None and L200)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      val result: Result = await(controller.claimantDetails(Nino(nino))(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, Nino(nino))))
      status(result) shouldBe 401
      contentAsJson(result) shouldBe noNinoFoundOnAccount
    }

    "return unauthorized when authority record has a low CL" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L50)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      val result: Result = await(controller.claimantDetails(Nino(nino))(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, Nino(nino))))
      status(result) shouldBe 401
      contentAsJson(result) shouldBe lowConfidenceLevelError
    }

    "return 403 response when the tcr auth header is not supplied in the request" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L200)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      val result: Result = await(controller.claimantDetails(Nino(nino))(fakeRequest))
      status(result) shouldBe 403
      contentAsJson(result) shouldBe toJson(ErrorNoAuthToken)
    }

    "return status code 406 when the Accept header is invalid" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L200)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      status(await(controller.claimantDetails(Nino(nino))(requestInvalidHeaders))) shouldBe 406
    }
  }

  "claimant details Sandbox" should {

    "return claimant details successfully when an unknown bar code reference is supplied" in new mocks {
      val claimantDetails = ClaimantDetails(hasPartner = false, 1, "r", nino, None, availableForCOCAutomation = false, "some-app-id")
      val controller = new SandboxPersonalIncomeController(mockAuthConnector, 200, logger)
      val result: Result = await(controller.claimantDetails(Nino(nino))(emptyRequestWithAcceptHeaderAndAuthHeader(RenewalReference("888888888888888"), Nino(nino))))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe toJson(claimantDetails.copy(mainApplicantNino = "false", hasPartner = false, renewalFormType = "r"))
    }

    "return claimant claims successfully" in new mocks {
      val claimantDetails = ClaimantDetails(hasPartner = false, 1, "r", nino, None, availableForCOCAutomation = false, "some-app-id")
      val controller = new SandboxPersonalIncomeController(mockAuthConnector, 200, logger)
      val result: Result = await(controller.claimantDetails(Nino(nino), None, Some("claims"))(fakeRequest))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe Json.parse(matchedClaimsJson)
    }

    "return claimant details successfully when a known bar code reference is supplied" in new mocks {
      val claimantDetails = ClaimantDetails(hasPartner = false, 1, "r", nino, None, availableForCOCAutomation = false, "some-app-id")

      case class TestData(barcode: String, renewalFormType: String, hasPartner: Boolean = false)

      val testData = Seq(TestData("111111111111111", "r"), TestData("222222222222222", "d"), TestData("333333333333333", "d2"),
        TestData("444444444444444", "d", hasPartner = true), TestData("555555555555555", "d2", hasPartner = true))
      val controller = new SandboxPersonalIncomeController(mockAuthConnector, 200, logger)
      testData.map(item => {
        val result = await(controller.claimantDetails(Nino(nino))(emptyRequestWithAcceptHeaderAndAuthHeader(RenewalReference(item.barcode), Nino(nino))))
        status(result) shouldBe 200
        contentAsJson(result) shouldBe toJson(claimantDetails.copy(mainApplicantNino = "true", hasPartner = item.hasPartner, renewalFormType = item.renewalFormType))
      })
    }

    "return 403 response when the tcr auth header is not supplied in the request" in new mocks {
      val controller = new SandboxPersonalIncomeController(mockAuthConnector, 200, logger)
      val result: Result = await(controller.claimantDetails(Nino(nino))(fakeRequest))
      status(result) shouldBe 403
      contentAsJson(result) shouldBe toJson(ErrorNoAuthToken)
    }

    "return status code 406 when the Accept header is invalid" in new mocks {
      val controller = new SandboxPersonalIncomeController(mockAuthConnector, 200, logger)
      val result: Result = await(controller.claimantDetails(Nino(nino))(requestInvalidHeaders))
      status(result) shouldBe 406
    }
  }

  "full claimant details" should {
    val applicant = Applicant(nino, "MR", "BOB", None, "ROBSON")
    val renewal = Renewal(None, None, None, None, None, None)
    val journeyId = Some("unique-journey-id")
    val renewalFormType = "renewalFormType"

    "return details with the renewalFormType set" in new mocks {
      val controller = new TestPersonalIncomeController(mockAuthConnector, mockLivePersonalIncomeService, 200, logger)
      val household = Household("barcodeReference", "applicationId", applicant, None, None, None)
      val expectedClaimDetails = Claim(household, Renewal(None, None, None, None, None, Some(renewalFormType)))

      stubAuthorisationGrantAccess(Some(nino) and L200)
      stubServiceClaimantClaims(Claims(Some(Seq(Claim(household, renewal)))))
      stubServiceAuthenticateRenewal
      stubClaimantDetailsResponse(ClaimantDetails(hasPartner = false, 1, renewalFormType, incorrectNino.value, None,
        availableForCOCAutomation = false, "some-app-id"))

      val result: Result = await(controller.fullClaimantDetails(Nino(nino), journeyId)(fakeRequest))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe toJson(Claims(Some(Seq(expectedClaimDetails))))
    }

    "log a message if the barcode reference is 000000000000000" in new mocks {
      logger.clearMessages()

      val controller = new TestPersonalIncomeController(mockAuthConnector, mockLivePersonalIncomeService, 200, logger)
      val claims = Claims(Some(Seq(Claim(Household("000000000000000", "applicationId", applicant, None, None, None), renewal))))

      stubAuthorisationGrantAccess(Some(nino) and L200)
      stubServiceClaimantClaims(claims)

      val result: Result = await(controller.fullClaimantDetails(Nino(nino), journeyId)(fakeRequest))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe toJson(claims)
      logger.warnMessageWaslogged(s"Invalid barcode reference 000000000000000 for journeyId $journeyId applicationId applicationId") shouldBe true
    }

    "log a message if an empty sequence of claims is found" in new mocks {
      logger.clearMessages()

      val controller = new TestPersonalIncomeController(mockAuthConnector, mockLivePersonalIncomeService, 200, logger)
      val claims = Claims(Some(Seq()))

      stubAuthorisationGrantAccess(Some(nino) and L200)
      stubServiceClaimantClaims(claims)

      val result: Result = await(controller.fullClaimantDetails(Nino(nino), journeyId)(fakeRequest))
      status(result) shouldBe 200
      logger.warnMessageWaslogged(s"Empty claims list for journeyId $journeyId") shouldBe true
    }
  }
}

class TestPersonalIncomeRenewalSpec extends TestSetup with WithFakeApplication {

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

  "renewal Live" should {
    val journeyId = Some("unique-journey-id")

    "process the renewal successfully if renewals are enabled" in new mocks {
      logger.clearMessages()

      stubAuthorisationGrantAccess(Some(nino) and L200)
      stubSubmitRenewals(Success(200))
      override val mockLivePersonalIncomeService = new TestPersonalIncomeService(mockNtcConnector, mockTaiConnector,
        mockPersonalTaxSummaryConnector, mockTaxCreditsBrokerConnector, mockAuditConnector, mockConfiguration, mockAppContext)
      val controller = new TestPersonalIncomeController(mockAuthConnector, mockLivePersonalIncomeService, 200, logger)
      status(await(controller.submitRenewal(Nino(nino)).apply(submitRenewalRequest))) shouldBe 200
      verify(mockNtcConnector, times(1)).submitRenewal(any[TaxCreditsNino](), any[TcrRenewal]())(any[HeaderCarrier](), any[ExecutionContext]())

      logger.infoMessageWasLogged(s"Tax credit renewal submission successful for journeyId None") shouldBe true
    }

    "return 401 when the nino in the request does not match the authority nino" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L200)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      status(await(controller.submitRenewal(incorrectNino).apply(submitRenewalRequest))) shouldBe 401
    }

    "return a 200 response if renewals are disabled" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L200)
      val submissionsShuttered = new TaxCreditsSubmissions(false, false, true)
      override val mockLivePersonalIncomeService = new TestPersonalIncomeService(mockNtcConnector, mockTaiConnector,
        mockPersonalTaxSummaryConnector, mockTaxCreditsBrokerConnector, mockAuditConnector, mockConfiguration, mockAppContext)
      val controller = new TestPersonalIncomeController(mockAuthConnector, mockLivePersonalIncomeService, 200, logger, taxCreditsSubmissions = submissionsShuttered)
      status(await(controller.submitRenewal(Nino(nino)).apply(submitRenewalRequest))) shouldBe 200
      verify(mockNtcConnector, never()).submitRenewal(any[TaxCreditsNino](), any[TcrRenewal]())(any[HeaderCarrier](), any[ExecutionContext]())
    }

    "process returns a 200 successfully when journeyId is supplied" in new mocks {
      logger.clearMessages()

      stubAuthorisationGrantAccess(Some(nino) and L200)
      stubSubmitRenewals(Success(200))
      override val mockLivePersonalIncomeService = new TestPersonalIncomeService(mockNtcConnector, mockTaiConnector,
        mockPersonalTaxSummaryConnector, mockTaxCreditsBrokerConnector, mockAuditConnector, mockConfiguration, mockAppContext)
      val controller = new TestPersonalIncomeController(mockAuthConnector, mockLivePersonalIncomeService, 200, logger)
      status(await(controller.submitRenewal(Nino(nino), Some("unique-journey-id")).apply(submitRenewalRequest))) shouldBe 200
      verify(mockNtcConnector, times(1)).submitRenewal(any[TaxCreditsNino](), any[TcrRenewal]())(any[HeaderCarrier](), any[ExecutionContext]())

      logger.infoMessageWasLogged(s"Tax credit renewal submission successful for journeyId $journeyId") shouldBe true
    }

    "process returns a 200 when journeyId is supplied and Renewals are disabled" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L200)
      val submissionsShuttered = new TaxCreditsSubmissions(false, false, true)
      override val mockLivePersonalIncomeService = new TestPersonalIncomeService(mockNtcConnector, mockTaiConnector,
        mockPersonalTaxSummaryConnector, mockTaxCreditsBrokerConnector, mockAuditConnector, mockConfiguration, mockAppContext)
      val controller = new TestPersonalIncomeController(mockAuthConnector, mockLivePersonalIncomeService, 200, logger, submissionsShuttered)
      status(await(controller.submitRenewal(Nino(nino), Some("unique-journey-id")).apply(submitRenewalRequest))) shouldBe 200
      verify(mockNtcConnector, never()).submitRenewal(any[TaxCreditsNino](), any[TcrRenewal]())(any[HeaderCarrier](), any[ExecutionContext]())
    }

    "return 403 result when no tcr auth header has been supplied" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L200)
      override val mockTaxCreditsSubmissionControlConfig: TaxCreditsSubmissionControlConfig =
        fakeApplication.injector.instanceOf[TaxCreditsSubmissionControlConfig]

      val invalidRequest: FakeRequest[JsValue] = FakeRequest().withBody(toJson(renewal)).withHeaders(acceptHeader)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      status(await(controller.submitRenewal(Nino(nino)).apply(invalidRequest))) shouldBe 403
    }

    "return bad request when invalid json is submitted" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L200)
      override val mockTaxCreditsSubmissionControlConfig: TaxCreditsSubmissionControlConfig =
        fakeApplication.injector.instanceOf[TaxCreditsSubmissionControlConfig]

      val badRequest: FakeRequest[JsObject] = FakeRequest().withBody(Json.obj()).withHeaders(acceptHeader, HeaderKeys.tcrAuthToken -> "some-auth-token")
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      status(await(controller.submitRenewal(Nino(nino)).apply(badRequest))) shouldBe 400
    }

    "return 401 result when authority record does not contain a NINO" in new mocks {
      stubAuthorisationGrantAccess(None and L200)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      val result: Result = await(controller.submitRenewal(Nino(nino)).apply(submitRenewalRequest))
      status(result) shouldBe 401
      contentAsJson(result) shouldBe noNinoFoundOnAccount
    }

    "return unauthorized when authority record has a low CL" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L50)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      val result: Result = await(controller.submitRenewal(Nino(nino)).apply(submitRenewalRequest))
      status(result) shouldBe 401
      contentAsJson(result) shouldBe lowConfidenceLevelError
    }

    "return 406 result when the headers are invalid" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L200)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      status(await(controller.submitRenewal(Nino(nino)).apply(requestIncorrectNoHeader))) shouldBe 406
    }

    "Log a warning when the service returns an Error response" in new mocks {
      logger.clearMessages()

      stubAuthorisationGrantAccess(Some(nino) and L200)
      stubSubmitRenewals(Error(300))
      override val mockLivePersonalIncomeService = new TestPersonalIncomeService(mockNtcConnector, mockTaiConnector,
        mockPersonalTaxSummaryConnector, mockTaxCreditsBrokerConnector, mockAuditConnector, mockConfiguration, mockAppContext)
      val controller = new TestPersonalIncomeController(mockAuthConnector, mockLivePersonalIncomeService, 200, logger)
      status(await(controller.submitRenewal(Nino(nino), journeyId).apply(submitRenewalRequest))) shouldBe 300
      verify(mockNtcConnector, times(1)).submitRenewal(any[TaxCreditsNino](), any[TcrRenewal]())(any[HeaderCarrier](), any[ExecutionContext]())

      logger.warnMessageWaslogged(s"Tax credit renewal submission failed with status 300 for journeyId $journeyId") shouldBe true
    }

    "Log a warning when the service throws an exception" in new mocks {
      logger.clearMessages()

      stubAuthorisationGrantAccess(Some(nino) and L200)
      stubSubmitRenewalsException(new RuntimeException("some 5XX message"))
      override val mockLivePersonalIncomeService = new TestPersonalIncomeService(mockNtcConnector, mockTaiConnector,
        mockPersonalTaxSummaryConnector, mockTaxCreditsBrokerConnector, mockAuditConnector, mockConfiguration, mockAppContext)
      val controller = new TestPersonalIncomeController(mockAuthConnector, mockLivePersonalIncomeService, 200, logger)

      status(await(controller.submitRenewal(Nino(nino), journeyId).apply(submitRenewalRequest))) shouldBe 500

      logger.warnMessageWaslogged(s"Tax credit renewal submission failed with exception some 5XX message for journeyId $journeyId") shouldBe true
    }
  }

  "renewal sandbox" should {

    "return success response" in new mocks {
      val controller = new SandboxPersonalIncomeController(mockAuthConnector, 200, logger)
      status(await(controller.submitRenewal(Nino(nino)).apply(submitRenewalRequest))) shouldBe 200
    }

    "return 403 result when no tcr auth header has been supplied" in new mocks {
      val invalidRequest: FakeRequest[JsValue] = FakeRequest().withBody(toJson(renewal)).withHeaders(acceptHeader)
      val controller = new SandboxPersonalIncomeController(mockAuthConnector, 200, logger)
      status(await(controller.submitRenewal(Nino(nino)).apply(invalidRequest))) shouldBe 403
    }

    "return 406 result when the headers are invalid" in new mocks {
      val controller = new SandboxPersonalIncomeController(mockAuthConnector, 200, logger)
      status(await(controller.submitRenewal(Nino(nino)).apply(requestIncorrectNoHeader))) shouldBe 406
    }
  }

}

class TestPersonalIncomeRenewalSummarySpec extends TestSetup with WithFakeApplication with FileResource {

  "tax credits summary live" should {
    "process the request successfully and filter children older than 20 and where deceased flags are active" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L200)
      stubTaxCreditBrokerConnectorGetChildren(Children(Seq(SarahSmith, JosephSmith, MarySmith, JennySmith, PeterSmith, SimonSmith)))
      stubTaxCreditBrokerConnectorGetPartnerDetails(Some(partnerDetails(nino)))
      stubTaxCreditBrokerConnectorGetPersonalDetails(personalDetails(nino))
      stubTaxCreditBrokerConnectorGetPaymentSummary(paymentSummary)
      val expectedResult = TaxCreditSummary(paymentSummary, personalDetails(nino), Some(partnerDetails(nino)), Children(Seq(SarahSmith, JosephSmith, MarySmith)))
      override val mockLivePersonalIncomeService = new TestPersonalIncomeService(mockNtcConnector, mockTaiConnector,
        mockPersonalTaxSummaryConnector, mockTaxCreditsBrokerConnector, mockAuditConnector, mockConfiguration, mockAppContext)
      val controller = new TestPersonalIncomeController(mockAuthConnector, mockLivePersonalIncomeService, 200, logger)
      val result: Result = await(controller.taxCreditsSummary(Nino(nino))(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, Nino(nino))))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe toJson(expectedResult)
    }

    "return 401 when the nino in the request does not match the authority nino" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L200)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      status(await(controller.taxCreditsSummary(incorrectNino)(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, Nino(nino))))) shouldBe 401
    }

    "return 429 HTTP status when retrieval of children returns 503" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L200)
      when(mockTaxCreditsBrokerConnector.getChildren(any[TaxCreditsNino]())(any[HeaderCarrier](), any[ExecutionContext]()))
        .thenReturn(Future.failed(new ServiceUnavailableException("controlled explosion kaboom!!")))
      override val mockLivePersonalIncomeService = new TestPersonalIncomeService(mockNtcConnector, mockTaiConnector,
        mockPersonalTaxSummaryConnector, mockTaxCreditsBrokerConnector, mockAuditConnector, mockConfiguration, mockAppContext)
      val controller = new TestPersonalIncomeController(mockAuthConnector, mockLivePersonalIncomeService, 200, logger)
      status(await(controller.taxCreditsSummary(Nino(nino))(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, Nino(nino))))) shouldBe 429
    }

    "return the summary successfully when journeyId is supplied" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L200)
      stubTaxCreditBrokerConnectorGetChildren(Children(Seq(SarahSmith, JosephSmith, MarySmith, JennySmith, PeterSmith, SimonSmith)))
      stubTaxCreditBrokerConnectorGetPartnerDetails(Some(partnerDetails(nino)))
      stubTaxCreditBrokerConnectorGetPersonalDetails(personalDetails(nino))
      stubTaxCreditBrokerConnectorGetPaymentSummary(paymentSummary)
      val expectedResult = TaxCreditSummary(paymentSummary, personalDetails(nino), Some(partnerDetails(nino)), Children(Seq(SarahSmith, JosephSmith, MarySmith)))
      override val mockLivePersonalIncomeService = new TestPersonalIncomeService(mockNtcConnector, mockTaiConnector,
        mockPersonalTaxSummaryConnector, mockTaxCreditsBrokerConnector, mockAuditConnector, mockConfiguration, mockAppContext)
      val controller = new TestPersonalIncomeController(mockAuthConnector, mockLivePersonalIncomeService, 200, logger)
      val result: Result = await(controller.taxCreditsSummary(Nino(nino), Some("unique-journey-id"))(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, Nino(nino))))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe toJson(expectedResult)
    }

    "return unauthorized when authority record does not contain a NINO" in new mocks {
      stubAuthorisationGrantAccess(None and L200)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      val result: Result = await(controller.taxCreditsSummary(Nino(nino))(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, Nino(nino))))
      status(result) shouldBe 401
      contentAsJson(result) shouldBe noNinoFoundOnAccount
    }

    "return unauthorized when authority record has a low CL" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L100)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      val result: Result = await(controller.taxCreditsSummary(Nino(nino))(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, Nino(nino))))
      status(result) shouldBe 401
      contentAsJson(result) shouldBe lowConfidenceLevelError
    }

    "return status code 406 when the headers are invalid" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L200)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      val result: Result = await(controller.taxCreditsSummary(Nino(nino))(requestInvalidHeaders))
      status(result) shouldBe 406
    }
  }

  "tax credits summary Sandbox" should {
    "return the summary response from a resource" in new mocks {
      val controller = new SandboxPersonalIncomeController(mockAuthConnector, 200, logger)
      val result: Result = await(controller.taxCreditsSummary(Nino(nino)).apply(fakeRequest))
      contentAsJson(result) shouldBe Json.parse(findResource(s"/resources/taxcreditsummary/$nino.json").get)
    }
  }

}

class TestExclusionsServiceSpec extends TestSetup with WithFakeApplication {

  "tax exclusions service" should {
    "process the request for get tax credit exclusion successfully" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L200)
      stubTaxCreditBrokerConnectorGetExclusion(Exclusion(false))
      override implicit val mockLivePersonalIncomeService: TestPersonalIncomeService = new TestPersonalIncomeService(mockNtcConnector, mockTaiConnector,
        mockPersonalTaxSummaryConnector, mockTaxCreditsBrokerConnector, mockAuditConnector, mockConfiguration, mockAppContext)
      val controller = new TestPersonalIncomeController(mockAuthConnector, mockLivePersonalIncomeService, 200, logger)
      val result: Result = await(controller.getTaxCreditExclusion(Nino(nino)).apply(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, Nino(nino))))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe Json.parse("""{"showData":true}""")

    }

    "return 401 when the nino in the request does not match the authority nino" in new mocks {
      stubAuthorisationGrantAccess(None and L200)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      val result: Result = await(controller.getTaxCreditExclusion(Nino(nino))(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, Nino(nino))))
      status(result) shouldBe 401
      contentAsJson(result) shouldBe noNinoFoundOnAccount
    }

    "return 429 HTTP status when get tax credit exclusion returns 503" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L200)
      when(mockTaxCreditsBrokerConnector.getExclusion(any[TaxCreditsNino]())(any[HeaderCarrier](), any[ExecutionContext]()))
        .thenReturn(Future.failed(new ServiceUnavailableException("controlled explosion kaboom!!")))
      override val mockLivePersonalIncomeService = new TestPersonalIncomeService(mockNtcConnector, mockTaiConnector,
        mockPersonalTaxSummaryConnector, mockTaxCreditsBrokerConnector, mockAuditConnector, mockConfiguration, mockAppContext)
      val controller = new TestPersonalIncomeController(mockAuthConnector, mockLivePersonalIncomeService, 200, logger)
      status(await(controller.getTaxCreditExclusion(Nino(nino))(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, Nino(nino))))) shouldBe 429
    }

    "return the tax credit exclusion successfully when journeyId is supplied" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L200)
      stubTaxCreditBrokerConnectorGetExclusion(Exclusion(false))
      override implicit val mockLivePersonalIncomeService: TestPersonalIncomeService = new TestPersonalIncomeService(mockNtcConnector, mockTaiConnector,
        mockPersonalTaxSummaryConnector, mockTaxCreditsBrokerConnector, mockAuditConnector, mockConfiguration, mockAppContext)
      val controller = new TestPersonalIncomeController(mockAuthConnector, mockLivePersonalIncomeService, 200, logger)
      val result: Result = await(controller.getTaxCreditExclusion(Nino(nino), Some("unique-journey-id"))
        .apply(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, Nino(nino))))
      status(result) shouldBe 200
      contentAsJson(result) shouldBe Json.parse("""{"showData":true}""")
    }

    "return unauthorized when authority record does not contain a NINO" in new mocks {
      stubAuthorisationGrantAccess(None and L200)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      val result: Result = await(controller.getTaxCreditExclusion(Nino(nino))(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, Nino(nino))))
      status(result) shouldBe 401
      contentAsJson(result) shouldBe noNinoFoundOnAccount
    }

    "return unauthorized when authority record has a low Confidence Level" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L100)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      val result: Result = await(controller.getTaxCreditExclusion(Nino(nino))(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, Nino(nino))))
      status(result) shouldBe 401
      contentAsJson(result) shouldBe lowConfidenceLevelError
    }

    "return status code 406 when the headers are invalid" in new mocks {
      stubAuthorisationGrantAccess(Some(nino) and L200)
      val controller = new LivePersonalIncomeController(mockAuthConnector, 200, logger,
        mockLivePersonalIncomeService, mockTaxCreditsSubmissionControlConfig)
      val result: Result = await(controller.getTaxCreditExclusion(Nino(nino))(requestInvalidHeaders))
      status(result) shouldBe 406
    }
  }
}