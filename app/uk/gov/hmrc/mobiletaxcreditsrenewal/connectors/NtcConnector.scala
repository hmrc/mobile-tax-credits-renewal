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

import com.google.inject.{Inject, Singleton}
import javax.inject.Named
import play.Logger
import play.api.Mode.Mode
import play.api.{Configuration, Environment}
import uk.gov.hmrc.http._
import uk.gov.hmrc.mobiletaxcreditsrenewal.config.ServicesCircuitBreaker
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NtcConnector @Inject()(http: CoreGet with CorePost,
                             @Named("ntc") serviceUrl: String,
                             val runModeConfiguration: Configuration, environment: Environment) extends ServicesCircuitBreaker {
  override protected def mode: Mode = environment.mode

  val externalServiceName = "ntc"

  def authenticateRenewal(nino: TaxCreditsNino,
                          renewalReference: RenewalReference)(implicit headerCarrier: HeaderCarrier, ex: ExecutionContext): Future[Option[TcrAuthenticationToken]] = {

    def logResult(status: Int, message: String): Unit = {
      Logger.info(s"Response from tcs auth service $status and message $message.")
    }

    withCircuitBreaker(
      http.GET[Option[TcrAuthenticationToken]](s"$serviceUrl/tcs/${nino.value}/${renewalReference.value}/auth").recover {
        case _: NotFoundException =>
          logResult(404, "Not found")
          None

        case _: BadRequestException =>
          logResult(400, "BadRequest")
          None
      })
  }

  def claimantDetails(nino: TaxCreditsNino)(implicit headerCarrier: HeaderCarrier, ex: ExecutionContext): Future[ClaimantDetails] =
    withCircuitBreaker(http.GET[ClaimantDetails](s"$serviceUrl/tcs/${nino.value}/claimant-details"))

  def claimantClaims(nino: TaxCreditsNino)(implicit headerCarrier: HeaderCarrier, ex: ExecutionContext): Future[Claims] =
    withCircuitBreaker(http.GET[Claims](s"$serviceUrl/tcs/${nino.value}/claimant-claims"))

  def legacyClaimantClaims(nino: TaxCreditsNino)(implicit headerCarrier: HeaderCarrier, ex: ExecutionContext): Future[LegacyClaims] =
    withCircuitBreaker(http.GET[LegacyClaims](s"$serviceUrl/tcs/${nino.value}/claimant-claims"))

  def submitRenewal(nino: TaxCreditsNino, renewalData: TcrRenewal)(implicit headerCarrier: HeaderCarrier, ex: ExecutionContext): Future[Int] = {
    val uri = s"$serviceUrl/tcs/${nino.taxCreditsNino}/renewal"
    withCircuitBreaker(http.POST[TcrRenewal, HttpResponse](uri, renewalData, Seq()).map(response => {
      response.status match {
        case x if x >= 200 && x < 300 => x
        case _ => throw new RuntimeException("Unsupported response code: " + response.status)
      }
    }))
  }

}