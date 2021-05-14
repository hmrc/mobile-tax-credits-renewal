/*
 * Copyright 2021 HM Revenue & Customs
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

import javax.inject.{Inject, Named, Singleton}
import play.api._
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsError, JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.api.controllers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException, ServiceUnavailableException, UpstreamErrorResponse}
import uk.gov.hmrc.mobiletaxcreditsrenewal.connectors.ShutteringConnector
import uk.gov.hmrc.mobiletaxcreditsrenewal.controllers.HeaderKeys.tcrAuthToken
import uk.gov.hmrc.mobiletaxcreditsrenewal.controllers.action.{AccessControl, ShutteredCheck}
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.types.ModelTypes.JourneyId
import uk.gov.hmrc.mobiletaxcreditsrenewal.services.MobileTaxCreditsRenewalService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController
import uk.gov.hmrc.play.http.HeaderCarrierConverter.fromRequest

import scala.concurrent.{ExecutionContext, Future}

trait MobileTaxCreditsRenewalController extends BackendBaseController with HeaderValidator {

  def renewals(
    nino:      Nino,
    journeyId: JourneyId
  ): Action[AnyContent]

  def submitRenewal(
    nino:      Nino,
    journeyId: JourneyId
  ): Action[JsValue]

}

@Singleton
class LiveMobileTaxCreditsRenewalController @Inject() (
  override val authConnector:                                   AuthConnector,
  val service:                                                  MobileTaxCreditsRenewalService,
  @Named("controllers.confidenceLevel") override val confLevel: Int,
  val controllerComponents:                                     ControllerComponents,
  shutteringConnector:                                          ShutteringConnector
)(implicit val executionContext:                                ExecutionContext)
    extends MobileTaxCreditsRenewalController
    with AccessControl
    with ShutteredCheck {
  override def parser: BodyParser[AnyContent] = controllerComponents.parsers.anyContent
  override val logger: Logger                 = Logger(this.getClass)

  def notFound: Result = Status(ErrorNotFound.httpStatusCode)(toJson(ErrorNotFound))

  def errorWrapper(func: => Future[mvc.Result]): Future[Result] =
    func.recover {
      case ex: UpstreamErrorResponse if ex.statusCode == NOT_FOUND => notFound

      case ex: UpstreamErrorResponse if ex.statusCode == SERVICE_UNAVAILABLE =>
        logger.error(s"ServiceUnavailableException reported: ${ex.getMessage}", ex)
        Status(ClientRetryRequest.httpStatusCode)(toJson(ClientRetryRequest))

      case e: Throwable =>
        logger.error(s"Internal server error: ${e.getMessage}", e)
        Status(ErrorInternalServerError.httpStatusCode)(toJson(ErrorInternalServerError))
    }

  override def renewals(
    nino:      Nino,
    journeyId: JourneyId
  ): Action[AnyContent] =
    validateAcceptWithAuth(acceptHeaderValidationRules, Option(nino)).async { implicit request =>
      implicit val hc: HeaderCarrier = fromRequest(request)
      shutteringConnector.getShutteringStatus(journeyId).flatMap { shuttered =>
        withShuttering(shuttered) {
          errorWrapper(
            service.renewals(nino, journeyId).map { renewals =>
              Ok(toJson(renewals))
            }
          )
        }
      }
    }

  override def submitRenewal(
    nino:      Nino,
    journeyId: JourneyId
  ): Action[JsValue] =
    validateAcceptWithAuth(acceptHeaderValidationRules, Option(nino)).async(controllerComponents.parsers.json) {
      implicit request =>
        implicit val hc: HeaderCarrier = fromRequest(request)
        shutteringConnector.getShutteringStatus(journeyId).flatMap { shuttered =>
          withShuttering(shuttered) {
            request.body
              .validate[TcrRenewal]
              .fold(
                errors => {
                  logger.warn("Received error with service submitRenewal: " + errors)
                  Future.successful(BadRequest(Json.obj("message" -> JsError.toJson(errors))))
                },
                renewal =>
                  errorWrapper(validateTcrAuthHeader(None) { implicit hc =>
                    service
                      .submitRenewal(nino, renewal)
                      .map { _ =>
                        logger.info(s"Tax credit renewal submission successful for journeyId $journeyId")
                        Ok
                      }
                      .recover {
                        case e: Exception =>
                          logger.warn(
                            s"Tax credit renewal submission failed with exception ${e.getMessage} for journeyId $journeyId"
                          )
                          throw e
                      }
                  })
              )
          }
        }
    }

  private def validateTcrAuthHeader(
    mode:             Option[String]
  )(func:             HeaderCarrier => Future[mvc.Result]
  )(implicit request: Request[_],
    hc:               HeaderCarrier
  ): Future[Result] =
    (request.headers.get(tcrAuthToken), mode) match {

      case (None, Some(_)) => func(hc)

      case (Some(token), None) => func(hc.copy(extraHeaders = Seq(tcrAuthToken -> token)))

      case _ =>
        val default: ErrorResponse = ErrorNoAuthToken
        val authTokenShouldNotBeSupplied = ErrorAuthTokenSupplied
        val response = mode.fold(default) { _ =>
          authTokenShouldNotBeSupplied
        }
        logger.warn("Either tcrAuthToken must be supplied as header or 'claims' as query param.")
        Future.successful(Forbidden(toJson(response)))
    }
}
