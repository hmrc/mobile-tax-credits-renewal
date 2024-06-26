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

package uk.gov.hmrc.mobiletaxcreditsrenewal.config

import play.api.Configuration
import uk.gov.hmrc.circuitbreaker.{CircuitBreakerConfig, UsingCircuitBreaker}
import uk.gov.hmrc.http.{BadRequestException, NotFoundException, UpstreamErrorResponse}

class ServicesCircuitBreaker(
  externalServiceName: String,
  configuration:       Configuration)
    extends UsingCircuitBreaker {

  protected lazy val rootServices = "microservice.services"

  protected def config(serviceName: String): Configuration =
    configuration
      .getOptional[Configuration](s"$rootServices.$serviceName")
      .getOrElse(throw new IllegalArgumentException(s"Configuration for service $serviceName not found"))

  protected def circuitBreakerConfig = CircuitBreakerConfig(
    serviceName = externalServiceName,
    numberOfCallsToTriggerStateChange =
      config(externalServiceName).getOptional[Int]("circuitBreaker.numberOfCallsToTriggerStateChange"),
    unavailablePeriodDuration = config(externalServiceName)
        .getOptional[Int]("circuitBreaker.unavailablePeriodDurationInSeconds") map (_ * 1000),
    unstablePeriodDuration = config(externalServiceName)
        .getOptional[Int]("circuitBreaker.unstablePeriodDurationInSeconds") map (_ * 1000)
  )

  override protected def breakOnException(t: Throwable): Boolean = t match {
    case _: BadRequestException                                                 => false
    case _: NotFoundException                                                   => false
    case e: UpstreamErrorResponse if (e.statusCode > 399 && e.statusCode < 500) => false
    case e: UpstreamErrorResponse if (e.statusCode > 499)                       => true
    case _ => true
  }
}
