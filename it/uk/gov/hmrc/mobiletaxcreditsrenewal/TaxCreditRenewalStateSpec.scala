package uk.gov.hmrc.mobiletaxcreditsrenewal

import com.github.tomakehurst.wiremock.client.WireMock._
import org.joda.time.DateTime
import org.joda.time.DateTimeZone.UTC
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
import uk.gov.hmrc.time.DateTimeUtils

class TaxCreditRenewalStateSpec extends BaseISpec with FileResource {
  protected val incomeDetails = IncomeDetails(Some(10), Some(20), Some(30), Some(40), Some(true))

  protected val renewal =
    TcrRenewal(RenewalData(Some(incomeDetails), None, None), None, None, None, hasChangeOfCircs = false)

  protected val now: DateTime = DateTimeUtils.now.withZone(UTC)
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

  "POST /declarations/:nino" should {
    "renew successfully" in {
      renewalIsSuccessful(nino1, renewal)
      submitTaxCreditRenewal.status shouldBe 200
      verify(1, postRequestedFor(urlEqualTo(s"/tcs/${nino1.value}/renewal")))
    }

    "handle failure" in {
      renewalFails(nino1, renewal)
      val response = submitTaxCreditRenewal
      response.status shouldBe 500
      verify(1, postRequestedFor(urlEqualTo(s"/tcs/${nino1.value}/renewal")))
    }

    "handle bad request" in {
      def request(nino: Nino) =
        wsUrl(s"/declarations/${nino.value}?journeyId=87144372-6bda-4cc9-87db-1d52fd96498f")
          .addHttpHeaders(acceptJsonHeader, tcrAuthTokenHeader)

      grantAccess(nino1.value)

      val renewalJson = toJson(incomeDetails)
      val response    = await(request(nino1).post(renewalJson))
      response.status shouldBe 400
    }

    "return SHUTTERED when shuttered" in {
      stubForShutteringEnabled
      grantAccess(nino1.value)

      val response = submitTaxCreditRenewal
      response.status shouldBe 521
      val shuttering: Shuttering = Json.parse(response.body).as[Shuttering]
      shuttering.shuttered shouldBe true
      shuttering.title     shouldBe Some("Shuttered")
      shuttering.message   shouldBe Some("Tax Credits Renewal is currently not available")
    }
  }

  "GET /income/:nino/tax-credits/:renewalReference/auth" should {
    val url = wsUrl(
      s"/income/${nino1.value}/tax-credits/${renewalReference.value}/auth?journeyId=87144372-6bda-4cc9-87db-1d52fd96498f"
    ).addHttpHeaders(acceptJsonHeader)

    "return a tcrAuthenticationToken" in {
      grantAccess(nino1.value)
      authenticationRenewalSuccessful(nino1, renewalReference, tcrAuthenticationToken)

      val response = await(url.get())

      response.status                             shouldBe 200
      (response.json \ "tcrAuthToken").as[String] shouldBe tcrAuthenticationToken
    }

    "return 404 when no auth tcrAuthenticationToken is found" in {
      grantAccess(nino1.value)
      authenticationRenewalNotFound(nino1, renewalReference)

      val response = await(url.get())

      response.status shouldBe 404
    }

    "return 400 when no journeyId is supplied" in {
      grantAccess(nino1.value)
      authenticationRenewalSuccessful(nino1, renewalReference, tcrAuthenticationToken)

      val response = await(
        wsUrl(s"/income/${nino1.value}/tax-credits/${renewalReference.value}/auth")
          .addHttpHeaders(acceptJsonHeader)
          .get()
      )

      response.status shouldBe 400
    }

    "return 400 when invalid journeyId is supplied" in {
      grantAccess(nino1.value)
      authenticationRenewalSuccessful(nino1, renewalReference, tcrAuthenticationToken)

      val response = await(
        wsUrl(s"/income/${nino1.value}/tax-credits/${renewalReference.value}/auth?journeyId=ThisIsAnInvalidJourneyId")
          .addHttpHeaders(acceptJsonHeader)
          .get()
      )

      response.status shouldBe 400
    }

    "return SHUTTERED when shuttered" in {
      stubForShutteringEnabled
      grantAccess(nino1.value)

      val response = await(url.get())
      response.status shouldBe 521
      val shuttering: Shuttering = Json.parse(response.body).as[Shuttering]
      shuttering.shuttered shouldBe true
      shuttering.title     shouldBe Some("Shuttered")
      shuttering.message   shouldBe Some("Tax Credits Renewal is currently not available")
    }
  }

