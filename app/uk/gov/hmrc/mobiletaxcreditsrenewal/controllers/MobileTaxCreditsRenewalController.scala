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

import javax.inject.{Inject, Named, Singleton}
import play.api.Play.current
import play.api._
import play.api.http.HeaderNames
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsError, JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.api.controllers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException, ServiceUnavailableException}
import uk.gov.hmrc.mobiletaxcreditsrenewal.connectors.Error
import uk.gov.hmrc.mobiletaxcreditsrenewal.controllers.HeaderKeys.tcrAuthToken
import uk.gov.hmrc.mobiletaxcreditsrenewal.controllers.action.AccessControl
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._
import uk.gov.hmrc.mobiletaxcreditsrenewal.services.{LiveMobileTaxCreditsRenewalService, MobileTaxCreditsRenewalService, SandboxMobileTaxCreditsRenewalService}
import uk.gov.hmrc.play.HeaderCarrierConverter.fromHeadersAndSession
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.collection.Seq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

trait ErrorHandling {
  self: BaseController =>

  def notFound: Result = Status(ErrorNotFound.httpStatusCode)(toJson(ErrorNotFound))

  def errorWrapper(func: => Future[mvc.Result])(implicit hc: HeaderCarrier): Future[Result] = {
    func.recover {
      case _: NotFoundException => notFound

      case ex: ServiceUnavailableException =>
        // The hod can return a 503 HTTP status which is translated to a 429 response code.
        // The 503 HTTP status code must only be returned from the API gateway and not from downstream API's.
        Logger.error(s"ServiceUnavailableException reported: ${ex.getMessage}", ex)
        Status(ClientRetryRequest.httpStatusCode)(toJson(ClientRetryRequest))

      case e: Throwable =>
        Logger.error(s"Internal server error: ${e.getMessage}", e)
        Status(ErrorInternalServerError.httpStatusCode)(toJson(ErrorInternalServerError))
    }
  }
}

trait MobileTaxCreditsRenewalController extends BaseController with AccessControl with ErrorHandling with ConfigLoad {

  val service: MobileTaxCreditsRenewalService
  val taxCreditsSubmissionControlConfig: TaxCreditsControl
  val logger: LoggerLike

  def addCacheHeader(maxAge:Long, result:Result):Result = {
    result.withHeaders(HeaderNames.CACHE_CONTROL -> s"max-age=$maxAge")
  }

  final def getRenewalAuthentication(nino: Nino, renewalReference: RenewalReference, journeyId: Option[String] = None): Action[AnyContent] =
    validateAcceptWithAuth(acceptHeaderValidationRules, Option(nino)).async {
      implicit request =>
        implicit val hc: HeaderCarrier = fromHeadersAndSession(request.headers, None)
        errorWrapper(
          service.authenticateRenewal(nino, renewalReference).map {
            case Some(authToken) => Ok(toJson(authToken))
            case _ => notFound
          })
  }

  def claimantDetails(nino: Nino, journeyId: Option[String] = None): Action[AnyContent] =
    validateAcceptWithAuth(acceptHeaderValidationRules, Option(nino)).async{
      implicit request =>
        implicit val hc: HeaderCarrier = fromHeadersAndSession(request.headers, None)

        errorWrapper(validateTcrAuthHeader(None) {
          implicit hc =>

            service.claimantDetails(nino).map { claimant =>
              val mainApplicantFlag: String = if (claimant.mainApplicantNino == nino.value) "true" else "false"
              Ok(toJson(claimant.copy(mainApplicantNino = mainApplicantFlag)))
        }
      }
    )
  }

  final def claimsDetails(nino: Nino, journeyId: Option[String] = None): Action[AnyContent] =
    validateAcceptWithAuth(acceptHeaderValidationRules, Option(nino)).async {
      implicit request =>
        implicit val hc: HeaderCarrier = fromHeadersAndSession(request.headers, None)

        errorWrapper({
          def fullClaimantDetails(claim: Claim): Future[Claim] = {
            def getClaimantDetail(token:TcrAuthenticationToken, hc: HeaderCarrier): Future[Claim] = {
              implicit val hcWithToken: HeaderCarrier = hc.copy(extraHeaders = Seq(tcrAuthToken -> token.tcrAuthToken))

              service.claimantDetails(nino).map { claimantDetails =>
                claim.copy(
                  household = claim.household.copy(),
                  renewal = claim.renewal.copy(renewalFormType = Some(claimantDetails.renewalFormType)))
              }
            }

            service.authenticateRenewal(nino, RenewalReference(claim.household.barcodeReference)).flatMap { maybeToken =>
              if (maybeToken.nonEmpty) getClaimantDetail(maybeToken.get,hc)
              else  Future successful claim
            }.recover{
              case e: Exception =>
                logger.warn(s"${e.getMessage} for ${claim.household.barcodeReference}")
                claim
            }
          }

          val eventualClaims: Future[Seq[Claim]] = service.claimantClaims(nino).flatMap { claimantClaims =>
            val claims: Seq[Claim] = claimantClaims.references.getOrElse(Seq.empty[Claim])

            if ( claims.isEmpty ) logger.warn(s"Empty claims list for journeyId $journeyId")

            Future sequence claims.map { claim =>
              val barcodeReference = claim.household.barcodeReference

              if (barcodeReference.equals("000000000000000")) {
                logger.warn(s"Invalid barcode reference $barcodeReference for journeyId $journeyId applicationId ${claim.household.applicationID}")
                Future successful claim
              } else fullClaimantDetails(claim)
            }
          }

          eventualClaims.map{ claimss =>
            Ok(toJson(Claims(if (claimss.isEmpty) None else Some(claimss))))
          }
        })
  }

