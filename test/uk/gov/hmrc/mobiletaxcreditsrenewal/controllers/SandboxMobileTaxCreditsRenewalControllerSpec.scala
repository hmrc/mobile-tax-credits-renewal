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

import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpecLike}
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import play.api.LoggerLike
import play.api.libs.json.JsValue
import play.api.libs.json.Json.{parse, toJson}
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.api.sandbox.FileResource
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SandboxMobileTaxCreditsRenewalControllerSpec extends WordSpecLike with Matchers with MockFactory with FileResource {
  private val logger = new LoggerLike {
    override val logger: Logger = getLogger("SandboxMobileTaxCreditsRenewalController")
  }

  private val nino      = Nino("CS700100A")
  private val journeyId = "journeyId"

  private val controller = new SandboxMobileTaxCreditsRenewalController(logger, stubControllerComponents())

  private val acceptHeader: (String, String) = "Accept" -> "application/vnd.hmrc.1.0+json"

  "renewals" should {
    lazy val fakeRequestWithoutHeaders: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    lazy val fakeRequest:               FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withHeaders(acceptHeader)

    "return the open renewals summary by default" in {
      val expectedValue = parse(findResource("/resources/claimantdetails/renewals-response-open.json").get)

      val response = controller.renewals(nino, journeyId).apply(fakeRequest)
      status(response)        shouldBe 200
      contentAsJson(response) shouldBe expectedValue
    }

    "return a closed response when directed to do so using the SANDBOX-CONTROL header" in {
      val expectedValue = parse(findResource("/resources/claimantdetails/renewals-response-closed.json").get)

      val response: Future[Result] =
        controller.renewals(nino, journeyId).apply(fakeRequest.withHeaders("SANDBOX-CONTROL" -> "RENEWALS-RESPONSE-CLOSED"))
      status(response)        shouldBe 200
      contentAsJson(response) shouldBe expectedValue
    }

    "return a check-only response when directed to do so using the SANDBOX-CONTROL header" in {
      val expectedValue = parse(findResource("/resources/claimantdetails/renewals-response-check-status-only.json").get)

      val response: Future[Result] =
        controller.renewals(nino, journeyId).apply(fakeRequest.withHeaders("SANDBOX-CONTROL" -> "RENEWALS-RESPONSE-CHECK-STATUS-ONLY"))
      status(response)        shouldBe 200
      contentAsJson(response) shouldBe expectedValue
    }

    "return unauthorised when directed to do so using the SANDBOX-CONTROL header" in {
      status(controller.renewals(nino, journeyId).apply(fakeRequest.withHeaders("SANDBOX-CONTROL" -> "ERROR-401"))) shouldBe 401
    }

    "return forbidden when directed to do so using the SANDBOX-CONTROL header" in {
      status(controller.renewals(nino, journeyId).apply(fakeRequest.withHeaders("SANDBOX-CONTROL" -> "ERROR-403"))) shouldBe 403
    }

    "return not found when directed to do so using the SANDBOX-CONTROL header" in {
      status(controller.renewals(nino, journeyId).apply(fakeRequest.withHeaders("SANDBOX-CONTROL" -> "ERROR-404"))) shouldBe 404
    }

    "return internal server error when directed to do so using the SANDBOX-CONTROL header" in {
      status(controller.renewals(nino, journeyId).apply(fakeRequest.withHeaders("SANDBOX-CONTROL" -> "ERROR-500"))) shouldBe 500
    }

    "return 406 if accept header not set" in {
      status(controller.renewals(nino, journeyId).apply(fakeRequestWithoutHeaders)) shouldBe 406
    }
  }

  "submitDeclarations" should {
    val incomeDetails = IncomeDetails(Some(10), Some(20), Some(30), Some(40), Some(true))
    val certainBenefits =
      CertainBenefits(receivedBenefits = false, incomeSupport = false, jsa = false, esa = false, pensionCredit = false)
    val otherIncome = OtherIncome(Some(100), Some(false))
    val renewal = TcrRenewal(
      RenewalData(Some(incomeDetails), Some(incomeDetails), Some(certainBenefits)),
      None,
      Some(otherIncome),
      Some(otherIncome),
      hasChangeOfCircs = false)

    val submitRenewalRequest:                    FakeRequest[JsValue] = FakeRequest().withBody(toJson(renewal)).withHeaders(acceptHeader)
    val submitRenewalRequestWithoutAcceptHeader: FakeRequest[JsValue] = FakeRequest().withBody(toJson(renewal))

    "submit a valid form for an authorised user with the right nino and a L200 confidence level when renewals are open" in {
      status(controller.submitRenewal(nino, journeyId).apply(submitRenewalRequest)) shouldBe 200
    }

    "return unauthorised when directed to do so using the SANDBOX-CONTROL header" in {
      status(controller.submitRenewal(nino, journeyId).apply(submitRenewalRequest.withHeaders("SANDBOX-CONTROL" -> "ERROR-401"))) shouldBe 401
    }

    "return forbidden when directed to do so using the SANDBOX-CONTROL header" in {
      status(controller.submitRenewal(nino, journeyId).apply(submitRenewalRequest.withHeaders("SANDBOX-CONTROL" -> "ERROR-403"))) shouldBe 403
    }

    "return not found when directed to do so using the SANDBOX-CONTROL header" in {
      status(controller.submitRenewal(nino, journeyId).apply(submitRenewalRequest.withHeaders("SANDBOX-CONTROL" -> "ERROR-404"))) shouldBe 404
    }

    "return internal server error when directed to do so using the SANDBOX-CONTROL header" in {
      status(controller.submitRenewal(nino, journeyId).apply(submitRenewalRequest.withHeaders("SANDBOX-CONTROL" -> "ERROR-500"))) shouldBe 500
    }

    "return 406 if accept header not set" in {
      status(controller.submitRenewal(nino, journeyId).apply(submitRenewalRequestWithoutAcceptHeader)) shouldBe 406
    }
  }
}
