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

package uk.gov.hmrc.mobiletaxcreditsrenewal.controllers

import javax.inject.{Inject, Named, Singleton}
import play.api._
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsError, JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.api.controllers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException, ServiceUnavailableException}
import uk.gov.hmrc.mobiletaxcreditsrenewal.connectors.ShutteringConnector
import uk.gov.hmrc.mobiletaxcreditsrenewal.controllers.action.{AccessControl, ShutteredCheck}
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._
import uk.gov.hmrc.mobiletaxcreditsrenewal.services.MobileTaxCreditsRenewalService
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.HeaderCarrierConverter.fromHeadersAndSession
import uk.gov.hmrc.play.bootstrap.controller.BackendBaseController

import scala.collection.Seq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

trait LegacyMobileTaxCreditsRenewalController extends BackendBaseController with HeaderValidator {

  def getRenewalAuthentication(nino: Nino, renewalReference: RenewalReference, journeyId: String): Action[AnyContent]

  def claimantDetails(nino: Nino, journeyId: String, claims: Option[String] = None): Action[AnyContent]

  def fullClaimantDetails(nino: Nino, journeyId: String): Action[AnyContent]

  def submitRenewal(nino: Nino, journeyId: String): Action[JsValue]

  def taxCreditsSubmissionStateEnabled(journeyId: String): Action[AnyContent]
}

