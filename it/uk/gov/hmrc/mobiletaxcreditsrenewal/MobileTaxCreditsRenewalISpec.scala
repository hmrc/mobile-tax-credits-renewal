/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.mobiletaxcreditsrenewal

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.libs.json.Json.parse
import play.api.libs.json.{JsArray, JsObject}
import uk.gov.hmrc.api.sandbox.FileResource
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.RenewalReference
import uk.gov.hmrc.mobiletaxcreditsrenewal.stubs.AuthStub.grantAccess
import uk.gov.hmrc.mobiletaxcreditsrenewal.stubs.NtcStub._
import uk.gov.hmrc.mobiletaxcreditsrenewal.support.BaseISpec

class MobileTaxCreditsRenewalISpec extends BaseISpec with FileResource {
  "GET /tokens/:nino/:renewalReference" should {
    val url = wsUrl(s"/tokens/${nino1.value}/${renewalReference.value}").withHeaders(acceptJsonHeader)

    "return a tcrAuthenticationToken" in {

      grantAccess(nino1.value)
      authenticationRenewalSuccessful(nino1,renewalReference,tcrAuthenticationToken)

      val response = await(url.get())

      response.status shouldBe 200
      (response.json \ "tcrAuthToken").as[String] shouldBe tcrAuthenticationToken
    }

    "return 404 when no auth tcrAuthenticationToken is found " in {
      grantAccess(nino1.value)
      authenticationRenewalNotFound(nino1,renewalReference)

      val response = await(url.get())

      response.status shouldBe 404
    }
  }

  "GET /claims/:nino" should {
    val mainApplicantNino = Nino("CS700100A")
    val barcodeReference = RenewalReference("200000000000013")
    val request = wsUrl(s"/claims/${mainApplicantNino.value}").withHeaders(acceptJsonHeader, tcrAuthTokenHeader)

    "retrieve claimant claims for main applicant and set renewalFormType for a renewal where bar code ref is not '000000000000000'" in {
      grantAccess(mainApplicantNino.value)
      claimantClaimsAreFound(mainApplicantNino,barcodeReference)
      authenticationRenewalSuccessful(mainApplicantNino,barcodeReference,tcrAuthenticationToken)
      claimantDetailsAreFoundFor(mainApplicantNino, mainApplicantNino, nino2, tcrAuthenticationToken)

      val response = await(request.get())
      response.status shouldBe 200

      val expectedJson = parse(findResource("/resources/claimantdetails/tax-credit-claimant-details-response.json").get)
      response.json shouldBe expectedJson

      val references = (response.json \ "references" ).as[JsArray]
      val renewal = (references(0) \ "renewal" ).as[JsObject]
      renewal.value("renewalFormType").as[String] shouldBe "D"
    }

    "retrieve claimant claims for main applicant and not set renewalFormType if no token is found" in {
      grantAccess(mainApplicantNino.value)
      claimantClaimsAreFound(mainApplicantNino,barcodeReference)
      authenticationRenewalNotFound(mainApplicantNino,barcodeReference)

      val response = await(request.get())
      response.status shouldBe 200

      val references = (response.json \ "references" ).as[JsArray]
      val renewal = (references(0) \ "renewal" ).as[JsObject]
      renewal.value.get("renewalFormType") shouldBe None

      verify(0, getRequestedFor(urlEqualTo(s"/tcs/${mainApplicantNino.value}/claimant-details")))
    }

    "retrieve claimant claims for main applicant and not set renewalFormType if no claimant details found" in {
      grantAccess(mainApplicantNino.value)
      claimantClaimsAreFound(mainApplicantNino,barcodeReference)
      authenticationRenewalSuccessful(mainApplicantNino,barcodeReference,tcrAuthenticationToken)
      claimantDetailsAreNotFoundFor(mainApplicantNino)

      val response = await(request.get())
      response.status shouldBe 200

      val references = (response.json \ "references" ).as[JsArray]
      val renewal = (references(0) \ "renewal" ).as[JsObject]
      renewal.value.get("renewalFormType") shouldBe None
    }
  }

  "GET /claimants/:nino" should {
    def request(nino:Nino) = wsUrl(s"/claimants/${nino.value}").withHeaders(acceptJsonHeader, tcrAuthTokenHeader)

    "retrieve claimant details for main applicant" in {
      grantAccess(nino1.value)
      claimantDetailsAreFoundFor(nino1, nino1, nino2, tcrAuthenticationToken)

      val response = await(request(nino1).get())

      response.status shouldBe 200
      (response.json \ "mainApplicantNino").as[String] shouldBe "true"
    }

    "retrieve claimant details for partner" in {
      grantAccess(nino2.value)
      claimantDetailsAreFoundFor(nino2, nino1, nino2, tcrAuthenticationToken)

      val response = await(request(nino2).get())

      response.status shouldBe 200
      (response.json \ "mainApplicantNino").as[String] shouldBe "false"
    }

    "return 404 when claimant details are not found" in {
      grantAccess(nino1.value)
      claimantDetailsAreNotFoundFor(nino1)

      val response = await(request(nino1).get())

      response.status shouldBe 404
    }
  }
}
