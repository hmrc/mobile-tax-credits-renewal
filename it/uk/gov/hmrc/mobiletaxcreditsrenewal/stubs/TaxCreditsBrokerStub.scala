package uk.gov.hmrc.mobiletaxcreditsrenewal.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.domain.Nino

object TaxCreditsBrokerStub {

  def employedEarningsRtiFound(
    currentUserNino: Nino,
    withPartner:     Boolean
  ): Unit =
    stubFor(
      get(urlEqualTo(s"/tcs/${currentUserNino.value}/employed-earnings-rti"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(
              if (withPartner) {
                s"""{
                   | "previousYearRTIEmployedEarnings": 25444.99,
                   | "previousYearRTIEmployedEarningsPartner": 20000.0
                   | }""".stripMargin
              } else {
                s"""{
                   | "previousYearRTIEmployedEarnings": 25444.99
                   | }""".stripMargin
              }
            )
        )
    )

  def employedEarningsRtiError(
    currentUserNino: Nino,
    status:          Int
  ): Unit =
    stubFor(
      get(urlEqualTo(s"/tcs/${currentUserNino.value}/employed-earnings-rti"))
        .willReturn(aResponse().withStatus(status).withHeader("Content-Type", "application/json"))
    )

}
