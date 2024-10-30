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

import com.google.inject.{Inject, Singleton}

import javax.inject.Named
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, NotFoundException, StringContextOps}
import uk.gov.hmrc.mobiletaxcreditsrenewal.config.ServicesCircuitBreaker
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.{ClaimantDetails, Claims, RenewalReference, TaxCreditsNino, TcrAuthenticationToken}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NtcConnector @Inject() (
  http:                     HttpClientV2,
  @Named("ntc") serviceUrl: String,
  val runModeConfiguration: Configuration,
  environment:              Environment)
    extends ServicesCircuitBreaker("ntc", runModeConfiguration) {

  val logger: Logger = Logger(this.getClass)

  val externalServiceName = "ntc"

  def authenticateRenewal(
    nino:                   TaxCreditsNino,
    renewalReference:       RenewalReference
  )(implicit headerCarrier: HeaderCarrier,
    ex:                     ExecutionContext
  ): Future[Option[TcrAuthenticationToken]] = {

    def logResult(
      status:  Int,
      message: String
    ): Unit =
      logger.info(s"Response from tcs auth service $status and message $message.")

    withCircuitBreaker(
      http
        .get(url"${s"$serviceUrl/tcs/${nino.value}/${renewalReference.value}/auth"}")
        .execute[Option[TcrAuthenticationToken]]
    ).recover {
      case _: NotFoundException =>
        logResult(404, "Not found")
        None

      case _: BadRequestException =>
        logResult(400, "BadRequest")
        None
    }

  }

  def claimantDetails(
    nino:                   TaxCreditsNino
  )(implicit headerCarrier: HeaderCarrier,
    ex:                     ExecutionContext
  ): Future[ClaimantDetails] =
    withCircuitBreaker(http.get(url"${s"$serviceUrl/tcs/${nino.value}/claimant-details"}").execute[ClaimantDetails])

  def claimantClaims(
    nino:                   TaxCreditsNino
  )(implicit headerCarrier: HeaderCarrier,
    ex:                     ExecutionContext
  ): Future[Claims] =
    withCircuitBreaker(http.get(url"${s"$serviceUrl/tcs/${nino.value}/claimant-claims"}").execute[Claims])

}
