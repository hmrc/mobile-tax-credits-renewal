package uk.gov.hmrc.mobiletaxcreditsrenewal

import play.api.libs.json.Json.toJson
import play.api.libs.ws.WSRequest
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.{IncomeDetails, RenewalData, TcrRenewal}
import uk.gov.hmrc.mobiletaxcreditsrenewal.support.BaseISpec

class SandboxTaxCreditRenewalSpec extends BaseISpec {
  val mobileHeader = "X-MOBILE-USER-ID" -> "208606423740"
  val nino = Nino("CS700100A")
  val incomeDetails = IncomeDetails(Some(10), Some(20), Some(30), Some(40), Some(true))
  val renewal = TcrRenewal(RenewalData(Some(incomeDetails), None, None), None, None, None, hasChangeOfCircs = false)
  val renewalJson = toJson(renewal)

  "GET /sandbox/renewals/:nino " should {
    def request(nino: Nino): WSRequest = wsUrl(s"/renewals/${nino.value}?journeyId=journeyId").addHttpHeaders(acceptJsonHeader, mobileHeader)

    "return successfully" in {
      await(request(nino).get()).status shouldBe 200
    }
    "return 400 if journeyId not supplied" in {
      await(wsUrl(s"/renewals/${nino.value}").addHttpHeaders(acceptJsonHeader, mobileHeader).get()).status shouldBe 400
    }
  }

  "POST /sandbox/declarations/:nino" should {
    def request(nino: Nino): WSRequest = wsUrl(s"/declarations/${nino.value}?journeyId=journeyId").addHttpHeaders(acceptJsonHeader, tcrAuthTokenHeader, mobileHeader)

    "renew successfully" in {
      await(request(nino1).post(renewalJson)).status shouldBe 200
    }
    "return 400 if journeyId not supplied" in {
      await(wsUrl(s"/declarations/${nino1}").addHttpHeaders(acceptJsonHeader, tcrAuthTokenHeader, mobileHeader).post(renewalJson)).status shouldBe 400
    }
  }
}
