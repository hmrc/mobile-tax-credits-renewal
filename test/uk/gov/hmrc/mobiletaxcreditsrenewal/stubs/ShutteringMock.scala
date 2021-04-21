/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.mobiletaxcreditsrenewal.stubs

import org.scalamock.handlers.CallHandler
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobiletaxcreditsrenewal.connectors.ShutteringConnector
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.Shuttering
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.types.ModelTypes.JourneyId

import scala.concurrent.{ExecutionContext, Future}

trait ShutteringMock extends MockFactory {

  private val shutteredResponse =
    Shuttering(shuttered = true, Some("Shuttered"), Some("Tax Credits Renewal is currently not available"))
  private val notShutteredResponse = Shuttering.shutteringDisabled

  def mockShutteringResponse(
    shuttered:                    Boolean
  )(implicit shutteringConnector: ShutteringConnector
  ): CallHandler[Future[Shuttering]] = {
    val response = if (shuttered) shutteredResponse else notShutteredResponse
    (shutteringConnector
      .getShutteringStatus(_: JourneyId)(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *)
      .returning(Future successful response)
  }

}
