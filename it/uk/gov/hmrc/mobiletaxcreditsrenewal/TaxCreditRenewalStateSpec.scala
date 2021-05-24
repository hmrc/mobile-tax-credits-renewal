package uk.gov.hmrc.mobiletaxcreditsrenewal

import java.time.{LocalDateTime, ZoneId}

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.libs.json.Json.{parse, toJson}
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.libs.ws.{WSRequest, WSResponse}
import uk.gov.hmrc.api.sandbox.FileResource
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.{IncomeDetails, RenewalData, RenewalReference, Shuttering, TcrRenewal}
import uk.gov.hmrc.mobiletaxcreditsrenewal.stubs.AuthStub.grantAccess
import uk.gov.hmrc.mobiletaxcreditsrenewal.stubs.NtcStub._
import uk.gov.hmrc.mobiletaxcreditsrenewal.stubs.ShutteringStub._
import uk.gov.hmrc.mobiletaxcreditsrenewal.stubs.TaxCreditsBrokerStub._
import uk.gov.hmrc.mobiletaxcreditsrenewal.support.BaseISpec

class TaxCreditRenewalStateSpec extends BaseISpec with FileResource {
  protected val incomeDetails = IncomeDetails(Some(10), Some(20), Some(30), Some(40), Some(true))

  protected val renewal =
    TcrRenewal(RenewalData(Some(incomeDetails), None, None), None, None, None, hasChangeOfCircs = false)

  protected val now: LocalDateTime = LocalDateTime.now(ZoneId.of("Europe/London"))
  val barcodeReference = RenewalReference("200000000000013")

  protected val submissionStateEnabledRequest: WSRequest = wsUrl(
    s"/income/tax-credits/submission/state/enabled?journeyId=87144372-6bda-4cc9-87db-1d52fd96498f"
  ).addHttpHeaders(acceptJsonHeader)

  protected val renewalsRequest: WSRequest =
    wsUrl(s"/renewals/${nino1.value}?journeyId=87144372-6bda-4cc9-87db-1d52fd96498f").addHttpHeaders(acceptJsonHeader)

  protected def submissionStartDate: String = now.minusDays(1).toString

  protected def submissionEndDate: String = now.plusDays(1).toString

  protected def endViewRenewalsDate: String = now.plusDays(2).toString

  override def config: Map[String, Any] =
    super.config ++
    Map(
      "microservice.services.ntc.submission.startDate"           -> submissionStartDate,
      "microservice.services.ntc.submission.endDate"             -> submissionEndDate,
      "microservice.services.ntc.submission.endViewRenewalsDate" -> endViewRenewalsDate
    )

  def submitTaxCreditRenewal: WSResponse = {
    def request(nino: Nino) =
      wsUrl(s"/declarations/${nino.value}?journeyId=87144372-6bda-4cc9-87db-1d52fd96498f")
        .addHttpHeaders(acceptJsonHeader, tcrAuthTokenHeader)

    grantAccess(nino1.value)

    val renewalJson = toJson(renewal)
    await(request(nino1).post(renewalJson))
  }

  protected def verifyNoSubmissionForPostToTaxCreditsRenewlEndpoint(): Unit = {
    val response = submitTaxCreditRenewal
    response.status shouldBe 200
    verify(0, postRequestedFor(urlEqualTo(s"/tcs/${nino1.value}/renewal")))
  }

