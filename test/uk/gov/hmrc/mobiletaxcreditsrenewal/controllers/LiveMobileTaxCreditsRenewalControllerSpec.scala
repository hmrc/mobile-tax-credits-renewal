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

import org.scalamock.scalatest.MockFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import play.api.LoggerLike
import play.api.libs.json.JsValue
import play.api.libs.json.Json.{parse, toJson}
import play.api.mvc.{AnyContentAsEmpty, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.ConfidenceLevel._
import uk.gov.hmrc.auth.core.syntax.retrieved._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._
import uk.gov.hmrc.mobiletaxcreditsrenewal.services.MobileTaxCreditsRenewalService
import uk.gov.hmrc.mobiletaxcreditsrenewal.stubs.AuthorisationStub
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.{ExecutionContext, Future}

class LiveMobileTaxCreditsRenewalControllerSpec
  extends UnitSpec with MockFactory with WithFakeApplication with AuthorisationStub{
  implicit val authConnector: AuthConnector = mock[AuthConnector]
  private val service = mock[MobileTaxCreditsRenewalService]

  private val logger = new LoggerLike {
    override val logger: Logger = getLogger("LiveMobileTaxCreditsRenewalControllerSpec")
  }

  private val nino = Nino("CS700100A")
  private val journeyId= "journeyId"

  private val controller = new LiveMobileTaxCreditsRenewalController(authConnector, logger, service, L200.level)

  private val acceptHeader: (String, String) = "Accept" -> "application/vnd.hmrc.1.0+json"

  "renewals" should {
    lazy val fakeRequest: FakeRequest[AnyContentAsEmpty.type] =
      FakeRequest().withSession("AuthToken" -> "Some Header").withHeaders(acceptHeader, "Authorization" -> "Some Header")

    "return the renewals summary from the service for an authorised user with the right nino and a L200 confidence level" in {
      val renewals = RenewalsSummary("closed", None)

      def mockServiceCall(): Unit =
        (service.renewals(_: Nino, _: Option[String])(_: HeaderCarrier, _: ExecutionContext, _: Request[_])).
          expects(nino, Some(journeyId), *, *, *).returning(Future successful renewals)

      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      mockServiceCall()

      val result: Future[Result] = await(controller.renewals(nino,Some(journeyId))).apply(fakeRequest)
      status(result) shouldBe 200
      contentAsJson(result) shouldBe parse("""{"submissionsState":"closed"}""")
    }

    "return forbidden for a user with L100 confidence level" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L100)
      status(await(controller.renewals(nino, Some(journeyId))).apply(fakeRequest)) shouldBe 403
    }

    "return forbidden when trying to access another users nino" in {
      stubAuthorisationGrantAccess(Some("AM242413B") and L200)
      status(await(controller.renewals(nino, Some(journeyId))).apply(fakeRequest)) shouldBe 403
    }

    "return unauthoirsed for an unauthorised user" in {
      stubAuthorisationUnauthorised()
      status(await(controller.renewals(nino, Some(journeyId))).apply(fakeRequest)) shouldBe 401
    }
  }

  "submitDeclarations" should {
    val incomeDetails = IncomeDetails(Some(10), Some(20), Some(30), Some(40), Some(true))
    val certainBenefits =
      CertainBenefits(receivedBenefits = false, incomeSupport = false, jsa = false, esa = false, pensionCredit = false)
    val otherIncome = OtherIncome(Some(100), Some(false))
    val renewal = TcrRenewal(RenewalData(Some(incomeDetails), Some(incomeDetails),
      Some(certainBenefits)), None, Some(otherIncome), Some(otherIncome), hasChangeOfCircs = false)

    val submitRenewalRequest: FakeRequest[JsValue] = FakeRequest().withBody(toJson(renewal)).withHeaders(
      acceptHeader,
      HeaderKeys.tcrAuthToken -> "some-auth-token"
    )

    def mockServiceCall(): Unit =
      (service.submitRenewal(_: Nino, _: TcrRenewal)(_: HeaderCarrier, _: ExecutionContext, _: Request[_])).
        expects(nino, renewal, *, *, *).returning(Future successful 200)

    "submit a valid form for an authorised user with the right nino and a L200 confidence level when renewals are open" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      mockServiceCall()

      status(await(controller.submitRenewal(nino, Some(journeyId))).apply(submitRenewalRequest)) shouldBe 200
    }

    "return forbidden for a user with L100 confidence level" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L100)
      status(await(controller.submitRenewal(nino, Some(journeyId))).apply(submitRenewalRequest)) shouldBe 403
    }

    "return forbidden when trying to access another users nino" in {
      stubAuthorisationGrantAccess(Some("AM242413B") and L200)
      status(await(controller.submitRenewal(nino, Some(journeyId))).apply(submitRenewalRequest)) shouldBe 403
    }

    "return unauthoirsed for an unauthorised user" in {
      stubAuthorisationUnauthorised()
      status(await(controller.submitRenewal(nino, Some(journeyId))).apply(submitRenewalRequest)) shouldBe 401
    }
  }
}