  final def submitRenewal(nino: Nino, journeyId: Option[String] = None): Action[JsValue] =
    validateAcceptWithAuth(acceptHeaderValidationRules, Option(nino)).async(BodyParsers.parse.json) {
      implicit request =>
        implicit val hc: HeaderCarrier = fromHeadersAndSession(request.headers, None)

        val enabled = taxCreditsSubmissionControlConfig.toTaxCreditsRenewalsState.submissionsState

        request.body.validate[TcrRenewal].fold(
          errors => {
            logger.warn("Received error with service submitRenewal: " + errors)
            Future.successful(BadRequest(Json.obj("message" -> JsError.toJson(errors))))
          },
          renewal => {
            errorWrapper(validateTcrAuthHeader(None) {
              implicit hc =>
                if (enabled != "open") {
                  logger.info("Renewals have been disabled.")
                  Future.successful(Ok)
                } else {
                  service.submitRenewal(nino, renewal).map {
                    case Error(status) =>
                      logger.warn(s"Tax credit renewal submission failed with status $status for journeyId $journeyId")
                      Status(status)(toJson(ErrorwithNtcRenewal))
                    case _ =>
                      logger.info(s"Tax credit renewal submission successful for journeyId $journeyId")
                      Ok
                  }.recover{
                    case e: Exception =>
                      logger.warn(s"Tax credit renewal submission failed with exception ${e.getMessage} for journeyId $journeyId")
                      throw e
                  }
                }
            })
          }
        )
  }

  private def validateTcrAuthHeader(mode:Option[String])(func: HeaderCarrier => Future[mvc.Result])(implicit request: Request[_], hc: HeaderCarrier) = {

    (request.headers.get(tcrAuthToken), mode) match {

      case (None , Some(_)) => func(hc)

      case (Some(token), None) => func(hc.copy(extraHeaders = Seq(tcrAuthToken -> token)))

      case _ =>
        val default: ErrorResponse = ErrorNoAuthToken
        val authTokenShouldNotBeSupplied = ErrorAuthTokenSupplied
        val response = mode.fold(default){ _ => authTokenShouldNotBeSupplied}
        logger.warn("Either tcrAuthToken must be supplied as header or 'claims' as query param.")
        Future.successful(Forbidden(toJson(response)))
    }
  }
}

trait ConfigLoad {
  val maxAgeClaimsConfig = "claims.maxAge"
  def getConfigForClaimsMaxAge:Option[Long]

  lazy val maxAgeForClaims: Long = getConfigForClaimsMaxAge
    .getOrElse(throw new Exception(s"Failed to resolve config key $maxAgeClaimsConfig"))
}

@Singleton
class SandboxMobileTaxCreditsRenewalController @Inject()(override val authConnector: AuthConnector,
                                                         @Named("controllers.confidenceLevel") override val confLevel: Int,
                                                         override val logger: LoggerLike) extends MobileTaxCreditsRenewalController {
  override lazy val requiresAuth: Boolean = false
  override val service: MobileTaxCreditsRenewalService = SandboxMobileTaxCreditsRenewalService
  override val taxCreditsSubmissionControlConfig: TaxCreditsControl = new TaxCreditsControl {
    override def toTaxCreditsSubmissions: TaxCreditsSubmissions = new TaxCreditsSubmissions(false, true, true )

    override def toTaxCreditsRenewalsState: TaxCreditsRenewalsState =
      TaxCreditsRenewalsState(submissionsState = "open")
  }
  override def getConfigForClaimsMaxAge: Option[Long] = current.configuration.getLong(maxAgeClaimsConfig)
}

@Singleton
class LiveMobileTaxCreditsRenewalController @Inject()(override val authConnector: AuthConnector,
                                                      @Named("controllers.confidenceLevel") override val confLevel: Int,
                                                      override val logger: LoggerLike,
                                                      override val service: LiveMobileTaxCreditsRenewalService,
                                                      override val taxCreditsSubmissionControlConfig: TaxCreditsSubmissionControlConfig) extends MobileTaxCreditsRenewalController {
  override def getConfigForClaimsMaxAge: Option[Long] = current.configuration.getLong(maxAgeClaimsConfig)
}
