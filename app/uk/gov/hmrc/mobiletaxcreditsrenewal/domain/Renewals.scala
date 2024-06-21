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

package uk.gov.hmrc.mobiletaxcreditsrenewal.domain

import java.util.Base64._
import play.api.libs.json.{Json, OFormat}

case class RenewalReference(value: String)

case class TcrAuthenticationToken(tcrAuthToken: String)

object TcrAuthenticationToken {
  implicit val formats: OFormat[TcrAuthenticationToken] = Json.format[TcrAuthenticationToken]

  def basicAuthString(
    nino:             String,
    renewalReference: String
  ): String = "Basic " + encodedAuth(nino, renewalReference)

  def encodedAuth(
    nino:             String,
    renewalReference: String
  ): String = new String(getEncoder.encode(s"$nino:$renewalReference".getBytes))

}

case class ClaimantDetails(
  hasPartner:                Boolean,
  claimantNumber:            Int,
  renewalFormType:           String,
  mainApplicantNino:         String,
  usersPartnerNino:          Option[String],
  availableForCOCAutomation: Boolean,
  applicationId:             String)

object ClaimantDetails {
  implicit val formats: OFormat[ClaimantDetails] = Json.format[ClaimantDetails]
}

