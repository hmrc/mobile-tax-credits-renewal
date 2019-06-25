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

package uk.gov.hmrc.mobiletaxcreditsrenewal.connectors

import akka.actor.ActorSystem
import com.typesafe.config.Config
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpecLike}
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.hooks.HttpHook
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._

import scala.concurrent.Future

class TaxCreditsBrokerConnectorSpec extends WordSpecLike with Matchers with ScalaFutures with MockFactory {

  import scala.concurrent.ExecutionContext.Implicits.global

  private trait Setup {

    implicit lazy val hc: HeaderCarrier = HeaderCarrier()

    val nino = Nino("KM569110B")
    val taxCreditNino = TaxCreditsNino(nino.value)

    val employedEarningsRtiJson: String =
      """{
        |   "previousYearRTIEmployedEarnings": 20000.0,
        |   "previousYearRTIEmployedEarningsPartner": 20000.0
        |}""".stripMargin

    val employedEarningsRti200Success: JsValue = Json.parse(employedEarningsRtiJson)
    lazy val http200EmployedEarningsRtiResponse: Future[AnyRef with HttpResponse] = Future.successful(HttpResponse(200, Some(employedEarningsRti200Success)))
    lazy val http500Response: Future[Nothing] = Future.failed(Upstream5xxResponse("Error", 500, 500))
    lazy val http400Response: Future[Nothing] = Future.failed(new BadRequestException("bad request"))
    lazy val http404Response: Future[AnyRef with HttpResponse] = Future.successful(HttpResponse(404))
    lazy val response: Future[HttpResponse] = http200EmployedEarningsRtiResponse

    val serviceUrl = "someUrl"
    val http: CoreGet = new CoreGet with HttpGet {
      override val hooks: Seq[HttpHook] = NoneRequired

      override def configuration: Option[Config] = None

      override def doGet(url: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = response

      override protected def actorSystem: ActorSystem = ActorSystem()
    }

    class TestTaxCreditsBrokerConnector(http: CoreGet)
      extends TaxCreditsBrokerConnector(http, serviceUrl)

    val connector = new TestTaxCreditsBrokerConnector(http)
  }

  "employedEarningsRti" should {

    "return a valid EmployedEarningsRti object when a 200 response is received with a valid json payload" in new Setup {
      override lazy val response: Future[AnyRef with HttpResponse] = http200EmployedEarningsRtiResponse
      val result: Option[EmployedEarningsRti] = await(connector.employedEarningsRti(taxCreditNino))

      result shouldBe Some(toJson(employedEarningsRti200Success).as[EmployedEarningsRti])
    }

    "return None when a 400 response is received" in new Setup {
      override lazy val response: Future[Nothing] = http400Response
      val result: Option[EmployedEarningsRti] = await(connector.employedEarningsRti(taxCreditNino))

      result shouldBe None
    }

    "return None when a 404 response is received" in new Setup {
      override lazy val response: Future[AnyRef with HttpResponse] = http404Response
      val result: Option[EmployedEarningsRti] = await(connector.employedEarningsRti(taxCreditNino))

      result shouldBe None
    }

    "return None when a 500 response is received" in new Setup {
      override lazy val response: Future[AnyRef with HttpResponse] = http500Response
      val result: Option[EmployedEarningsRti] = await(connector.employedEarningsRti(taxCreditNino))

      result shouldBe None
    }

  }

}