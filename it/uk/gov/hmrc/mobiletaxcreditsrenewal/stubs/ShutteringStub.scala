package uk.gov.hmrc.mobiletaxcreditsrenewal.stubs

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, stubFor, urlPathEqualTo}
import com.github.tomakehurst.wiremock.stubbing.StubMapping

object ShutteringStub {

  def stubForShutteringDisabled: StubMapping = {
    stubFor(
      get(urlPathEqualTo("/mobile-shuttering/service/mobile-tax-credits-renewal/shuttered-status"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""
                         |{
                         |  "shuttered": false,
                         |  "title":     "",
                         |  "message":    ""
                         |}
          """.stripMargin)))
  }

  def stubForShutteringEnabled: StubMapping = {
    stubFor(
      get(urlPathEqualTo("/mobile-shuttering/service/mobile-tax-credits-renewal/shuttered-status"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""
                         |{
                         |  "shuttered": true,
                         |  "title":     "Shuttered",
                         |  "message":   "Tax Credits Renewal is currently not available"
                         |}
          """.stripMargin)))
  }

}
