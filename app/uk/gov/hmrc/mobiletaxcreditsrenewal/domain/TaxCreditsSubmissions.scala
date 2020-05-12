/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.{LocalDateTime, ZoneId}

import com.google.inject.Singleton
import javax.inject.{Inject, Named}
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.time.DateTimeUtils

import scala.collection.Seq

case class TaxCreditsSubmissions(
  inSubmitRenewalsPeriod: Boolean,
  inViewRenewalsPeriod:   Boolean) {

  def toTaxCreditsRenewalsState: TaxCreditsRenewalsState =
    new TaxCreditsRenewalsState(
      if (inSubmitRenewalsPeriod) "open"
      else if (inViewRenewalsPeriod) "check_status_only"
      else "closed"
    )
}

case class TaxCreditsRenewalsState(submissionsState: String) {
  private val statesThatRequireSummaryData = Seq("open", "check_status_only")

  val showSummaryData: Boolean = statesThatRequireSummaryData.contains(submissionsState)
}

object TaxCreditsSubmissions extends DateTimeUtils {
  implicit val formats: OFormat[TaxCreditsSubmissions] = Json.format[TaxCreditsSubmissions]
}

object TaxCreditsRenewalsState {
  implicit val formats: OFormat[TaxCreditsRenewalsState] = Json.format[TaxCreditsRenewalsState]
}

trait LoadConfig {

  import com.typesafe.config.Config

  def config: Config
}

trait TaxCreditsControl {
  def toTaxCreditsSubmissions: TaxCreditsSubmissions

  def toTaxCreditsRenewalsState: TaxCreditsRenewalsState
}

@Singleton
class TaxCreditsSubmissionControlConfig @Inject() (
  @Named("submission.startDate") submissionStartDate:                     String,
  @Named("submission.endDate") submissionEndDate:                         String,
  @Named("submission.endViewRenewalsDate") submissionEnvViewRenewalsDate: String)
    extends TaxCreditsControl
    with DateTimeUtils {

  val startDate:           LocalDateTime = LocalDateTime.parse(submissionStartDate)
  val endDate:             LocalDateTime = LocalDateTime.parse(submissionEndDate)
  val endViewRenewalsDate: LocalDateTime = LocalDateTime.parse(submissionEnvViewRenewalsDate)

  def toTaxCreditsSubmissions: TaxCreditsSubmissions = {
    val currentTime          = LocalDateTime.now(ZoneId.of("Europe/London"))
    val allowSubmissions     = currentTime.isAfter(startDate) && currentTime.isBefore(endDate)
    val allowViewSubmissions = currentTime.isAfter(startDate) && currentTime.isBefore(endViewRenewalsDate)
    new TaxCreditsSubmissions(allowSubmissions, allowViewSubmissions)
  }

  def toTaxCreditsRenewalsState: TaxCreditsRenewalsState =
    toTaxCreditsSubmissions.toTaxCreditsRenewalsState
}