  "GET /income/:nino/tax-credits/full-claimant-details" should {
    val mainApplicantNino = Nino("CS700100A")
    val barcodeReference  = RenewalReference("200000000000013")
    val request = wsUrl(
      s"/income/${mainApplicantNino.value}/tax-credits/full-claimant-details?journeyId=87144372-6bda-4cc9-87db-1d52fd96498f"
    ).addHttpHeaders(acceptJsonHeader, tcrAuthTokenHeader)

    "retrieve claimant claims for main applicant and set renewalFormType for a renewal where bar code ref is not '000000000000000'" in {
      grantAccess(mainApplicantNino.value)
      claimantClaimsAreFound(mainApplicantNino, barcodeReference)
      authenticationRenewalSuccessful(mainApplicantNino, barcodeReference, tcrAuthenticationToken)
      claimantDetailsAreFoundFor(mainApplicantNino, mainApplicantNino, nino2, tcrAuthenticationToken)

      val response = await(request.get())
      response.status shouldBe 200

      val expectedJson = parse(findResource("/resources/claimantdetails/tax-credit-claimant-details-response.json").get)
      response.json shouldBe expectedJson

      val references = (response.json \ "references").as[JsArray]
      val renewal    = (references(0) \ "renewal").as[JsObject]
      renewal.value("renewalFormType").as[String] shouldBe "D"
    }

    "retrieve claimant claims for main applicant and not set renewalFormType if no token is found" in {
      grantAccess(mainApplicantNino.value)
      claimantClaimsAreFound(mainApplicantNino, barcodeReference)
      authenticationRenewalNotFound(mainApplicantNino, barcodeReference)

      val response = await(request.get())
      response.status shouldBe 200

      val references = (response.json \ "references").as[JsArray]
      val renewal    = (references(0) \ "renewal").as[JsObject]
      renewal.value.get("renewalFormType") shouldBe None

      verify(0, getRequestedFor(urlEqualTo(s"/tcs/${mainApplicantNino.value}/claimant-details")))
    }

    "retrieve claimant claims for main applicant and not set renewalFormType if no claimant details found" in {
      grantAccess(mainApplicantNino.value)
      claimantClaimsAreFound(mainApplicantNino, barcodeReference)
      authenticationRenewalSuccessful(mainApplicantNino, barcodeReference, tcrAuthenticationToken)
      claimantDetailsAreNotFoundFor(mainApplicantNino)

      val response = await(request.get())
      response.status shouldBe 200

      val references = (response.json \ "references").as[JsArray]
      val renewal    = (references(0) \ "renewal").as[JsObject]
      renewal.value.get("renewalFormType") shouldBe None
    }

    "retrieve claimant claims for main applicant with applicant1 employed earnings RTI" in {
      grantAccess(mainApplicantNino.value)
      claimantClaimsAreFound(mainApplicantNino, barcodeReference)
      authenticationRenewalSuccessful(mainApplicantNino, barcodeReference, tcrAuthenticationToken)
      employedEarningsRtiFound(mainApplicantNino, false)
      claimantDetailsAreFoundFor(mainApplicantNino, mainApplicantNino, nino2, tcrAuthenticationToken)

      val response = await(request.get())
      response.status shouldBe 200

      val references = (response.json \ "references").as[JsArray]
      val previousYearRtiEmployedEarnings =
        (references(0) \ "household" \ "applicant1" \ "previousYearRtiEmployedEarnings").as[Double]
      previousYearRtiEmployedEarnings shouldBe 25444.99
    }

    "retrieve claimant claims for main applicant with applicant1 and applicant2 employed earnings RTI" in {
      grantAccess(mainApplicantNino.value)
      claimantClaimsAreFoundWithPartner(mainApplicantNino, nino2, barcodeReference)
      authenticationRenewalSuccessful(mainApplicantNino, barcodeReference, tcrAuthenticationToken)
      employedEarningsRtiFound(mainApplicantNino, true)
      claimantDetailsAreFoundFor(mainApplicantNino, mainApplicantNino, nino2, tcrAuthenticationToken)

      val response = await(request.get())
      response.status shouldBe 200

      val references = (response.json \ "references").as[JsArray]
      val applicant1PreviousYearRtiEmployedEarnings =
        (references(0) \ "household" \ "applicant1" \ "previousYearRtiEmployedEarnings").as[Double]
      val applicant2PreviousYearRtiEmployedEarnings =
        (references(0) \ "household" \ "applicant2" \ "previousYearRtiEmployedEarnings").as[Double]

      applicant1PreviousYearRtiEmployedEarnings shouldBe 25444.99
      applicant2PreviousYearRtiEmployedEarnings shouldBe 20000.0
    }

    "retrieve claimant claims for main applicant with no employed earnings RTI" in {
      grantAccess(mainApplicantNino.value)
      claimantClaimsAreFound(mainApplicantNino, barcodeReference)
      authenticationRenewalSuccessful(mainApplicantNino, barcodeReference, tcrAuthenticationToken)
      employedEarningsRtiError(mainApplicantNino, 404)
      claimantDetailsAreFoundFor(mainApplicantNino, mainApplicantNino, nino2, tcrAuthenticationToken)

      val response = await(request.get())
      response.status shouldBe 200

      val references = (response.json \ "references").as[JsArray]
      val applicant1 = (references(0) \ "household" \ "applicant1").as[JsObject]
      applicant1.value.get("previousYearRtiEmployedEarnings") shouldBe None
    }

    "retrieve claimant claims for main applicant with a 500 error from tax-credits-broker" in {
      grantAccess(mainApplicantNino.value)
      claimantClaimsAreFound(mainApplicantNino, barcodeReference)
      authenticationRenewalSuccessful(mainApplicantNino, barcodeReference, tcrAuthenticationToken)
      employedEarningsRtiError(mainApplicantNino, 500)
      claimantDetailsAreFoundFor(mainApplicantNino, mainApplicantNino, nino2, tcrAuthenticationToken)

      val response = await(request.get())
      response.status shouldBe 200

      val references = (response.json \ "references").as[JsArray]
      val applicant1 = (references(0) \ "household" \ "applicant1").as[JsObject]
      applicant1.value.get("previousYearRtiEmployedEarnings") shouldBe None
    }

    "return 400 if no journeyId is supplied" in {
      grantAccess(mainApplicantNino.value)
      claimantClaimsAreFound(mainApplicantNino, barcodeReference)
      authenticationRenewalNotFound(mainApplicantNino, barcodeReference)

      val response = await(
        wsUrl(s"/income/${mainApplicantNino.value}/tax-credits/full-claimant-details")
          .addHttpHeaders(acceptJsonHeader, tcrAuthTokenHeader)
          .get()
      )
      response.status shouldBe 400
    }

    "return 400 if invalid journeyId is supplied" in {
      grantAccess(mainApplicantNino.value)
      claimantClaimsAreFound(mainApplicantNino, barcodeReference)
      authenticationRenewalNotFound(mainApplicantNino, barcodeReference)

      val response = await(
        wsUrl(
          s"/income/${mainApplicantNino.value}/tax-credits/full-claimant-details?journeyId=ThisIsAnInvalidJourneyId"
        ).addHttpHeaders(acceptJsonHeader, tcrAuthTokenHeader).get()
      )
      response.status shouldBe 400
    }

    "return SHUTTERED when shuttered" in {
      stubForShutteringEnabled
      grantAccess(mainApplicantNino.value)

      val response = await(request.get())
      response.status shouldBe 521
      val shuttering: Shuttering = Json.parse(response.body).as[Shuttering]
      shuttering.shuttered shouldBe true
      shuttering.title     shouldBe Some("Shuttered")
      shuttering.message   shouldBe Some("Tax Credits Renewal is currently not available")
    }

  }
}