@Singleton
class LiveLegacyMobileTaxCreditsRenewalController @Inject()(
  override val authConnector:                                   AuthConnector,
  val logger:                                                   LoggerLike,
  val service:                                                  MobileTaxCreditsRenewalService,
  val taxCreditsControl:                                        TaxCreditsControl,
  @Named("controllers.confidenceLevel") override val confLevel: Int,
  val controllerComponents:                                     ControllerComponents,
  shutteringConnector:                                          ShutteringConnector
)(
  implicit val executionContext: ExecutionContext
) extends LegacyMobileTaxCreditsRenewalController
    with AccessControl
    with ShutteredCheck {

  override def parser: BodyParser[AnyContent] = controllerComponents.parsers.anyContent

  private val tcrAuthToken = "tcrAuthToken"

  def notFound: Result = Status(ErrorNotFound.httpStatusCode)(toJson(ErrorNotFound))

  def errorWrapper(func: => Future[mvc.Result])(implicit hc: HeaderCarrier): Future[Result] =
    func.recover {
      case _: NotFoundException => notFound

      case ex: ServiceUnavailableException =>
        Logger.error(s"ServiceUnavailableException reported: ${ex.getMessage}", ex)
        Status(ClientRetryRequest.httpStatusCode)(toJson(ClientRetryRequest))

      case e: Throwable =>
        Logger.error(s"Internal server error: ${e.getMessage}", e)
        Status(ErrorInternalServerError.httpStatusCode)(toJson(ErrorInternalServerError))
    }

  override def getRenewalAuthentication(nino: Nino, renewalReference: RenewalReference, journeyId: String): Action[AnyContent] =
    validateAcceptWithAuth(acceptHeaderValidationRules, Option(nino)).async { implicit request =>
      implicit val hc: HeaderCarrier = fromHeadersAndSession(request.headers, None)
      shutteringConnector.getShutteringStatus(journeyId).flatMap { shuttered =>
        withShuttering(shuttered) {
          errorWrapper(
            service.authenticateRenewal(nino, renewalReference).map {
              case Some(authToken) => Ok(toJson(authToken))
              case _ => notFound
            }
          )
        }
      }
    }

  override def claimantDetails(nino: Nino, journeyId: String, claims: Option[String] = None): Action[AnyContent] =
    validateAcceptWithAuth(acceptHeaderValidationRules, Option(nino)).async { implicit request =>
      implicit val hc: HeaderCarrier = fromHeadersAndSession(request.headers, None)
      shutteringConnector.getShutteringStatus(journeyId).flatMap { shuttered =>
        withShuttering(shuttered) {
          errorWrapper(validateTcrAuthHeader(claims) { implicit hc =>
            def singleClaim: Future[Result] = addMainApplicantFlag(nino)

            def retrieveAllClaims: Future[Result] = service.legacyClaimantClaims(nino).map { claims =>
              claims.references.fold(notFound) { found =>
                if (found.isEmpty) notFound else Ok(toJson(claims))
              }
            }

            claims.fold(singleClaim) { _ =>
              retrieveAllClaims
            }
          })
        }
      }
    }

  def addMainApplicantFlag(nino: Nino)(implicit headerCarrier: HeaderCarrier, ex: ExecutionContext): Future[Result] =
    service.claimantDetails(nino).map { claim =>
      val mainApplicantFlag: String = if (claim.mainApplicantNino == nino.value) "true" else "false"
      Ok(toJson(claim.copy(mainApplicantNino = mainApplicantFlag)))
    }

  override def fullClaimantDetails(nino: Nino, journeyId: String): Action[AnyContent] =
    validateAcceptWithAuth(acceptHeaderValidationRules, Option(nino)).async { implicit request =>
      implicit val hc: HeaderCarrier = fromHeadersAndSession(request.headers, None)
      shutteringConnector.getShutteringStatus(journeyId).flatMap { shuttered =>
        withShuttering(shuttered) {

          def swapRtiIfNeeded(optApplicantNino: Option[String], urlNino: Nino, rtiEmployedEarnings: Option[Double], rtiEmployedEarningsPartner: Option[Double]): Option[Double] =
            optApplicantNino.flatMap(applicantNino => if (applicantNino == urlNino.value) rtiEmployedEarnings else rtiEmployedEarningsPartner)

          errorWrapper({
            def fullClaimantDetails(claim: LegacyClaim): Future[LegacyClaim] = {
              def getClaimantDetail(token: TcrAuthenticationToken, hc: HeaderCarrier): Future[LegacyClaim] = {
                implicit val hcWithToken: HeaderCarrier = hc.copy(extraHeaders = Seq(tcrAuthToken -> token.tcrAuthToken))

                service.claimantDetails(nino).flatMap {
                  claimantDetails =>
                    service.employedEarningsRti(nino) map {
                      case Some(employedEarningsRti) =>
                        employedEarningsRti match {
                          case EmployedEarningsRti(Some(rtiEmployedEarnings), Some(rtiEmployedEarningsPartner)) =>
                            claim.copy(
                              household = claim.household.copy(
                                applicant1 = claim.household.applicant1.copy(previousYearRtiEmployedEarnings = swapRtiIfNeeded(Some(claim.household.applicant1.nino), nino, Some(rtiEmployedEarnings), Some(rtiEmployedEarningsPartner))),
                                applicant2 = claim.household.applicant2.map(_.copy(previousYearRtiEmployedEarnings = swapRtiIfNeeded(claim.household.applicant2.map(_.nino), nino, Some(rtiEmployedEarnings), Some(rtiEmployedEarningsPartner))))
                              ),
                              renewal = claim.renewal.copy(renewalFormType = Some(claimantDetails.renewalFormType))
                            )
                          case EmployedEarningsRti(_, Some(rtiEmployedEarningsPartner)) =>
                            claim.copy(
                              household = claim.household.copy(
                                applicant1 = claim.household.applicant1.copy(previousYearRtiEmployedEarnings = swapRtiIfNeeded(Some(claim.household.applicant1.nino), nino, None, Some(rtiEmployedEarningsPartner))),
                                applicant2 = claim.household.applicant2.map(_.copy(previousYearRtiEmployedEarnings = swapRtiIfNeeded(claim.household.applicant2.map(_.nino), nino, None, Some(rtiEmployedEarningsPartner))))
                              ),
                              renewal = claim.renewal.copy(renewalFormType = Some(claimantDetails.renewalFormType))
                            )
                          case EmployedEarningsRti(Some(rtiEmployedEarnings), _) =>
                            claim.copy(
                              household = claim.household.copy(
                                applicant1 = claim.household.applicant1.copy(previousYearRtiEmployedEarnings = swapRtiIfNeeded(Some(claim.household.applicant1.nino), nino, Some(rtiEmployedEarnings), None)),
                                applicant2 = claim.household.applicant2.map(_.copy(previousYearRtiEmployedEarnings = swapRtiIfNeeded(claim.household.applicant2.map(_.nino), nino, Some(rtiEmployedEarnings), None)))
                              ),
                              renewal = claim.renewal.copy(renewalFormType = Some(claimantDetails.renewalFormType))
                            )
                          case _ => claim.copy(renewal = claim.renewal.copy(renewalFormType = Some(claimantDetails.renewalFormType)))
                        }
                      case _ => claim.copy(renewal = claim.renewal.copy(renewalFormType = Some(claimantDetails.renewalFormType)))
                    }
                }
              }

              service
                .authenticateRenewal(nino, RenewalReference(claim.household.barcodeReference))
                .flatMap { maybeToken =>
                  if (maybeToken.nonEmpty) getClaimantDetail(maybeToken.get, hc)
                  else Future successful claim
                }
                .recover {
                  case e: Exception =>
                    logger.warn(s"${e.getMessage} for ${claim.household.barcodeReference}")
                    claim
                }
            }

            val eventualClaims: Future[Seq[LegacyClaim]] = service.legacyClaimantClaims(nino).flatMap {
              claimantClaims =>
                val claims: Seq[LegacyClaim] = claimantClaims.references.getOrElse(Seq.empty[LegacyClaim])

                if (claims.isEmpty) logger.warn(s"Empty claims list for journeyId $journeyId")

                Future sequence claims.map { claim =>
                  val barcodeReference = claim.household.barcodeReference

                  if (barcodeReference.equals("000000000000000")) {
                    logger.warn(s"Invalid barcode reference $barcodeReference for journeyId $journeyId applicationId ${claim.household.applicationID}")
                    Future successful claim
                  } else fullClaimantDetails(claim)
                }
            }

            eventualClaims.map { claimantDetails =>
              Ok(toJson(LegacyClaims(if (claimantDetails.isEmpty) None else Some(claimantDetails))))
            }
          })
        }
      }
    }

  override def submitRenewal(nino: Nino, journeyId: String): Action[JsValue] =
    validateAcceptWithAuth(acceptHeaderValidationRules, Option(nino)).async(controllerComponents.parsers.json) { implicit request =>
      implicit val hc: HeaderCarrier = fromHeadersAndSession(request.headers, None)
      shutteringConnector.getShutteringStatus(journeyId).flatMap { shuttered =>
        withShuttering(shuttered) {
          request.body
            .validate[TcrRenewal]
            .fold(
              errors => {
                logger.warn("Received error with service submitRenewal: " + errors)
                Future.successful(BadRequest(Json.obj("message" -> JsError.toJson(errors))))
              },
              renewal => {
                errorWrapper(validateTcrAuthHeader(None) { implicit hc =>
                  service
                    .submitRenewal(nino, renewal)
                    .map { _ =>
                      logger.info(s"Tax credit renewal submission successful for journeyId $journeyId")
                      Ok
                    }
                    .recover {
                      case e: Exception =>
                        logger.warn(s"Tax credit renewal submission failed with exception ${e.getMessage} for journeyId $journeyId")
                        throw e
                    }
                })
              }
            )
        }
      }
    }

  override def taxCreditsSubmissionStateEnabled(journeyId: String): Action[AnyContent] =
    validateAccept(acceptHeaderValidationRules).async { implicit request =>
      implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, None)
      errorWrapper(Future {
        taxCreditsControl.toTaxCreditsRenewalsState
      }.map { submissionState =>
        Ok(Json.toJson(submissionState))
      })
    }

  private def validateTcrAuthHeader(mode: Option[String])(
    func:                                 HeaderCarrier => Future[mvc.Result])(implicit request: Request[_], hc: HeaderCarrier): Future[Result] =
    (request.headers.get(tcrAuthToken), mode) match {
      case (None, Some(_))     => func(hc)
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
