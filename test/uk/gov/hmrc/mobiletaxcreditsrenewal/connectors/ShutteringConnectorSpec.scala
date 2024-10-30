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

import eu.timepit.refined.auto._
import org.scalamock.handlers.CallHandler
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.http._
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.Shuttering
import uk.gov.hmrc.mobiletaxcreditsrenewal.utils.BaseSpec

import java.net.URL
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class ShutteringConnectorSpec extends BaseSpec with FutureAwaits with DefaultAwaitTimeout {
  val connector: ShutteringConnector = new ShutteringConnector(mockHttpClient, serviceUrl)

  def mockShutteringGet[T]: CallHandler[Future[T]] = {
    (mockHttpClient
      .get(_: URL)(_: HeaderCarrier))
      .expects(
        url"${s"$serviceUrl/mobile-shuttering/service/mobile-tax-credits-renewal/shuttered-status?journeyId=87144372-6bda-4cc9-87db-1d52fd96498f"}",
        *
      )
      .returns(mockRequestBuilder)

    (mockRequestBuilder
      .execute[T](_: HttpReads[T], _: ExecutionContext))
      .expects(*, *)

  }

  "getShutteredStatus" should {
    "Assume unshuttered for InternalServerException response" in {
      mockShutteringGet.returns(Future failed new InternalServerException(""))

      val result: Shuttering = await(connector.getShutteringStatus("87144372-6bda-4cc9-87db-1d52fd96498f"))
      result shouldBe Shuttering.shutteringDisabled
    }

    "Assume unshuttered for BadGatewayException response" in {
      mockShutteringGet.returns(Future failed new BadGatewayException(""))

      val result: Shuttering = await(connector.getShutteringStatus("87144372-6bda-4cc9-87db-1d52fd96498f"))
      result shouldBe Shuttering.shutteringDisabled
    }
  }
}
