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

import javax.inject.{Inject, Singleton}
import play.api.LoggerLike
import play.api.libs.json.{JsError, JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.TcrRenewal

import scala.concurrent.Future

@Singleton
class SandboxMobileTaxCreditsRenewalController @Inject()(val logger: LoggerLike) extends MobileTaxCreditsRenewalController {
  override def renewals(nino: Nino, journeyId: Option[String] = None): Action[AnyContent] = validateAccept(acceptHeaderValidationRules).async {
    Future successful Ok
  }

  override def submitRenewal(nino: Nino, journeyId: Option[String] = None): Action[JsValue] =
    validateAccept(acceptHeaderValidationRules).async(BodyParsers.parse.json) {
      implicit request =>

      request.body.validate[TcrRenewal].fold(
        errors => {
          logger.warn("Received error with service submitRenewal: " + errors)
          Future.successful(BadRequest(Json.obj("message" -> JsError.toJson(errors))))
        },
        renewal => {
          Future successful Ok
        }
      )
    }
}
