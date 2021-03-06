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

package uk.gov.hmrc.mobiletaxcreditsrenewal.controllers.action

import play.api.Logger
import play.api.libs.json.Json.toJson
import play.api.mvc._
import uk.gov.hmrc.api.controllers._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, Upstream4xxResponse}
import uk.gov.hmrc.mobiletaxcreditsrenewal.controllers._
import uk.gov.hmrc.play.HeaderCarrierConverter.fromHeadersAndSession

import scala.concurrent.{ExecutionContext, Future}

case object ErrorUnauthorizedMicroService extends ErrorResponse(401, "UNAUTHORIZED", "Unauthorized")
case object ErrorForbidden extends ErrorResponse(403, "FORBIDDEN", "Forbidden")

trait AccountAccessControl extends Results with Authorisation {
  import scala.concurrent.ExecutionContext.Implicits.global

  lazy val requiresAuth: Boolean = true

  def invokeAuthBlock[A](
    request: Request[A],
    block:   (Request[A]) => Future[Result],
    taxId:   Option[Nino]
  ): Future[Result] = {
    implicit val hc: HeaderCarrier = fromHeadersAndSession(request.headers, None)

    grantAccess(taxId.getOrElse(Nino("")))
      .flatMap { _ ⇒
        block(request)
      }
      .recover {
        case _: Upstream4xxResponse =>
          Logger.info("Unauthorized! Failed to grant access since 4xx response!")
          Unauthorized(toJson(ErrorUnauthorizedMicroService))

        case _: NinoNotFoundOnAccount =>
          Logger.info("Unauthorized! NINO not found on account!")
          Forbidden(toJson(ErrorForbidden))

        case _: FailToMatchTaxIdOnAuth =>
          Logger.info("Unauthorized! Failure to match URL NINO against Auth NINO")
          Forbidden(toJson(ErrorForbidden))

        case _: AccountWithLowCL =>
          Logger.info("Unauthorized! Account with low CL!")
          Forbidden(toJson(ErrorForbidden))
      }
  }
}

trait AccessControl extends AccountAccessControl {
  outer =>

  def executionContext: ExecutionContext
  def parser:           BodyParser[AnyContent]

  def validateAcceptWithAuth(
    rules: Option[String] ⇒ Boolean,
    taxId: Option[Nino]
  ): ActionBuilder[Request, AnyContent] =
    new ActionBuilder[Request, AnyContent] {

      def invokeBlock[A](
        request: Request[A],
        block:   (Request[A]) => Future[Result]
      ): Future[Result] =
        if (rules(request.headers.get("Accept"))) {
          invokeAuthBlock(request, block, taxId)
        } else Future.successful(Status(ErrorAcceptHeaderInvalid.httpStatusCode)(toJson(ErrorAcceptHeaderInvalid)))
      override def parser:                     BodyParser[AnyContent] = outer.parser
      override protected def executionContext: ExecutionContext       = outer.executionContext
    }
}
