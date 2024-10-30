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
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._
import uk.gov.hmrc.mobiletaxcreditsrenewal.utils.BaseSpec

import java.net.URL
import scala.concurrent.{ExecutionContext, Future}

class NtcConnectorSpec extends BaseSpec {

  import scala.concurrent.ExecutionContext.Implicits.global

  private trait Setup {

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

    val claimsSuccess: Claims = Json.parse(claimsJson).as[Claims]

    val connector =
      new NtcConnector(mockHttpClient, serviceUrl, Configuration.load(Environment.simple()), mock[Environment])

    def authenticateRenewalGet[T](nino: Nino): CallHandler[Future[T]] = {
      (mockHttpClient
        .get(_: URL)(_: HeaderCarrier))
        .expects(url"${s"$serviceUrl/tcs/${nino.value}/${renewalReference.value}/auth"}", *)
        .returns(mockRequestBuilder)
        .anyNumberOfTimes()

      (mockRequestBuilder
        .execute[T](_: HttpReads[T], _: ExecutionContext))
        .expects(*, *)
        .anyNumberOfTimes()

    }

    def claimantClaimsGet[T](nino: Nino): CallHandler[Future[T]] = {
      (mockHttpClient
        .get(_: URL)(_: HeaderCarrier))
        .expects(url"${s"$serviceUrl/tcs/${nino.value}/claimant-claims"}", *)
        .returns(mockRequestBuilder)
        .anyNumberOfTimes()

      (mockRequestBuilder
        .execute[T](_: HttpReads[T], _: ExecutionContext))
        .expects(*, *)
        .anyNumberOfTimes()

    }

    def claimantDetailsGet[T](nino: Nino): CallHandler[Future[T]] = {
      (mockHttpClient
        .get(_: URL)(_: HeaderCarrier))
        .expects(url"${s"$serviceUrl/tcs/${nino.value}/claimant-details"}", *)
        .returns(mockRequestBuilder)
        .anyNumberOfTimes()

      (mockRequestBuilder
        .execute[T](_: HttpReads[T], _: ExecutionContext))
        .expects(*, *)
        .anyNumberOfTimes()

    }
  }

  "authenticate" should {

    "return None when 404 returned" in new Setup {
      authenticateRenewalGet(nino).returns(http404Response)
      val result: Option[TcrAuthenticationToken] = await(connector.authenticateRenewal(taxCreditNino, renewalReference))
      result shouldBe None
    }

    "return None when 400 returned" in new Setup {
      authenticateRenewalGet(nino).returns(http400Response)
      val result: Option[TcrAuthenticationToken] = await(connector.authenticateRenewal(taxCreditNino, renewalReference))
      result shouldBe None
    }

    "return None when 429 returned" in new Setup {
      authenticateRenewalGet(nino).returns(http429Response)
      intercept[TooManyRequestException] {
        await(connector.authenticateRenewal(taxCreditNino, renewalReference))
      }
    }

    "throw Upstream5xxResponse when a 500 response is returned" in new Setup {
      authenticateRenewalGet(nino).returns(http500Response)
      intercept[uk.gov.hmrc.http.UpstreamErrorResponse] {
        await(connector.authenticateRenewal(taxCreditNino, renewalReference))
      }
    }

    "return a valid response when a 200 response is received with a valid json payload" in new Setup {
      authenticateRenewalGet(nino).returns(Future successful Some(tcrAuthToken))
      val result: Option[TcrAuthenticationToken] = await(connector.authenticateRenewal(taxCreditNino, renewalReference))

      result.get shouldBe tcrAuthToken
    }

    "circuit breaker configuration should be applied and unhealthy service exception will kick in after 5th failed call" in new Setup {
      authenticateRenewalGet(nino).returns(http500Response)
      executeCB(connector.authenticateRenewal(taxCreditNino, renewalReference))
    }
  }

  "claims" should {

    "throw BadRequestException when a 400 response is returned" in new Setup {
      claimantClaimsGet(nino).returns(http400Response)
      intercept[BadRequestException] {
        await(connector.claimantClaims(taxCreditNino))
      }
    }

    "throw Upstream5xxResponse when a 500 response is returned" in new Setup {
      claimantClaimsGet(nino).returns(http500Response)
      intercept[uk.gov.hmrc.http.UpstreamErrorResponse] {
        await(connector.claimantClaims(taxCreditNino))
      }
    }

    "return a valid response when a 200 response is received with a valid json payload" in new Setup {
      claimantClaimsGet(nino).returns(Future successful claimsSuccess)
      val result: Claims = await(connector.claimantClaims(taxCreditNino))

      result shouldBe claimsSuccess
    }

    "circuit breaker configuration should be applied and unhealthy service exception will kick in after 5th failed call" in new Setup {
      claimantClaimsGet(nino).returns(http500Response)
      executeCB(connector.claimantClaims(taxCreditNino))
    }
  }

  "claimantDetails" should {

    "throw BadRequestException when a 400 response is returned" in new Setup {
      claimantDetailsGet(nino).returns(http400Response)
      intercept[BadRequestException] {
        await(connector.claimantDetails(taxCreditNino))
      }
    }

    "throw Upstream5xxResponse when a 500 response is returned" in new Setup {
      claimantDetailsGet(nino).returns(http500Response)
      intercept[uk.gov.hmrc.http.UpstreamErrorResponse] {
        await(connector.claimantDetails(taxCreditNino))
      }
    }

    "return a valid response when a 200 response is received with a valid json payload" in new Setup {
      claimantDetailsGet(nino).returns(Future successful claimantDetails)
      val result: ClaimantDetails = await(connector.claimantDetails(taxCreditNino))

      result shouldBe claimantDetails
    }

    "circuit breaker configuration should be applied and unhealthy service exception will kick in after 5th failed call" in new Setup {
      claimantDetailsGet(nino).returns(http500Response)
      executeCB(connector.claimantDetails(taxCreditNino))
    }
  }

}
