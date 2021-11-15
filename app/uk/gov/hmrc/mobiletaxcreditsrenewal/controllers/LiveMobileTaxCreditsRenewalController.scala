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
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.api.controllers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mobiletaxcreditsrenewal.connectors.ShutteringConnector
import uk.gov.hmrc.mobiletaxcreditsrenewal.controllers.action.{AccessControl, ShutteredCheck}
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.types.ModelTypes.JourneyId
import uk.gov.hmrc.mobiletaxcreditsrenewal.services.MobileTaxCreditsRenewalService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendBaseController
import uk.gov.hmrc.play.http.HeaderCarrierConverter.fromRequest

import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

trait LegacyMobileTaxCreditsRenewalController extends BackendBaseController with HeaderValidator {

  def fullClaimantDetails(
    nino:      Nino,
    journeyId: JourneyId
  ): Action[AnyContent]

  def taxCreditsSubmissionStateEnabled(journeyId: JourneyId): Action[AnyContent]
}

@Singleton
class LiveMobileTaxCreditsRenewalController @Inject()(
  override val authConnector:                                   AuthConnector,
  val service:                                                  MobileTaxCreditsRenewalService,
  val taxCreditsControl:                                        TaxCreditsControl,
  @Named("controllers.confidenceLevel") override val confLevel: Int,
  val controllerComponents:                                     ControllerComponents,
  shutteringConnector:                                          ShutteringConnector
)(implicit val executionContext:                                ExecutionContext)
    extends LegacyMobileTaxCreditsRenewalController
    with AccessControl
    with ShutteredCheck {

  override def parser: BodyParser[AnyContent] = controllerComponents.parsers.anyContent

  private val tcrAuthToken = "tcrAuthToken"
  override val logger: Logger = Logger(this.getClass)

  def notFound: Result = Status(ErrorNotFound.httpStatusCode)(toJson[ErrorResponse](ErrorNotFound))

  def errorWrapper(func: => Future[mvc.Result]): Future[Result] =
    func.recover {
      case ex: UpstreamErrorResponse if ex.statusCode == NOT_FOUND => notFound

      case ex: UpstreamErrorResponse if ex.statusCode == SERVICE_UNAVAILABLE =>
        logger.error(s"ServiceUnavailableException reported: ${ex.getMessage}", ex)
        Status(ClientRetryRequest.httpStatusCode)(toJson[ErrorResponse](ClientRetryRequest))

      case e: Throwable =>
        logger.error(s"Internal server error: ${e.getMessage}", e)
        Status(ErrorInternalServerError.httpStatusCode)(toJson[ErrorResponse](ErrorInternalServerError))
    }

  override def fullClaimantDetails(
    nino:      Nino,
    journeyId: JourneyId
  ): Action[AnyContent] =
    validateAcceptWithAuth(acceptHeaderValidationRules, Option(nino)).async { implicit request =>
      implicit val hc: HeaderCarrier = fromRequest(request)
      shutteringConnector.getShutteringStatus(journeyId).flatMap { shuttered =>
        withShuttering(shuttered) {

          def swapRtiIfNeeded(
            optApplicantNino:           Option[String],
            urlNino:                    Nino,
            rtiEmployedEarnings:        Option[Double],
            rtiEmployedEarningsPartner: Option[Double]
          ): Option[Double] =
            optApplicantNino.flatMap(applicantNino =>
              if (applicantNino == urlNino.value) rtiEmployedEarnings
              else rtiEmployedEarningsPartner
            )

          errorWrapper({
            def fullClaimantDetails(claim: Claim): Future[Claim] = {
              def getClaimantDetail(
                token: TcrAuthenticationToken,
                hc:    HeaderCarrier
              ): Future[Claim] = {
                implicit val hcWithToken: HeaderCarrier =
                  hc.copy(extraHeaders = Seq(tcrAuthToken -> token.tcrAuthToken))

                service.claimantDetails(nino).flatMap {
                  claimantDetails =>
                    service.employedEarningsRti(nino) map {
                      case Some(employedEarningsRti) =>
                        employedEarningsRti match {
                          case EmployedEarningsRti(Some(rtiEmployedEarnings), Some(rtiEmployedEarningsPartner)) =>
                            claim.copy(
                              household = claim.household.copy(
                                applicant1 = claim.household.applicant1.copy(previousYearRtiEmployedEarnings =
                                  swapRtiIfNeeded(Some(claim.household.applicant1.nino),
                                                  nino,
                                                  Some(rtiEmployedEarnings),
                                                  Some(rtiEmployedEarningsPartner))
                                ),
                                applicant2 = claim.household.applicant2.map(
                                  _.copy(previousYearRtiEmployedEarnings =
                                    swapRtiIfNeeded(claim.household.applicant2.map(_.nino),
                                                    nino,
                                                    Some(rtiEmployedEarnings),
                                                    Some(rtiEmployedEarningsPartner))
                                  )
                                )
                              ),
                              renewal = claim.renewal.copy(renewalFormType = Some(claimantDetails.renewalFormType))
                            )
                          case EmployedEarningsRti(_, Some(rtiEmployedEarningsPartner)) =>
                            claim.copy(
                              household = claim.household.copy(
                                applicant1 = claim.household.applicant1.copy(previousYearRtiEmployedEarnings =
                                  swapRtiIfNeeded(Some(claim.household.applicant1.nino),
                                                  nino,
                                                  None,
                                                  Some(rtiEmployedEarningsPartner))
                                ),
                                applicant2 = claim.household.applicant2.map(
                                  _.copy(previousYearRtiEmployedEarnings =
                                    swapRtiIfNeeded(claim.household.applicant2.map(_.nino),
                                                    nino,
                                                    None,
                                                    Some(rtiEmployedEarningsPartner))
                                  )
                                )
                              ),
                              renewal = claim.renewal.copy(renewalFormType = Some(claimantDetails.renewalFormType))
                            )
                          case EmployedEarningsRti(Some(rtiEmployedEarnings), _) =>
                            claim.copy(
                              household = claim.household.copy(
                                applicant1 = claim.household.applicant1.copy(previousYearRtiEmployedEarnings =
                                  swapRtiIfNeeded(Some(claim.household.applicant1.nino),
                                                  nino,
                                                  Some(rtiEmployedEarnings),
                                                  None)
                                ),
                                applicant2 = claim.household.applicant2.map(
                                  _.copy(previousYearRtiEmployedEarnings =
                                    swapRtiIfNeeded(claim.household.applicant2.map(_.nino),
                                                    nino,
                                                    Some(rtiEmployedEarnings),
                                                    None)
                                  )
                                )
                              ),
                              renewal = claim.renewal.copy(renewalFormType = Some(claimantDetails.renewalFormType))
                            )
                          case _ =>
                            claim.copy(renewal =
                              claim.renewal.copy(renewalFormType = Some(claimantDetails.renewalFormType))
                            )
                        }
                      case _ =>
                        claim.copy(renewal = claim.renewal.copy(renewalFormType = Some(claimantDetails.renewalFormType))
                        )
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

            val eventualClaims: Future[Seq[Claim]] = service.legacyClaimantClaims(nino).flatMap {
              claimantClaims =>
                val claims: Seq[Claim] = claimantClaims.references.getOrElse(Seq.empty[Claim])

                if (claims.isEmpty) logger.warn(s"Empty claims list for journeyId $journeyId")

                Future sequence claims.map { claim =>
                  val barcodeReference = claim.household.barcodeReference

                  if (barcodeReference.equals("000000000000000")) {
                    logger.warn(
                      s"Invalid barcode reference $barcodeReference for journeyId $journeyId applicationId ${claim.household.applicationID}"
                    )
                    Future successful claim
                  } else fullClaimantDetails(claim)
                }
            }

            eventualClaims.map { claimantDetails =>
              Ok(toJson(Claims(if (claimantDetails.isEmpty) None else Some(claimantDetails))))
            }
          })
        }
      }
    }

  override def taxCreditsSubmissionStateEnabled(journeyId: JourneyId): Action[AnyContent] =
    validateAccept(acceptHeaderValidationRules).async { implicit request =>
      implicit val hc: HeaderCarrier = fromRequest(request)
      errorWrapper(Future {
        taxCreditsControl.toTaxCreditsRenewalsState
      }.map { submissionState =>
        Ok(Json.toJson(submissionState))
      })
    }

}
