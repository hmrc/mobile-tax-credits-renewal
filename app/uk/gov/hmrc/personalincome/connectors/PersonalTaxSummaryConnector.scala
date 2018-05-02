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

import com.google.inject.name.Named
import com.google.inject.{Inject, Singleton}
import play.api.Mode.Mode
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{CoreGet, CorePost, HeaderCarrier}
import uk.gov.hmrc.personalincome.config.ServicesCircuitBreaker
import uk.gov.hmrc.personaltaxsummary.domain.PersonalTaxSummaryContainer
import uk.gov.hmrc.personaltaxsummary.viewmodels.{PTSEstimatedIncomeViewModel, PTSYourTaxableIncomeViewModel}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PersonalTaxSummaryConnector @Inject()(http: CoreGet with CorePost,
                                            @Named("personal-tax-summary") serviceUrl: String,
                                            val runModeConfiguration: Configuration, environment: Environment) extends ServicesCircuitBreaker {
  override protected def mode: Mode = environment.mode

  val externalServiceName = "personal-tax-summary"

  def url(path: String) = s"$serviceUrl$path"

  def buildJourneyQueryParam(journeyId: Option[String]): String = journeyId.fold("")(id => s"?journeyId=$id")

  def buildEstimatedIncome(nino: Nino, container: PersonalTaxSummaryContainer, journeyId: Option[String] = None)
                          (implicit headerCarrier: HeaderCarrier, ex: ExecutionContext): Future[PTSEstimatedIncomeViewModel] = {
    Logger.debug(s"PersonalTaxSummary - POST to /personal-tax/$nino/buildestimatedincome ")
    withCircuitBreaker(
      http.POST[PersonalTaxSummaryContainer, PTSEstimatedIncomeViewModel](
        url = url(s"/personal-tax/$nino/buildestimatedincome${buildJourneyQueryParam(journeyId)}"), body = container)
    )
  }

  def buildYourTaxableIncome(nino: Nino, container: PersonalTaxSummaryContainer, journeyId: Option[String] = None)
                            (implicit headerCarrier: HeaderCarrier, ex: ExecutionContext): Future[PTSYourTaxableIncomeViewModel] = {
    Logger.debug(s"PersonalTaxSummary - POST to /personal-tax/$nino/buildyourtaxableincome ")
    withCircuitBreaker(
      http.POST[PersonalTaxSummaryContainer, PTSYourTaxableIncomeViewModel](
        url = url(s"/personal-tax/$nino/buildyourtaxableincome${buildJourneyQueryParam(journeyId)}"), body = container)
    )
  }
}
