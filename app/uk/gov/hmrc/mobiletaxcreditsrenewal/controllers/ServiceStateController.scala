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

package uk.gov.hmrc.mobiletaxcreditsrenewal.controllers


import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.api.controllers.HeaderValidator
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.time.DateTimeUtils

import scala.concurrent.Future

trait ServiceStateController extends BaseController with HeaderValidator with ErrorHandling {

  import play.api.libs.json.Json

  import scala.concurrent.ExecutionContext.Implicits.global

  val taxCreditsSubmissionControlConfig: TaxCreditsControl

  final def taxCreditsSubmissionStateEnabled(journeyId: Option[String] = None) = validateAccept(acceptHeaderValidationRules).async {
    implicit request =>
      implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(request.headers, None)
      errorWrapper(
        Future {
          taxCreditsSubmissionControlConfig.toTaxCreditsRenewalsState
        }.map {
          submissionState => Ok(Json.toJson(submissionState))
        })
  }
}

@Singleton
class SandboxServiceStateController() extends ServiceStateController with DateTimeUtils {

  override val taxCreditsSubmissionControlConfig = new TaxCreditsControl {
    override def toTaxCreditsSubmissions = new TaxCreditsSubmissions(false, true, true)

    override def toTaxCreditsRenewalsState = new TaxCreditsRenewalsState("open")
  }
}

@Singleton
class LiveServiceStateController @Inject()(override val taxCreditsSubmissionControlConfig: TaxCreditsSubmissionControlConfig)
  extends ServiceStateController
