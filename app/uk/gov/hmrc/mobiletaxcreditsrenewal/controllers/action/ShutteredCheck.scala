/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.mobiletaxcreditsrenewal.controllers.action

import play.api.libs.json.Json
import play.api.mvc.{Result, Results}
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.Shuttering

import scala.concurrent.Future

trait ShutteredCheck extends Results {

  private final val WebServerIsDown = new Status(521)

  def withShuttering(shuttering: Shuttering)(fn: => Future[Result]): Future[Result] =
    if (shuttering.shuttered) Future.successful(WebServerIsDown(Json.toJson(shuttering))) else fn

}