  "GET /income/:nino/tax-credits/claimant-details" should {
    def request(nino: Nino) =
      wsUrl(s"/income/${nino.value}/tax-credits/claimant-details?journeyId=87144372-6bda-4cc9-87db-1d52fd96498f")
        .addHttpHeaders(acceptJsonHeader, tcrAuthTokenHeader)

    "retrieve claimant details for main applicant" in {
      grantAccess(nino1.value)
      claimantDetailsAreFoundFor(nino1, nino1, nino2, tcrAuthenticationToken)

      val response = await(request(nino1).get())

      response.status                                  shouldBe 200
      (response.json \ "mainApplicantNino").as[String] shouldBe "true"
    }

    "retrieve claimant details for partner" in {
      grantAccess(nino2.value)
      claimantDetailsAreFoundFor(nino2, nino1, nino2, tcrAuthenticationToken)

      val response = await(request(nino2).get())

      response.status                                  shouldBe 200
      (response.json \ "mainApplicantNino").as[String] shouldBe "false"
    }

    "return 404 when claimant details are not found" in {
      grantAccess(nino1.value)
      claimantDetailsAreNotFoundFor(nino1)

      val response = await(request(nino1).get())

      response.status shouldBe 404
    }

    "return 400 when journeyId not supplied" in {
      grantAccess(nino1.value)
      claimantDetailsAreFoundFor(nino1, nino1, nino2, tcrAuthenticationToken)

      val response = await(
        wsUrl(s"/income/${nino1.value}/tax-credits/claimant-details")
          .addHttpHeaders(acceptJsonHeader, tcrAuthTokenHeader)
          .get()
      )

      response.status shouldBe 400
    }

    "return 400 when invalid journeyId supplied" in {
      grantAccess(nino1.value)
      claimantDetailsAreFoundFor(nino1, nino1, nino2, tcrAuthenticationToken)

      val response = await(
        wsUrl(s"/income/${nino1.value}/tax-credits/claimant-details?journeyId=ThisIsAnInvalidJourneyId")
          .addHttpHeaders(acceptJsonHeader, tcrAuthTokenHeader)
          .get()
      )

      response.status shouldBe 400
    }

    "return SHUTTERED when shuttered" in {
      stubForShutteringEnabled
      grantAccess(nino1.value)

      val response = await(request(nino1).get())
      response.status shouldBe 521
      val shuttering: Shuttering = Json.parse(response.body).as[Shuttering]
      shuttering.shuttered shouldBe true
      shuttering.title     shouldBe Some("Shuttered")
      shuttering.message   shouldBe Some("Tax Credits Renewal is currently not available")
    }
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
  "GET /renewals/:nino" should {
    "return open state " in {
      grantAccess(nino1.value)
      claimantClaimsAreFound(nino1, barcodeReference)
      authenticationRenewalSuccessful(nino1, barcodeReference, tcrAuthenticationToken)
      claimantDetailsAreFoundFor(nino1, nino1, nino2, tcrAuthenticationToken)

      val response = await(renewalsRequest.get)
      response.status                                 shouldBe 200
      (response.json \ "submissionsState").as[String] shouldBe "open"

      val expectedJson = parse(findResource("/resources/claimantdetails/renewals-response-open.json").get)
      response.json shouldBe expectedJson

      val claims  = (response.json \ "claims").as[JsArray]
      val renewal = (claims(0) \ "renewal" \ "claimantDetails").as[JsObject]
      renewal.value("renewalFormType").as[String] shouldBe "D"
    }

    "return SHUTTERED when shuttered" in {
      stubForShutteringEnabled
      grantAccess(nino1.value)

      val response = await(renewalsRequest.get)
      response.status shouldBe 521
      val shuttering: Shuttering = Json.parse(response.body).as[Shuttering]
      shuttering.shuttered shouldBe true
      shuttering.title     shouldBe Some("Shuttered")
      shuttering.message   shouldBe Some("Tax Credits Renewal is currently not available")
    }
  }

  "POST /income/:nino/tax-credits/renewal" should {
    "allow renewal" in {
      renewalIsSuccessful(nino1, renewal)
      submitTaxCreditRenewal
      verify(1, postRequestedFor(urlEqualTo(s"/tcs/${nino1.value}/renewal")))
    }

    "return SHUTTERED when shuttered" in {
      stubForShutteringEnabled
      grantAccess(nino1.value)

      val response = submitTaxCreditRenewal
      response.status shouldBe 521
      val shuttering: Shuttering = Json.parse(response.body).as[Shuttering]
      shuttering.shuttered shouldBe true
      shuttering.title     shouldBe Some("Shuttered")
      shuttering.message   shouldBe Some("Tax Credits Renewal is currently not available")
    }
  }

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

  "GET /renewals/:nino" should {
    "return closed state " in {
      grantAccess(nino1.value)

      val response = await(renewalsRequest.get)
      response.status                                 shouldBe 200
      (response.json \ "submissionsState").as[String] shouldBe "closed"
    }
    "return 400 if journeyId not supplied " in {
      grantAccess(nino1.value)

      val response = await(wsUrl(s"/renewals/${nino1.value}").addHttpHeaders(acceptJsonHeader).get)
      response.status shouldBe 400
    }
    "return SHUTTERED when shuttered" in {
      stubForShutteringEnabled
      grantAccess(nino1.value)

      val response = await(renewalsRequest.get)
      response.status shouldBe 521
      val shuttering: Shuttering = Json.parse(response.body).as[Shuttering]
      shuttering.shuttered shouldBe true
      shuttering.title     shouldBe Some("Shuttered")
      shuttering.message   shouldBe Some("Tax Credits Renewal is currently not available")
    }
  }

  "POST /income/:nino/tax-credits/renewal" should {
    "allow renewal so that anyone who has started a renewal can complete their journey even if the renewals period has just closed" in {
      renewalIsSuccessful(nino1, renewal)
      submitTaxCreditRenewal
      verify(1, postRequestedFor(urlEqualTo(s"/tcs/${nino1.value}/renewal")))
    }
    "return SHUTTERED when shuttered" in {
      stubForShutteringEnabled
      grantAccess(nino1.value)

      val response = submitTaxCreditRenewal
      response.status shouldBe 521
      val shuttering: Shuttering = Json.parse(response.body).as[Shuttering]
      shuttering.shuttered shouldBe true
      shuttering.title     shouldBe Some("Shuttered")
      shuttering.message   shouldBe Some("Tax Credits Renewal is currently not available")
    }
  }

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

  "GET /renewals/:nino" should {
    "return check_status_only state " in {
      grantAccess(nino1.value)
      claimantClaimsAreFound(nino1, barcodeReference)
      authenticationRenewalSuccessful(nino1, barcodeReference, tcrAuthenticationToken)
      claimantDetailsAreFoundFor(nino1, nino1, nino2, tcrAuthenticationToken)

      val response = await(renewalsRequest.get)
      response.status                                 shouldBe 200
      (response.json \ "submissionsState").as[String] shouldBe "check_status_only"

      val expectedJson = parse(findResource("/resources/claimantdetails/renewals-response-check-status-only.json").get)
      response.json shouldBe expectedJson

      val claims  = (response.json \ "claims").as[JsArray]
      val renewal = (claims(0) \ "renewal" \ "claimantDetails").as[JsObject]
      renewal.value("renewalFormType").as[String] shouldBe "D"
    }
    "return SHUTTERED when shuttered" in {
      stubForShutteringEnabled
      grantAccess(nino1.value)

      val response = await(renewalsRequest.get)
      response.status shouldBe 521
      val shuttering: Shuttering = Json.parse(response.body).as[Shuttering]
      shuttering.shuttered shouldBe true
      shuttering.title     shouldBe Some("Shuttered")
      shuttering.message   shouldBe Some("Tax Credits Renewal is currently not available")
    }
  }

  "POST /income/:nino/tax-credits/renewal" should {
    "allow renewal so that anyone who has started a renewal can complete their journey even if the renewals period has just closed" in {
      renewalIsSuccessful(nino1, renewal)
      submitTaxCreditRenewal
      verify(1, postRequestedFor(urlEqualTo(s"/tcs/${nino1.value}/renewal")))
    }
    "return SHUTTERED when shuttered" in {
      stubForShutteringEnabled
      grantAccess(nino1.value)

      val response = submitTaxCreditRenewal
      response.status shouldBe 521
      val shuttering: Shuttering = Json.parse(response.body).as[Shuttering]
      shuttering.shuttered shouldBe true
      shuttering.title     shouldBe Some("Shuttered")
      shuttering.message   shouldBe Some("Tax Credits Renewal is currently not available")
    }
  }

  "GET /income/tax-credits/submission/state/enabled" should {
    "return check-only state so that no new renewals can be started" in {
      val response = await(submissionStateEnabledRequest.get)
      response.status                                 shouldBe 200
      (response.json \ "submissionsState").as[String] shouldBe "check_status_only"
    }
  }
}
