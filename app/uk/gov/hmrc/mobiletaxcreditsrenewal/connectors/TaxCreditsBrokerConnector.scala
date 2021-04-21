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

package uk.gov.hmrc.mobiletaxcreditsrenewal.connectors

import com.google.inject.{Inject, Singleton}
import javax.inject.Named
import play.api.Logger
import uk.gov.hmrc.http._
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaxCreditsBrokerConnector @Inject() (
  http:                                    CoreGet,
  @Named("tax-credits-broker") serviceUrl: String) {

  val externalServiceName = "tax-credits-broker"

  def employedEarningsRti(
    nino:                   TaxCreditsNino
  )(implicit headerCarrier: HeaderCarrier,
    ex:                     ExecutionContext
  ): Future[Option[EmployedEarningsRti]] =
    http.GET[Option[EmployedEarningsRti]](s"$serviceUrl/tcs/${nino.value}/employed-earnings-rti") recover {
      case e: NotFoundException =>
        Logger.warn(s"No employed earnings RTI found for user: ${e.getMessage}")
        None
      case e: Exception =>
        Logger.error(s"Failed to get employed earnings RTI: ${e.getMessage}")
        None
    }

}
