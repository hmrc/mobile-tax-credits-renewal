/*
 * Copyright 2023 HM Revenue & Customs
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

import eu.timepit.refined.auto._
import org.apache.commons.codec.binary.Base64.encodeBase64
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.Json.toJson
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.types.ModelTypes.JourneyId
import uk.gov.hmrc.mobiletaxcreditsrenewal.services.MobileTaxCreditsRenewalService
import uk.gov.hmrc.mobiletaxcreditsrenewal.stubs.{AuthorisationStub, MobileTaxCreditsRenewalServiceStub}

import scala.concurrent.ExecutionContext.Implicits.global

class SandboxMobileTaxCreditsRenewalControllerSpec
    extends AnyWordSpecLike
    with Matchers
    with MockFactory
    with AuthorisationStub
    with MobileTaxCreditsRenewalServiceStub
    with ClaimsJson {
  implicit val authConnector:     AuthConnector                  = mock[AuthConnector]
  implicit val mockControlConfig: TaxCreditsControl              = mock[TaxCreditsControl]
  implicit val service:           MobileTaxCreditsRenewalService = mock[MobileTaxCreditsRenewalService]

  private val nino          = Nino("CS700100A")
  private val journeyId: JourneyId = "87144372-6bda-4cc9-87db-1d52fd96498f"

  private val controller =
    new SandboxMobileTaxCreditsRenewalController(stubControllerComponents())

  private val acceptHeader: (String, String) = "Accept" -> "application/vnd.hmrc.1.0+json"

  val renewalReference = RenewalReference("200000000000013")

  lazy val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    .withSession(
      "AuthToken" -> "Some Header"
    )
    .withHeaders(
      acceptHeader,
      "Authorization" -> "Some Header"
    )

  lazy val requestInvalidHeaders: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    .withSession(
      "AuthToken" -> "Some Header"
    )
    .withHeaders(
      "Authorization" -> "Some Header"
    )

  def basicAuthString(encodedAuth: String): String = "Basic " + encodedAuth

  def encodedAuth(
    nino:                Nino,
    tcrRenewalReference: RenewalReference
  ): String =
    new String(encodeBase64(s"${nino.value}:${tcrRenewalReference.value}".getBytes))

  def emptyRequestWithAcceptHeaderAndAuthHeader(
    renewalsRef: RenewalReference,
    nino:        Nino
  ): FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest().withHeaders(acceptHeader, HeaderKeys.tcrAuthToken -> basicAuthString(encodedAuth(nino, renewalsRef)))

  "fullClaimantDetails" should {

    "return details with the renewalFormType set" in {
      val expectedClaimDetails = Claim(
        Household(renewalReference.value,
                  "198765432134567",
                  Applicant(nino.nino, "MR", "JOHN", Some(""), "DENSMORE", Some(19500.00)),
                  None,
                  None,
                  Some("")),
        Renewal(Some("12/10/2030"),
                      Some("12/10/2010"),
                      Some("NOT_SUBMITTED"),
                      Some("12/10/2030"),
                      Some("12/10/2010"),
                      Some("D"))
      )

      val result = controller.fullClaimantDetails(nino, journeyId)(fakeRequest)
      status(result)        shouldBe 200
      contentAsJson(result) shouldBe toJson(Claims(Some(Seq(expectedClaimDetails))))
    }

    "return unauthorised when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .fullClaimantDetails(nino, journeyId)
          .apply(
            emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, nino)
              .withHeaders("SANDBOX-CONTROL" -> "ERROR-401")
          )
      ) shouldBe 401
    }

    "return forbidden when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .fullClaimantDetails(nino, journeyId)
          .apply(
            emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, nino)
              .withHeaders("SANDBOX-CONTROL" -> "ERROR-403")
          )
      ) shouldBe 403
    }

    "return not found when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .fullClaimantDetails(nino, journeyId)
          .apply(
            emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, nino)
              .withHeaders("SANDBOX-CONTROL" -> "ERROR-404")
          )
      ) shouldBe 404
    }

    "return internal sever error when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .fullClaimantDetails(nino, journeyId)
          .apply(
            emptyRequestWithAcceptHeaderAndAuthHeader(renewalReference, nino)
              .withHeaders("SANDBOX-CONTROL" -> "ERROR-500")
          )
      ) shouldBe 500
    }

    "return shuttered when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .fullClaimantDetails(nino, journeyId)
          .apply(fakeRequest.withHeaders("SANDBOX-CONTROL" -> "SHUTTERED"))
      ) shouldBe 521
    }

  }

  "taxCreditsSubmissionStateEnabled" should {
    "return an open submission state when directed to do so using the SANDBOX-CONTROL header" in {
      val result = controller.taxCreditsSubmissionStateEnabled(journeyId).apply(fakeRequest)
      status(result)        shouldBe 200
      contentAsJson(result) shouldBe Json.parse("""{"submissionsState":"open"}""")
    }

    "return an closed submission state when directed to do so using the SANDBOX-CONTROL header" in {
      val result = controller
        .taxCreditsSubmissionStateEnabled(journeyId)
        .apply(fakeRequest.withHeaders("SANDBOX-CONTROL" -> "CLOSED"))
      status(result)        shouldBe 200
      contentAsJson(result) shouldBe Json.parse("""{"submissionsState":"closed"}""")
    }

    "return an check_status_only submission state when directed to do so using the SANDBOX-CONTROL header" in {
      val result = controller
        .taxCreditsSubmissionStateEnabled(journeyId)
        .apply(fakeRequest.withHeaders("SANDBOX-CONTROL" -> "CHECK-STATUS-ONLY"))
      status(result)        shouldBe 200
      contentAsJson(result) shouldBe Json.parse("""{"submissionsState":"check_status_only"}""")
    }

    "return unauthorised when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .taxCreditsSubmissionStateEnabled(journeyId)
          .apply(fakeRequest.withHeaders("SANDBOX-CONTROL" -> "ERROR-401"))
      ) shouldBe 401
    }

    "return forbidden when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .taxCreditsSubmissionStateEnabled(journeyId)
          .apply(fakeRequest.withHeaders("SANDBOX-CONTROL" -> "ERROR-403"))
      ) shouldBe 403
    }

    "return not found when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .taxCreditsSubmissionStateEnabled(journeyId)
          .apply(fakeRequest.withHeaders("SANDBOX-CONTROL" -> "ERROR-404"))
      ) shouldBe 404
    }

    "return internal sever error when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .taxCreditsSubmissionStateEnabled(journeyId)
          .apply(fakeRequest.withHeaders("SANDBOX-CONTROL" -> "ERROR-500"))
      ) shouldBe 500
    }

    "return shuttered when directed to do so using the SANDBOX-CONTROL header" in {
      status(
        controller
          .taxCreditsSubmissionStateEnabled(journeyId)
          .apply(fakeRequest.withHeaders("SANDBOX-CONTROL" -> "SHUTTERED"))
      ) shouldBe 521
    }
  }

}
