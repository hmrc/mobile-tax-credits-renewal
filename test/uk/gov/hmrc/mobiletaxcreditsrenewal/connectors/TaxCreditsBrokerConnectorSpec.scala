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

package uk.gov.hmrc.mobiletaxcreditsrenewal.connectors

import org.scalamock.handlers.CallHandler
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._
import uk.gov.hmrc.mobiletaxcreditsrenewal.utils.BaseSpec

import java.net.URL
import scala.concurrent.{ExecutionContext, Future}

class TaxCreditsBrokerConnectorSpec extends BaseSpec {

  import scala.concurrent.ExecutionContext.Implicits.global

  private trait Setup {

    val employedEarningsRtiJson: String =
      """{
        |   "previousYearRTIEmployedEarnings": 20000.0,
        |   "previousYearRTIEmployedEarningsPartner": 20000.0
        |}""".stripMargin

    val employedEarningsRti200Success: JsValue = Json.parse(employedEarningsRtiJson)

    lazy val http200EmployedEarningsRtiResponse: Future[AnyRef with HttpResponse] =
      Future.successful(HttpResponse(200, employedEarningsRtiJson))

    lazy val response: Future[HttpResponse] = http200EmployedEarningsRtiResponse

    val connector = new TaxCreditsBrokerConnector(mockHttpClient, serviceUrl)

    def employedEarningsRtiGet[T](nino: Nino): CallHandler[Future[T]] = {
      (mockHttpClient
        .get(_: URL)(_: HeaderCarrier))
        .expects(url"${s"$serviceUrl/tcs/${nino.value}/employed-earnings-rti"}", *)
        .returns(mockRequestBuilder)

      (mockRequestBuilder
        .execute[T](_: HttpReads[T], _: ExecutionContext))
        .expects(*, *)

    }
  }

  "employedEarningsRti" should {

    "return a valid EmployedEarningsRti object when a 200 response is received with a valid json payload" in new Setup {
      employedEarningsRtiGet(nino).returns(Future successful Some(employedEarningsRti200Success))
      val result: Option[EmployedEarningsRti] = await(connector.employedEarningsRti(taxCreditNino))

      result shouldBe Some(employedEarningsRti200Success)
    }

    "return None when a 400 response is received" in new Setup {
      employedEarningsRtiGet(nino).returns(http400Response)
      val result: Option[EmployedEarningsRti] = await(connector.employedEarningsRti(taxCreditNino))

      result shouldBe None
    }

    "return None when a 404 response is received" in new Setup {
      employedEarningsRtiGet(nino).returns(http404Response)
      val result: Option[EmployedEarningsRti] = await(connector.employedEarningsRti(taxCreditNino))

      result shouldBe None
    }

    "return None when a 500 response is received" in new Setup {
      employedEarningsRtiGet(nino).returns(http500Response)
      val result: Option[EmployedEarningsRti] = await(connector.employedEarningsRti(taxCreditNino))

      result shouldBe None
    }

  }

}
