package uk.gov.hmrc.mobiletaxcreditsrenewal

import com.github.tomakehurst.wiremock.client.WireMock.{postRequestedFor, urlEqualTo, verify}
import org.joda.time.DateTime
import org.joda.time.DateTimeZone.UTC
import org.scalatest.Assertion
import play.api.libs.json.Json.toJson
import play.api.libs.ws.WSRequest
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.{IncomeDetails, RenewalData, TcrRenewal}
import uk.gov.hmrc.mobiletaxcreditsrenewal.stubs.AuthStub.grantAccess
import uk.gov.hmrc.mobiletaxcreditsrenewal.stubs.NtcStub.renewalIsSuccessful
import uk.gov.hmrc.mobiletaxcreditsrenewal.support.BaseISpec
import uk.gov.hmrc.time.DateTimeUtils

class TaxCreditRenewalStateSpec extends BaseISpec{
  protected val incomeDetails = IncomeDetails(Some(10), Some(20), Some(30), Some(40), Some(true))
  protected val renewal = TcrRenewal(RenewalData(Some(incomeDetails), None, None), None, None, None, hasChangeOfCircs = false)

  protected val now: DateTime = DateTimeUtils.now.withZone(UTC)

  protected val submissionStateEnabledRequest: WSRequest = wsUrl(s"/states/current").withHeaders(acceptJsonHeader)

  protected def submissionShuttered = false
  protected def submissionStartDate: String = now.minusDays(1).toString
  protected def submissionEndDate: String = now.plusDays(1).toString
  protected def endViewRenewalsDate: String = now.plusDays(2).toString

  override def config: Map[String, Any] = {
     super.config ++
     Map(
       "microservice.services.ntc.submission.submissionShuttered" -> submissionShuttered,
       "microservice.services.ntc.submission.startDate" -> submissionStartDate,
       "microservice.services.ntc.submission.endDate" -> submissionEndDate,
       "microservice.services.ntc.submission.endViewRenewalsDate" -> endViewRenewalsDate)
   }

  protected def submitTaxCreditRenewal: Assertion = {
    def request(nino: Nino) = wsUrl(s"/declarations/${nino.value}").withHeaders(acceptJsonHeader, tcrAuthTokenHeader)

    grantAccess(nino1.value)

    val renewalJson = toJson(renewal)
    val response = await(request(nino1).post(renewalJson))
    response.status shouldBe 200
  }

  protected def verifyNoSubmissionForPostToTaxCreditsRenewlEndpoint(): Unit = {
    submitTaxCreditRenewal
    verify(0, postRequestedFor(urlEqualTo(s"/tcs/${nino1.value}/renewal")))
  }
}


class TaxCreditRenewalOpenStateSpec extends TaxCreditRenewalStateSpec{
  "POST /declarations/:nino" should {
    "renew when submissions are enabled" in {
      renewalIsSuccessful(nino1, renewal)
      submitTaxCreditRenewal
      verify(1, postRequestedFor(urlEqualTo(s"/tcs/${nino1.value}/renewal")))
    }
  }

  "GET /states/current" should {
    "return open state " in {
      val response = await(submissionStateEnabledRequest.get)
      response.status shouldBe 200
      (response.json \ "submissionsState").as[String] shouldBe "open"
    }
  }
}


class TaxCreditRenewalClosedStateSpec extends TaxCreditRenewalStateSpec{
  override def submissionStartDate: String = now.plusDays(1).toString
  override def submissionEndDate: String = now.plusDays(2).toString
  override def endViewRenewalsDate: String = now.plusDays(3).toString

  "POST /declarations/:nino" should {
    "return OK but not renew when submissions are closed" in {
      verifyNoSubmissionForPostToTaxCreditsRenewlEndpoint()
    }
  }

  "GET /states/current" should {
    "return closed state " in {
      val response = await(submissionStateEnabledRequest.get)
      response.status shouldBe 200
      (response.json \ "submissionsState").as[String] shouldBe "closed"
    }
  }
}


class TaxCreditRenewalShutteredStateSpec extends TaxCreditRenewalStateSpec{
  override def submissionShuttered: Boolean = true

  "POST /declarations/:nino" should {
    "return OK but not renew when submissions are shuttered" in {
      verifyNoSubmissionForPostToTaxCreditsRenewlEndpoint()
    }
  }

  "GET /rstates/current" should {
    "return shuttered state " in {
      val response = await(submissionStateEnabledRequest.get)
      response.status shouldBe 200
      (response.json \ "submissionsState").as[String] shouldBe "shuttered"
    }
  }
}


class TaxCreditRenewalCheckStatusOnlyPeriodStateSpec extends TaxCreditRenewalStateSpec{
  override def submissionStartDate: String = now.minusDays(2).toString
  override def submissionEndDate: String = now.minusDays(1).toString
  override def endViewRenewalsDate: String = now.plusDays(1).toString

  "POST /declarations/:nino" should {
    "return OK but not renew when submissions are view-only" in {
      verifyNoSubmissionForPostToTaxCreditsRenewlEndpoint()
    }
  }

  "GET /states/current" should {
    "return check-only state " in {
      val response = await(submissionStateEnabledRequest.get)
      response.status shouldBe 200
      (response.json \ "submissionsState").as[String] shouldBe "check_status_only"
    }
  }
}
