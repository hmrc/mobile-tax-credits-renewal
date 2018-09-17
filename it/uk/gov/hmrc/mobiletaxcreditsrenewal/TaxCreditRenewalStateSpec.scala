package uk.gov.hmrc.mobiletaxcreditsrenewal

import com.github.tomakehurst.wiremock.client.WireMock.{postRequestedFor, urlEqualTo, verify}
import org.joda.time.DateTime
import org.joda.time.DateTimeZone.UTC
import play.api.libs.json.Json.{parse, toJson}
import play.api.libs.json.{JsArray, JsObject}
import play.api.libs.ws.{WSRequest, WSResponse}
import uk.gov.hmrc.api.sandbox.FileResource
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.{IncomeDetails, RenewalData, RenewalReference, TcrRenewal}
import uk.gov.hmrc.mobiletaxcreditsrenewal.stubs.AuthStub.grantAccess
import uk.gov.hmrc.mobiletaxcreditsrenewal.stubs.NtcStub._
import uk.gov.hmrc.mobiletaxcreditsrenewal.support.BaseISpec
import uk.gov.hmrc.time.DateTimeUtils

class TaxCreditRenewalStateSpec extends BaseISpec with FileResource{
  protected val incomeDetails = IncomeDetails(Some(10), Some(20), Some(30), Some(40), Some(true))
  protected val renewal = TcrRenewal(RenewalData(Some(incomeDetails), None, None), None, None, None, hasChangeOfCircs = false)

  protected val now: DateTime = DateTimeUtils.now.withZone(UTC)
  val barcodeReference = RenewalReference("200000000000013")

  protected val renewalsRequest: WSRequest = wsUrl(s"/renewals/${nino1.value}").withHeaders(acceptJsonHeader)

  protected def submissionStartDate: String = now.minusDays(1).toString
  protected def submissionEndDate: String = now.plusDays(1).toString
  protected def endViewRenewalsDate: String = now.plusDays(2).toString

  override def config: Map[String, Any] = {
     super.config ++
     Map(
       "microservice.services.ntc.submission.startDate" -> submissionStartDate,
       "microservice.services.ntc.submission.endDate" -> submissionEndDate,
       "microservice.services.ntc.submission.endViewRenewalsDate" -> endViewRenewalsDate)
   }

  def submitTaxCreditRenewal: WSResponse = {
    def request(nino: Nino) = wsUrl(s"/declarations/${nino.value}").withHeaders(acceptJsonHeader, tcrAuthTokenHeader)

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
  }
}


class TaxCreditRenewalOpenStateSpec extends TaxCreditRenewalStateSpec{
  "GET /renewals/:nino" should {
    "return open state " in {
      grantAccess(nino1.value)
      claimantClaimsAreFound(nino1,barcodeReference)
      authenticationRenewalSuccessful(nino1,barcodeReference,tcrAuthenticationToken)
      claimantDetailsAreFoundFor(nino1, nino1, nino2, tcrAuthenticationToken)

      val response = await(renewalsRequest.get)
      response.status shouldBe 200
      (response.json \ "submissionsState").as[String] shouldBe "open"

      val expectedJson = parse(findResource("/resources/claimantdetails/renewals-response-open.json").get)
      response.json shouldBe expectedJson

      val claims = (response.json \ "claims").as[JsArray]
      val renewal = (claims(0) \ "renewal" \ "claimantDetails").as[JsObject]
      renewal.value("renewalFormType").as[String] shouldBe "D"
    }
  }
}

class TaxCreditRenewalClosedStateSpec extends TaxCreditRenewalStateSpec{
  override def submissionStartDate: String = now.plusDays(1).toString
  override def submissionEndDate: String = now.plusDays(2).toString
  override def endViewRenewalsDate: String = now.plusDays(3).toString

  "GET /renewals/:nino" should {
    "return closed state " in {
      grantAccess(nino1.value)

      val response = await(renewalsRequest.get)
      response.status shouldBe 200
      (response.json \ "submissionsState").as[String] shouldBe "closed"
    }
  }
}

class TaxCreditRenewalCheckStatusOnlyPeriodStateSpec extends TaxCreditRenewalStateSpec{
  override def submissionStartDate: String = now.minusDays(2).toString
  override def submissionEndDate: String = now.minusDays(1).toString
  override def endViewRenewalsDate: String = now.plusDays(1).toString

  "GET /renewals/:nino" should {
    "return check_status_only state " in {
      grantAccess(nino1.value)
      claimantClaimsAreFound(nino1,barcodeReference)
      authenticationRenewalSuccessful(nino1,barcodeReference,tcrAuthenticationToken)
      claimantDetailsAreFoundFor(nino1, nino1, nino2, tcrAuthenticationToken)

      val response = await(renewalsRequest.get)
      response.status shouldBe 200
      (response.json \ "submissionsState").as[String] shouldBe "check_status_only"

      val expectedJson = parse(findResource("/resources/claimantdetails/renewals-response-check-status-only.json").get)
      response.json shouldBe expectedJson

      val claims = (response.json \ "claims").as[JsArray]
      val renewal = (claims(0) \ "renewal" \ "claimantDetails").as[JsObject]
      renewal.value("renewalFormType").as[String] shouldBe "D"
    }
  }
}
