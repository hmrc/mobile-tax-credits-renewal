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

package uk.gov.hmrc.mobiletaxcreditsrenewal.controllers

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
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._
import uk.gov.hmrc.mobiletaxcreditsrenewal.services.MobileTaxCreditsRenewalService
import uk.gov.hmrc.mobiletaxcreditsrenewal.stubs.{AuthorisationStub, MobileTaxCreditsRenewalServiceStub}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class SandboxLegacyMobileTaxCreditsRenewalControllerSpec
    extends WordSpecLike
    with Matchers
    with MockFactory
    with AuthorisationStub
    with MobileTaxCreditsRenewalServiceStub
    with ClaimsJson {
  implicit val authConnector:     AuthConnector                  = mock[AuthConnector]
  implicit val mockControlConfig: TaxCreditsControl              = mock[TaxCreditsControl]
  implicit val service:           MobileTaxCreditsRenewalService = mock[MobileTaxCreditsRenewalService]

  private val logger = new LoggerLike {
    override val logger: Logger = getLogger("LiveMobileTaxCreditsRenewalControllerSpec")
  }

  private val nino          = Nino("CS700100A")
  private val incorrectNino = Nino("SC100700A")
  private val journeyId     = "journeyId"
  private val forbidden:         JsValue         = parse("""{"code":"FORBIDDEN","message":"Forbidden"}""")

  private val controller =
    new SandboxLegacyMobileTaxCreditsRenewalController(stubControllerComponents())

  private val acceptHeader: (String, String) = "Accept" -> "application/vnd.hmrc.1.0+json"

  val renewalReference = RenewalReference("200000000000013")

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

  def encodedAuth(nino: Nino, tcrRenewalReference: RenewalReference): String =
    new String(encodeBase64(s"${nino.value}:${tcrRenewalReference.value}".getBytes))

  def emptyRequestWithAcceptHeaderAndAuthHeader(renewalsRef: RenewalReference, nino: Nino): FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest().withHeaders(acceptHeader, HeaderKeys.tcrAuthToken -> basicAuthString(encodedAuth(nino, renewalsRef)))

  "getRenewalAuthentication" should {
    "process the authentication successfully" in {

      val result = controller.getRenewalAuthentication(nino, renewalReference, journeyId).apply(fakeRequest)
      status(result)        shouldBe 200
      contentAsJson(result) shouldBe toJson(TcrAuthenticationToken("Basic " + encodedAuth(nino, renewalReference)))
    }

    "return unauthorised when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .getRenewalAuthentication(incorrectNino, renewalReference, journeyId)
          .apply(fakeRequest.withHeaders("SANDBOX-CONTROL" -> "ERROR-401"))) shouldBe 401
    }

    "return forbidden when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .getRenewalAuthentication(incorrectNino, renewalReference, journeyId)
          .apply(fakeRequest.withHeaders("SANDBOX-CONTROL" -> "ERROR-403"))) shouldBe 403
    }

    "return not found when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .getRenewalAuthentication(incorrectNino, renewalReference, journeyId)
          .apply(fakeRequest.withHeaders("SANDBOX-CONTROL" -> "ERROR-404"))) shouldBe 404
    }

    "return internal sever error when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .getRenewalAuthentication(incorrectNino, renewalReference, journeyId)
          .apply(fakeRequest.withHeaders("SANDBOX-CONTROL" -> "ERROR-500"))) shouldBe 500
    }

  }

  "claimantDetails" should {
    "return claimant details successfully" in {
      val claimantDetails = ClaimantDetails(hasPartner = false, 1, "r", nino.nino, None, availableForCOCAutomation = false, "some-app-id")

      val result = controller.claimantDetails(nino, journeyId).apply(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, nino))

      status(result)                  shouldBe 200
      contentAsJson(result)           shouldBe toJson(claimantDetails.copy(mainApplicantNino = "false"))
      header("Cache-Control", result) shouldBe None
    }

    "return unauthorised when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .claimantDetails(nino, journeyId)
          .apply(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, nino).withHeaders("SANDBOX-CONTROL" -> "ERROR-401"))) shouldBe 401
    }

    "return forbidden when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .claimantDetails(nino, journeyId)
          .apply(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, nino).withHeaders("SANDBOX-CONTROL" -> "ERROR-403"))) shouldBe 403
    }

    "return not found when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .claimantDetails(nino, journeyId)
          .apply(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, nino).withHeaders("SANDBOX-CONTROL" -> "ERROR-404"))) shouldBe 404
    }

    "return internal sever error when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .claimantDetails(nino, journeyId)
          .apply(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, nino).withHeaders("SANDBOX-CONTROL" -> "ERROR-500"))) shouldBe 500
    }

  }

  "fullClaimantDetails" should {

    "return details with the renewalFormType set" in {
      val expectedClaimDetails = LegacyClaim(
        Household(renewalReference.value, "198765432134567", Applicant(nino.nino, "MR", "JOHN", Some(""), "DENSMORE", Some(19500.00)), None, None, Some("")),
        LegacyRenewal(Some("12/10/2030"), Some("12/10/2010"), Some("NOT_SUBMITTED"), Some("12/10/2030"), Some("12/10/2010"), Some("D"))
      )

      val result = controller.fullClaimantDetails(nino, journeyId)(fakeRequest)
      status(result)        shouldBe 200
      contentAsJson(result) shouldBe toJson(LegacyClaims(Some(Seq(expectedClaimDetails))))
    }

    "return unauthorised when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .fullClaimantDetails(nino, journeyId)
          .apply(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, nino).withHeaders("SANDBOX-CONTROL" -> "ERROR-401"))) shouldBe 401
    }

    "return forbidden when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .fullClaimantDetails(nino, journeyId)
          .apply(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, nino).withHeaders("SANDBOX-CONTROL" -> "ERROR-403"))) shouldBe 403
    }

    "return not found when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .fullClaimantDetails(nino, journeyId)
          .apply(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, nino).withHeaders("SANDBOX-CONTROL" -> "ERROR-404"))) shouldBe 404
    }

    "return internal sever error when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .fullClaimantDetails(nino, journeyId)
          .apply(emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, nino).withHeaders("SANDBOX-CONTROL" -> "ERROR-500"))) shouldBe 500
    }

  }

  "submitRenewal" should {
    val incomeDetails   = IncomeDetails(Some(10), Some(20), Some(30), Some(40), Some(true))
    val certainBenefits = CertainBenefits(receivedBenefits = false, incomeSupport = false, jsa = false, esa = false, pensionCredit = false)
    val otherIncome     = OtherIncome(Some(100), Some(false))
    val renewal = TcrRenewal(
      RenewalData(Some(incomeDetails), Some(incomeDetails), Some(certainBenefits)),
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
      status(controller.submitRenewal(nino, journeyId).apply(submitRenewalRequest)) shouldBe 200
    }

    "return unauthorised when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .submitRenewal(nino, journeyId)
          .apply(submitRenewalRequest.withHeaders("SANDBOX-CONTROL" -> "ERROR-401"))) shouldBe 401
    }

    "return forbidden when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .submitRenewal(nino, journeyId)
          .apply(submitRenewalRequest.withHeaders("SANDBOX-CONTROL" -> "ERROR-403"))) shouldBe 403
    }

    "return not found when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .submitRenewal(nino, journeyId)
          .apply(submitRenewalRequest.withHeaders("SANDBOX-CONTROL" -> "ERROR-404"))) shouldBe 404
    }

    "return internal sever error when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .submitRenewal(nino, journeyId)
          .apply(submitRenewalRequest.withHeaders("SANDBOX-CONTROL" -> "ERROR-500"))) shouldBe 500
    }

  }

  "taxCreditsSubmissionStateEnabled" should {
    "return an open submission state when directed to do so using the SANDBOX-CONTROL header" in {
      val result = controller.taxCreditsSubmissionStateEnabled(journeyId).apply(fakeRequest)
      status(result)        shouldBe 200
      contentAsJson(result) shouldBe Json.parse("""{"submissionsState":"open"}""")
    }

    "return an closed submission state when directed to do so using the SANDBOX-CONTROL header" in {
      val result = controller.taxCreditsSubmissionStateEnabled(journeyId).apply(fakeRequest.withHeaders("SANDBOX-CONTROL" -> "CLOSED"))
      status(result)        shouldBe 200
      contentAsJson(result) shouldBe Json.parse("""{"submissionsState":"closed"}""")
    }

    "return an check_status_only submission state when directed to do so using the SANDBOX-CONTROL header" in {
      val result = controller.taxCreditsSubmissionStateEnabled(journeyId).apply(fakeRequest.withHeaders("SANDBOX-CONTROL" -> "CHECK-STATUS-ONLY"))
      status(result)        shouldBe 200
      contentAsJson(result) shouldBe Json.parse("""{"submissionsState":"check_status_only"}""")
    }

    "return unauthorised when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .taxCreditsSubmissionStateEnabled(journeyId)
          .apply(fakeRequest.withHeaders("SANDBOX-CONTROL" -> "ERROR-401"))) shouldBe 401
    }

    "return forbidden when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .taxCreditsSubmissionStateEnabled(journeyId)
          .apply(fakeRequest.withHeaders("SANDBOX-CONTROL" -> "ERROR-403"))) shouldBe 403
    }

    "return not found when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .taxCreditsSubmissionStateEnabled(journeyId)
          .apply(fakeRequest.withHeaders("SANDBOX-CONTROL" -> "ERROR-404"))) shouldBe 404
    }

    "return internal sever error when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .taxCreditsSubmissionStateEnabled(journeyId)
          .apply(fakeRequest.withHeaders("SANDBOX-CONTROL" -> "ERROR-500"))) shouldBe 500
    }
  }

}
