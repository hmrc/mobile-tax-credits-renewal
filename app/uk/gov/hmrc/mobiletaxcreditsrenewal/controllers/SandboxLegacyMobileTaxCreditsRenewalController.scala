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
import org.apache.commons.codec.binary.Base64.encodeBase64
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.api.sandbox.FileResource
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class SandboxLegacyMobileTaxCreditsRenewalController @Inject()()
  extends LegacyMobileTaxCreditsRenewalController with FileResource {
  override def getRenewalAuthentication(nino: Nino, renewalReference: RenewalReference, journeyId: Option[String]): Action[AnyContent] =
    validateAccept(acceptHeaderValidationRules).async {
      implicit request =>
        request.headers.get("SANDBOX-CONTROL") match {
          case Some("ERROR-401") => Future.successful(Unauthorized)
          case Some("ERROR-403") => Future.successful(Forbidden)
          case Some("ERROR-404") => Future.successful(NotFound)
          case Some("ERROR-500") => Future.successful(InternalServerError)
          case _ => Future.successful(Some(TcrAuthenticationToken(basicAuthString(encodedAuth(nino, renewalReference))))).map {
            case Some(authToken) => Ok(toJson(authToken))
            case _ => NotFound
          }
        }
    }

  private def basicAuthString(encodedAuth: String): String = "Basic " + encodedAuth

  private def encodedAuth(nino: Nino, tcrRenewalReference: RenewalReference): String =
    new String(encodeBase64(s"${nino.value}:${tcrRenewalReference.value}".getBytes))

  override def claimantDetails(nino: Nino, journeyId: Option[String], claims: Option[String]): Action[AnyContent] =
    validateAccept(acceptHeaderValidationRules).async {
      implicit request =>
        Future.successful(request.headers.get("SANDBOX-CONTROL") match {
          case Some("ERROR-401") => Unauthorized
          case Some("ERROR-403") => Forbidden
          case Some("ERROR-404") => NotFound
          case Some("ERROR-500") => InternalServerError
          case _ =>
            val tcrAuthToken = getTcrAuthHeader(request.headers.get(HeaderKeys.tcrAuthToken))
            val resource: String = findResource(s"/resources/claimantdetails/${nino.value}-${tcrAuthToken.extractRenewalReference.get}.json")
              .getOrElse(throw new IllegalArgumentException("Resource not found!"))
            Ok(Json.toJson(Json.parse(resource).as[ClaimantDetails]))
        })
    }

  private def getTcrAuthHeader(tcrAuthToken: Option[String]): TcrAuthenticationToken = {
    tcrAuthToken match {
      case Some(t@TcrAuthCheck(_)) => TcrAuthenticationToken(t)
      case _ => throw new IllegalArgumentException("Failed to locate tcrAuthToken")
    }
  }

  override def fullClaimantDetails(nino: Nino, journeyId: Option[String]): Action[AnyContent] =
    validateAccept(acceptHeaderValidationRules).async {
      implicit request =>
        Future.successful(request.headers.get("SANDBOX-CONTROL") match {
          case Some("ERROR-401") => Unauthorized
          case Some("ERROR-403") => Forbidden
          case Some("ERROR-404") => NotFound
          case Some("ERROR-500") => InternalServerError
          case _ => val resource: String = findResource(s"/resources/claimantdetails/claimant-details.json")
            .getOrElse(throw new IllegalArgumentException("Resource not found!"))
            Ok(toJson(resource))
        })
    }

  override def submitRenewal(nino: Nino, journeyId: Option[String]): Action[JsValue] =
    validateAccept(acceptHeaderValidationRules).async(BodyParsers.parse.json) {
      implicit request =>
        Future.successful(request.headers.get("SANDBOX-CONTROL") match {
          case Some("ERROR-401") => Unauthorized
          case Some("ERROR-403") => Forbidden
          case Some("ERROR-404") => NotFound
          case Some("ERROR-500") => InternalServerError
          case _ => Ok
        })
    }

  override def taxCreditsSubmissionStateEnabled(journeyId: Option[String]): Action[AnyContent] =
    validateAccept(acceptHeaderValidationRules).async {
      implicit request =>
        Future.successful(request.headers.get("SANDBOX-CONTROL") match {
          case Some("ERROR-401") => Unauthorized
          case Some("ERROR-403") => Forbidden
          case Some("ERROR-404") => NotFound
          case Some("ERROR-500") => InternalServerError
          case Some("CLOSED") => Ok(Json.toJson(TaxCreditsRenewalsState("closed")))
          case Some("CHECK-STATUS-ONLY") => Ok(Json.toJson(TaxCreditsRenewalsState("check_status_only")))
          case _ => Ok(Json.toJson(TaxCreditsRenewalsState("open")))
        })
    }
}
