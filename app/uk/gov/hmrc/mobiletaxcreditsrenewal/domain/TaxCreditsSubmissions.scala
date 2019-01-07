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

package uk.gov.hmrc.mobiletaxcreditsrenewal.domain

import com.google.inject.Singleton
import javax.inject.{Inject, Named}
import org.joda.time.DateTime
import org.joda.time.DateTime.parse
import org.joda.time.DateTimeZone.UTC
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.time.DateTimeUtils

import scala.collection.Seq

case class TaxCreditsSubmissions(inSubmitRenewalsPeriod: Boolean, inViewRenewalsPeriod: Boolean) {
  def toTaxCreditsRenewalsState: TaxCreditsRenewalsState = {
    new TaxCreditsRenewalsState(
      if (inSubmitRenewalsPeriod) "open"
      else if (inViewRenewalsPeriod) "check_status_only"
      else "closed"
    )
  }
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
  def toTaxCreditsSubmissions(): TaxCreditsSubmissions

  def toTaxCreditsRenewalsState(): TaxCreditsRenewalsState
}

@Singleton
class TaxCreditsSubmissionControlConfig @Inject()(@Named("submission.startDate") submissionStartDate: String,
                                                  @Named("submission.endDate") submissionEndDate: String,
                                                  @Named("submission.endViewRenewalsDate") submissionEnvViewRenewalsDate: String)
  extends TaxCreditsControl with DateTimeUtils {

  val submissionControl: TaxCreditsSubmissionControl =
    TaxCreditsSubmissionControl(
      parse(submissionStartDate).toDateTime(UTC),
      parse(submissionEndDate).toDateTime(UTC),
      parse(submissionEnvViewRenewalsDate).toDateTime(UTC)
    )

  def toTaxCreditsSubmissions: TaxCreditsSubmissions = {
    val currentTime = now.getMillis
    val allowSubmissions = currentTime >= submissionControl.startMs && currentTime <= submissionControl.endMs
    val allowViewSubmissions = currentTime >= submissionControl.startMs && currentTime <= submissionControl.endViewMs
    new TaxCreditsSubmissions(allowSubmissions, allowViewSubmissions)
  }

  def toTaxCreditsRenewalsState: TaxCreditsRenewalsState = {
    toTaxCreditsSubmissions.toTaxCreditsRenewalsState
  }
}


sealed case class TaxCreditsSubmissionControl(startDate: DateTime, endDate: DateTime, endViewRenewalsDate: DateTime) {
  val startMs: Long = startDate.getMillis
  val endMs: Long = endDate.getMillis
  val endViewMs: Long = endViewRenewalsDate.getMillis
}
