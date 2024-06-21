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

import com.typesafe.config.Config
import org.apache.pekko.actor.ActorSystem
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.test.Helpers._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.circuitbreaker.CircuitBreakerConfig
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.hooks.HttpHook
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._

import scala.concurrent.{ExecutionContext, Future}

class NtcConnectorSpec
    extends AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with CircuitBreakerTest
    with MockFactory {

  import scala.concurrent.ExecutionContext.Implicits.global

  private trait Setup {

    implicit lazy val hc: HeaderCarrier = HeaderCarrier()

    val nino             = Nino("KM569110B")
    val taxCreditNino    = TaxCreditsNino(nino.value)
    val renewalReference = RenewalReference("123456")
    val tcrAuthToken     = TcrAuthenticationToken("some-token")

    val claimantDetails = ClaimantDetails(hasPartner = false,
                                          1,
                                          "renewalForm",
                                          nino.value,
                                          None,
                                          availableForCOCAutomation = false,
                                          "some-app-id")

    val claimsJson: String =
      """{
        |  "references": [
        |    {
        |      "household": {
        |        "barcodeReference": "200000000000012",
        |        "applicationID": "198765432134566",
        |        "applicant1": {
        |          "nino": "AA000003D",
        |          "title": "Miss",
        |          "firstForename": "Emma",
        |          "secondForename": "",
        |          "surname": "Cowling"
        |        }
        |      },
        |      "renewal": {
        |        "awardStartDate": "2016-04-05",
        |        "awardEndDate": "2016-08-31",
        |        "renewalNoticeIssuedDate": "20301012",
        |        "renewalNoticeFirstSpecifiedDate": "20101012"
        |      }
        |    },
        |    {
        |      "household": {
        |        "barcodeReference": "200000000000013",
        |        "applicationID": "198765432134567",
        |        "applicant1": {
        |          "nino": "AA000003D",
        |          "title": "Miss",
        |          "firstForename": "Emma",
        |          "secondForename": "",
        |          "surname": "Cowling"
        |        }
        |      },
        |      "renewal": {
        |        "awardStartDate": "2016-08-31",
        |        "awardEndDate": "2016-12-31",
        |        "renewalNoticeIssuedDate": "20301012",
        |        "renewalNoticeFirstSpecifiedDate": "20101012"
        |      }
        |    },
        |    {
        |      "household": {
        |        "barcodeReference": "200000000000014",
        |        "applicationID": "198765432134568",
        |        "applicant1": {
        |          "nino": "AM242413B",
        |          "title": "Miss",
        |          "firstForename": "Hazel",
        |          "secondForename": "",
        |          "surname": "Young"
        |        },
        |        "applicant2": {
        |          "nino": "AP412713B",
        |          "title": "Miss",
        |          "firstForename": "Cathy",
        |          "secondForename": "",
        |          "surname": "Garcia-Vazquez"
        |        }
        |      },
        |      "renewal": {
        |        "awardStartDate": "2016-12-31",
        |        "awardEndDate": "2017-07-31",
        |        "renewalNoticeIssuedDate": "20301012",
        |        "renewalNoticeFirstSpecifiedDate": "20101012"
        |      }
        |    }
        |  ]
        |}""".stripMargin

    val claims200Success: JsValue = Json.parse(claimsJson)

    lazy val http200ClaimsResponse: Future[AnyRef with HttpResponse] =
      Future.successful(HttpResponse(200, claimsJson))
    lazy val http500Response: Future[Nothing]                  = Future.failed(uk.gov.hmrc.http.UpstreamErrorResponse("Error", 500, 500))
    lazy val http400Response: Future[Nothing]                  = Future.failed(new BadRequestException("bad request"))
    lazy val http404Response: Future[AnyRef with HttpResponse] = Future.successful(HttpResponse(404, "{ NOT_FOUND }"))
    lazy val http429Response: Future[Nothing]                  = Future.failed(new TooManyRequestException("too many requests"))

    lazy val http200AuthenticateResponse: Future[AnyRef with HttpResponse] =
      Future.successful(uk.gov.hmrc.http.HttpResponse(200, toJson(tcrAuthToken).toString))

    lazy val http200ClaimantDetailsResponse: Future[AnyRef with HttpResponse] =
      Future.successful(HttpResponse(200, toJson(claimantDetails).toString))
    lazy val response: Future[HttpResponse] = http400Response

    val serviceUrl = "https://localhost"

    val http: CoreGet with CorePost = new CoreGet with HttpGet with CorePost with HttpPost with GetHttpTransport {
      override val hooks: Seq[HttpHook] = NoneRequired

      override def configuration: Config = Configuration.load(Environment.simple()).underlying

      override def doGet(
        url:         String,
        headers:     Seq[(String, String)] = Seq.empty
      )(implicit ec: ExecutionContext
      ): Future[HttpResponse] = response

      override def doPost[A](
        url:          String,
        body:         A,
        headers:      Seq[(String, String)]
      )(implicit wts: Writes[A],
        ec:           ExecutionContext
      ): Future[HttpResponse] =
        response

      override def doPostString(
        url:         String,
        body:        String,
        headers:     Seq[(String, String)]
      )(implicit ec: ExecutionContext
      ): Future[HttpResponse] = ???

      override def doFormPost(
        url:         String,
        body:        Map[String, Seq[String]],
        headers:     Seq[(String, String)] = Seq.empty
      )(implicit ec: ExecutionContext
      ): Future[HttpResponse] = ???

      override def doEmptyPost[A](
        url:         String,
        headers:     Seq[(String, String)] = Seq.empty
      )(implicit ec: ExecutionContext
      ): Future[HttpResponse] = ???

      override protected def actorSystem: ActorSystem = ActorSystem()
    }

    class TestNtcConnector(
      http:                 CoreGet with CorePost,
      runModeConfiguration: Configuration,
      environment:          Environment)
        extends NtcConnector(http, serviceUrl, runModeConfiguration, environment) {

      override protected def circuitBreakerConfig: CircuitBreakerConfig =
        CircuitBreakerConfig(externalServiceName, 5, 2000, 2000)
    }

    val connector = new TestNtcConnector(http, mock[Configuration], mock[Environment])
  }

  "authenticate" should {

    "return None when 404 returned" in new Setup {
      override lazy val response: Future[AnyRef with HttpResponse] = http404Response
      val result:                 Option[TcrAuthenticationToken]   = await(connector.authenticateRenewal(taxCreditNino, renewalReference))
      result shouldBe None
    }

    "return None when 400 returned" in new Setup {
      override lazy val response: Future[Nothing]                = http400Response
      val result:                 Option[TcrAuthenticationToken] = await(connector.authenticateRenewal(taxCreditNino, renewalReference))
      result shouldBe None
    }

    "return None when 429 returned" in new Setup {
      override lazy val response: Future[Nothing] = http429Response
      intercept[TooManyRequestException] {
        await(connector.authenticateRenewal(taxCreditNino, renewalReference))
      }
    }

    "throw Upstream5xxResponse when a 500 response is returned" in new Setup {
      override lazy val response: Future[Nothing] = http500Response
      intercept[uk.gov.hmrc.http.UpstreamErrorResponse] {
        await(connector.authenticateRenewal(taxCreditNino, renewalReference))
      }
    }

    "return a valid response when a 200 response is received with a valid json payload" in new Setup {
      override lazy val response: Future[AnyRef with HttpResponse] = http200AuthenticateResponse
      val result:                 Option[TcrAuthenticationToken]   = await(connector.authenticateRenewal(taxCreditNino, renewalReference))

      result.get shouldBe tcrAuthToken
    }

    "circuit breaker configuration should be applied and unhealthy service exception will kick in after 5th failed call" in new Setup {
      override lazy val response: Future[Nothing] = http500Response
      executeCB(connector.authenticateRenewal(taxCreditNino, renewalReference))
    }
  }

  "claims" should {

    "throw BadRequestException when a 400 response is returned" in new Setup {
      override lazy val response: Future[Nothing] = http400Response
      intercept[BadRequestException] {
        await(connector.claimantClaims(taxCreditNino))
      }
    }

    "throw Upstream5xxResponse when a 500 response is returned" in new Setup {
      override lazy val response: Future[Nothing] = http500Response
      intercept[uk.gov.hmrc.http.UpstreamErrorResponse] {
        await(connector.claimantClaims(taxCreditNino))
      }
    }

    "return a valid response when a 200 response is received with a valid json payload" in new Setup {
      override lazy val response: Future[AnyRef with HttpResponse] = http200ClaimsResponse
      val result:                 Claims                           = await(connector.claimantClaims(taxCreditNino))

      result shouldBe toJson(claims200Success).as[Claims]
    }

    "circuit breaker configuration should be applied and unhealthy service exception will kick in after 5th failed call" in new Setup {
      override lazy val response: Future[Nothing] = http500Response
      executeCB(connector.claimantClaims(taxCreditNino))
    }
  }

  "claimantDetails" should {

    "throw BadRequestException when a 400 response is returned" in new Setup {
      override lazy val response: Future[Nothing] = http400Response
      intercept[BadRequestException] {
        await(connector.claimantDetails(taxCreditNino))
      }
    }

    "throw Upstream5xxResponse when a 500 response is returned" in new Setup {
      override lazy val response: Future[Nothing] = http500Response
      intercept[uk.gov.hmrc.http.UpstreamErrorResponse] {
        await(connector.claimantDetails(taxCreditNino))
      }
    }

    "return a valid response when a 200 response is received with a valid json payload" in new Setup {
      override lazy val response: Future[AnyRef with HttpResponse] = http200ClaimantDetailsResponse
      val result:                 ClaimantDetails                  = await(connector.claimantDetails(taxCreditNino))

      result shouldBe claimantDetails
    }

    "circuit breaker configuration should be applied and unhealthy service exception will kick in after 5th failed call" in new Setup {
      override lazy val response: Future[Nothing] = http500Response
      executeCB(connector.claimantDetails(taxCreditNino))
    }
  }

}