class TaxCreditRenewalOpenStateSpec extends TaxCreditRenewalStateSpec {
  "GET /income/tax-credits/submission/state/enabled" should {
    "return open state" in {
      val response = await(submissionStateEnabledRequest.get)
      response.status                                 shouldBe 200
      (response.json \ "submissionsState").as[String] shouldBe "open"
    }
    "return 400 when journeyId not supplied" in {
      val response = await(wsUrl("/income/tax-credits/submission/state/enabled").addHttpHeaders(acceptJsonHeader).get)
      response.status shouldBe 400
    }
    "return 400 when invalid journeyId supplied" in {
      val response = await(
        wsUrl("/income/tax-credits/submission/state/enabled?journeyId=ThisIsAnInvalidJourneyId")
          .addHttpHeaders(acceptJsonHeader)
          .get
      )
      response.status shouldBe 400
    }

  }
}

class TaxCreditRenewalClosedStateSpec extends TaxCreditRenewalStateSpec {
  override def submissionStartDate: String = now.plusDays(1).toString

  override def submissionEndDate: String = now.plusDays(2).toString

  override def endViewRenewalsDate: String = now.plusDays(3).toString

  "GET /income/tax-credits/submission/state/enabled" should {
    "return closed state so that no new renewals can be started" in {
      val response = await(submissionStateEnabledRequest.get)
      response.status                                 shouldBe 200
      (response.json \ "submissionsState").as[String] shouldBe "closed"
    }
  }
}

class TaxCreditRenewalCheckStatusOnlyPeriodStateSpec extends TaxCreditRenewalStateSpec {
  override def submissionStartDate: String = now.minusDays(2).toString

  override def submissionEndDate: String = now.minusDays(1).toString

  override def endViewRenewalsDate: String = now.plusDays(1).toString

  "GET /income/tax-credits/submission/state/enabled" should {
    "return check-only state so that no new renewals can be started" in {
      val response = await(submissionStateEnabledRequest.get)
      response.status                                 shouldBe 200
      (response.json \ "submissionsState").as[String] shouldBe "check_status_only"
    }
  }
}
