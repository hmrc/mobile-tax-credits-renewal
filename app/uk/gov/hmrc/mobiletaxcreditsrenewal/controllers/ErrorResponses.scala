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

package uk.gov.hmrc.mobiletaxcreditsrenewal.controllers

import uk.gov.hmrc.api.controllers.ErrorResponse
import uk.gov.hmrc.http.HttpException

case object ClientRetryRequest extends ErrorResponse(429, "NTC_RETRY", "Client must retry the request.")

class GrantAccessException(message: String) extends HttpException(message, 401)

class FailToMatchTaxIdOnAuth extends GrantAccessException("Unauthorised! Failure to match URL NINO against Auth NINO")

class NinoNotFoundOnAccount extends GrantAccessException("Unauthorised! NINO not found on account!")

class AccountWithLowCL extends GrantAccessException("Unauthorised! Account with low CL!")

case object ErrorTooManyRequests
    extends ErrorResponse(429,
                          "TOO_MANY_REQUESTS",
                          "Too many requests made to mobile-tax-credits please try again later")
