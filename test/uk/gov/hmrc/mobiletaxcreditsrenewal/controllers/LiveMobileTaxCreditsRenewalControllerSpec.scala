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
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpecLike}
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
import uk.gov.hmrc.mobiletaxcreditsrenewal.connectors.ShutteringConnector
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.types.ModelTypes.JourneyId
import uk.gov.hmrc.mobiletaxcreditsrenewal.services.MobileTaxCreditsRenewalService
import uk.gov.hmrc.mobiletaxcreditsrenewal.stubs.{AuthorisationStub, ShutteringMock}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class LiveMobileTaxCreditsRenewalControllerSpec
    extends WordSpecLike
    with Matchers
    with MockFactory
    with AuthorisationStub
    with ShutteringMock {
  implicit val authConnector: AuthConnector = mock[AuthConnector]
  private val service = mock[MobileTaxCreditsRenewalService]
  implicit val shutteringConnector: ShutteringConnector = mock[ShutteringConnector]

  private val logger = new LoggerLike {
    override val logger: Logger = getLogger("LiveMobileTaxCreditsRenewalControllerSpec")
  }

  private val nino = Nino("CS700100A")
  private val journeyId: JourneyId = "87144372-6bda-4cc9-87db-1d52fd96498f"

  private val controller = new LiveMobileTaxCreditsRenewalController(authConnector,
                                                                     logger,
                                                                     service,
                                                                     L200.level,
                                                                     stubControllerComponents(),
                                                                     shutteringConnector)

  private val acceptHeader: (String, String) = "Accept" -> "application/vnd.hmrc.1.0+json"

  "renewals" should {
    lazy val fakeRequest: FakeRequest[AnyContentAsEmpty.type] =
      FakeRequest()
        .withSession("AuthToken" -> "Some Header")
        .withHeaders(acceptHeader, "Authorization" -> "Some Header")

    "return the renewals summary from the service for an authorised user with the right nino and a L200 confidence level" in {
      val renewals = RenewalsSummary("closed", None)

      def mockServiceCall(): Unit =
        (service
          .renewals(_: Nino, _: JourneyId)(_: HeaderCarrier, _: ExecutionContext, _: Request[_]))
          .expects(nino, journeyId, *, *, *)
          .returning(Future successful renewals)

      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      mockServiceCall()
      mockShutteringResponse(false)

      val result: Future[Result] = controller.renewals(nino, journeyId).apply(fakeRequest)
      status(result)        shouldBe 200
      contentAsJson(result) shouldBe parse("""{"submissionsState":"closed"}""")
    }

    "return forbidden for a user with L100 confidence level" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L100)
      status(controller.renewals(nino, journeyId).apply(fakeRequest)) shouldBe 403
    }

    "return forbidden when trying to access another users nino" in {
      stubAuthorisationGrantAccess(Some("AM242413B") and L200)
      status(controller.renewals(nino, journeyId).apply(fakeRequest)) shouldBe 403
    }

    "return unauthoirsed for an unauthorised user" in {
      stubAuthorisationUnauthorised()
      status(controller.renewals(nino, journeyId).apply(fakeRequest)) shouldBe 401
    }
  }

  "submitDeclarations" should {
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

    def mockServiceCall(): Unit =
      (service
        .submitRenewal(_: Nino, _: TcrRenewal)(_: HeaderCarrier, _: ExecutionContext, _: Request[_]))
        .expects(nino, renewal, *, *, *)
        .returning(Future successful 200)

    "submit a valid form for an authorised user with the right nino and a L200 confidence level when renewals are open" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L200)
      mockServiceCall()
      mockShutteringResponse(false)

      status(controller.submitRenewal(nino, journeyId).apply(submitRenewalRequest)) shouldBe 200
    }

    "return forbidden for a user with L100 confidence level" in {
      stubAuthorisationGrantAccess(Some(nino.nino) and L100)
      status(controller.submitRenewal(nino, journeyId).apply(submitRenewalRequest)) shouldBe 403
    }

    "return forbidden when trying to access another users nino" in {
      stubAuthorisationGrantAccess(Some("AM242413B") and L200)
      status(controller.submitRenewal(nino, journeyId).apply(submitRenewalRequest)) shouldBe 403
    }

    "return unauthorised for an unauthorised user" in {
      stubAuthorisationUnauthorised()
      status(controller.submitRenewal(nino, journeyId).apply(submitRenewalRequest)) shouldBe 401
    }

  }
}
