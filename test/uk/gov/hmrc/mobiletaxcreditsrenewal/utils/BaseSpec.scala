/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.mobiletaxcreditsrenewal.utils

import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, NotFoundException, TooManyRequestException}
import uk.gov.hmrc.mobiletaxcreditsrenewal.connectors.CircuitBreakerTest
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.TaxCreditsNino

import scala.concurrent.Future

trait BaseSpec extends AnyWordSpecLike with Matchers with ScalaFutures with CircuitBreakerTest with MockFactory {

  val nino:               Nino           = Nino("KM569110B")
  val taxCreditNino:      TaxCreditsNino = TaxCreditsNino(nino.value)
  val serviceUrl:         String         = "https://localhost"
  val mockHttpClient:     HttpClientV2   = mock[HttpClientV2]
  val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]

  implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  lazy val http500Response: Future[Nothing] = Future.failed(uk.gov.hmrc.http.UpstreamErrorResponse("Error", 500, 500))
  lazy val http400Response: Future[Nothing] = Future.failed(new BadRequestException("bad request"))
  lazy val http404Response: Future[Nothing] = Future.failed(new NotFoundException("{ NOT_FOUND }"))
  lazy val http429Response: Future[Nothing] = Future.failed(new TooManyRequestException("too many requests"))

}
