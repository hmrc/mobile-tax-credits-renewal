package uk.gov.hmrc.mobiletaxcreditsrenewal.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.RenewalReference

object NtcStub {
  val applicationId = "198765432134566"

  def claimantClaimsAreFound(
    nino:             Nino,
    barcodeReference: RenewalReference
  ): Unit =
    stubFor(
      get(urlPathEqualTo(s"/tcs/${nino.value}/claimant-claims")).willReturn(
        aResponse()
          .withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody(
            s"""{
          "references": [
            {
              "household": {
                "barcodeReference": "${barcodeReference.value}",
                "applicationID": "198765432134567",
                "applicant1": {
                  "nino": "${nino.value}",
                  "title": "MR",
                  "firstForename": "JOHN",
                  "secondForename": "",
                  "surname": "DENSMORE"
                },
                "householdEndReason": ""
              },
              "renewal": {
                "awardStartDate": "2030-10-12",
                "awardEndDate": "2010-10-12",
                "renewalNoticeIssuedDate": "2030-10-12",
                "renewalNoticeFirstSpecifiedDate": "2010-10-12"
              }
            },
            {
              "household": {
                "barcodeReference": "000000000000000",
                "applicationID": "198765432134567",
                "applicant1": {
                  "nino": "${nino.value}",
                  "title": "MR",
                  "firstForename": "JOHN",
                  "secondForename": "",
                  "surname": "DENSMORE"
                },
                "householdEndReason": ""
              },
              "renewal": {
                "awardStartDate": "2030-10-12",
                "awardEndDate": "2010-10-12",
                "renewalNoticeIssuedDate": "2030-10-12",
                "renewalNoticeFirstSpecifiedDate": "2010-10-12"
              }
            }
          ]
        }""".stripMargin
          )
      )
    )

  def claimantClaimsAreFoundWithPartner(
    nino:             Nino,
    applicant2Nino:   Nino,
    barcodeReference: RenewalReference
  ): Unit =
    stubFor(
      get(urlPathEqualTo(s"/tcs/${nino.value}/claimant-claims")).willReturn(
        aResponse()
          .withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody(
            s"""{
          "references": [
            {
              "household": {
                "barcodeReference": "${barcodeReference.value}",
                "applicationID": "198765432134567",
                "applicant1": {
                  "nino": "${nino.value}",
                  "title": "MR",
                  "firstForename": "JOHN",
                  "secondForename": "",
                  "surname": "DENSMORE"
                },
                "applicant2": {
                  "nino": "${applicant2Nino.value}",
                  "title": "MRS",
                  "firstForename": "JILL",
                  "secondForename": "",
                  "surname": "DENSMORE"
                },
                "householdEndReason": ""
              },
              "renewal": {
                "awardStartDate": "2030-10-12",
                "awardEndDate": "2010-10-12",
                "renewalNoticeIssuedDate": "2030-10-12",
                "renewalNoticeFirstSpecifiedDate": "2010-10-12"
              }
            },
            {
              "household": {
                "barcodeReference": "000000000000000",
                "applicationID": "198765432134567",
                "applicant1": {
                  "nino": "${nino.value}",
                  "title": "MR",
                  "firstForename": "JOHN",
                  "secondForename": "",
                  "surname": "DENSMORE"
                },
                "applicant2": {
                  "nino": "${applicant2Nino.value}",
                  "title": "MRS",
                  "firstForename": "JILL",
                  "secondForename": "",
                  "surname": "DENSMORE"
                },
                "householdEndReason": ""
              },
              "renewal": {
                "awardStartDate": "2030-10-12",
                "awardEndDate": "2010-10-12",
                "renewalNoticeIssuedDate": "2030-10-12",
                "renewalNoticeFirstSpecifiedDate": "2010-10-12"
              }
            }
          ]
        }""".stripMargin
          )
      )
    )

  def authenticationRenewalNotFound(
    nino:             Nino,
    barcodeReference: RenewalReference
  ): Unit =
    stubFor(
      get(urlPathEqualTo(s"/tcs/${nino.value}/${barcodeReference.value}/auth")).willReturn(aResponse().withStatus(404))
    )

  def authenticationRenewalSuccessful(
    nino:             Nino,
    barcodeReference: RenewalReference,
    token:            String
  ): Unit =
    stubFor(
      get(urlPathEqualTo(s"/tcs/${nino.value}/${barcodeReference.value}/auth")).willReturn(
        aResponse()
          .withStatus(200)
          .withHeader("Content-Type", "application/json")
          .withBody(s"""{ "tcrAuthToken" : "$token" }""")
      )
    )

  def claimantDetailsAreFoundFor(
    currentUserNino:    Nino,
    mainApplicant1Nino: Nino,
    applicant2Nino:     Nino,
    token:              String
  ): Unit =
    stubFor(
      get(urlEqualTo(s"/tcs/${currentUserNino.value}/claimant-details"))
        .withHeader("tcrAuthToken", equalTo(token))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(
              s"""{
                 | "hasPartner": true,
                 | "claimantNumber" : 123,
                 | "renewalFormType": "D",
                 | "mainApplicantNino" : "${mainApplicant1Nino.value}",
                 | "usersPartnerNino" : "${applicant2Nino.value}",
                 | "availableForCOCAutomation" : false,
                 | "applicationId" : "$applicationId" }""".stripMargin
            )
        )
    )

  def claimantDetailsAreNotFoundFor(nino: Nino): Unit =
    stubFor(get(urlPathEqualTo(s"/tcs/${nino.value}/claimant-details")).willReturn(aResponse().withStatus(404)))

  def claimantDetailsTooManyRequests(nino: Nino): Unit =
    stubFor(get(urlPathEqualTo(s"/tcs/${nino.value}/claimant-details")).willReturn(aResponse().withStatus(429)))

}
