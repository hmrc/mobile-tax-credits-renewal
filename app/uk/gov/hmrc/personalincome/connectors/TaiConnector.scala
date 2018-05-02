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

package uk.gov.hmrc.personalincome.connectors

import com.google.inject.{Inject, Singleton}
import javax.inject.Named
import play.api.Mode.Mode
import play.api.http.Status._
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import uk.gov.hmrc.personalincome.config.ServicesCircuitBreaker
import uk.gov.hmrc.personalincome.domain.TaxSummaryDetails

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaiConnector @Inject()(http: CoreGet with CorePost,
                             @Named("tai") serviceUrl: String,
                             val runModeConfiguration: Configuration, environment: Environment) extends ServicesCircuitBreaker {
  override protected def mode: Mode = environment.mode

  val externalServiceName = "tai"

  def url(path: String) = s"$serviceUrl$path"

  def taxSummary(nino: Nino, year: Int)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[Option[TaxSummaryDetails]] = {
    withCircuitBreaker(
      http.GET[HttpResponse](url = url(s"/tai/$nino/tax-summary-full/$year")) map { response =>
        response.status match {
          case OK => Some(response.json.as[TaxSummaryDetails](TaxSummaryDetails.formats))
          case nonOkResponse =>
            Logger.warn(s"taxSummary request responded with $nonOkResponse")
            None
        }
      } recover {
        case _: NotFoundException => None
        case _: BadRequestException => None
      }
    )
  }
}