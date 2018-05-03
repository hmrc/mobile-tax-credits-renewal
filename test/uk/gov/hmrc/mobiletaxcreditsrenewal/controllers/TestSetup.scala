/*
 * Copyright 2018 HM Revenue & Customs
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

import com.ning.http.util.Base64
import org.scalatest.mockito.MockitoSugar
import org.slf4j
import play.api.libs.json.JsValue
import play.api.libs.json.Json.parse
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.{Configuration, Logger, LoggerLike}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobiletaxcreditsrenewal.config.AppContext
import uk.gov.hmrc.mobiletaxcreditsrenewal.connectors.NtcConnector
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._
import uk.gov.hmrc.mobiletaxcreditsrenewal.services.LivePersonalIncomeService
import uk.gov.hmrc.mobiletaxcreditsrenewal.stubs.{AuthorisationStub, NtcConnectorStub, PersonalIncomeServiceStub}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

class TestLoggerLike extends LoggerLike {
  override val logger: slf4j.Logger = Logger("test").logger

  var infoMessages: Seq[String] = Seq()
  var warnMessages: Seq[String] = Seq()

  override def info(message: => String): scala.Unit = {
    infoMessages = infoMessages ++ Seq(message)
  }

  override def warn(message: => String): scala.Unit = {
    warnMessages = warnMessages ++ Seq(message)
  }

  def warnMessageWaslogged(message: String): Boolean = {
    warnMessages.contains(message)
  }

  def infoMessageWasLogged(message: String): Boolean = {
    infoMessages.contains(message)
  }

  def clearMessages(): Unit = {
    infoMessages = Seq()
    warnMessages = Seq()
  }
}


trait TestSetup extends MockitoSugar with UnitSpec with WithFakeApplication with PersonalIncomeServiceStub
  with AuthorisationStub with NtcConnectorStub {

  trait mocks {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val mockAuthConnector: AuthConnector = mock[AuthConnector]
    implicit val mockNtcConnector: NtcConnector = mock[NtcConnector]
    implicit val mockAuditConnector: AuditConnector = mock[AuditConnector]
    implicit val mockLivePersonalIncomeService: LivePersonalIncomeService = mock[LivePersonalIncomeService]
    implicit val mockTaxCreditsSubmissionControlConfig: TaxCreditsSubmissionControlConfig = mock[TaxCreditsSubmissionControlConfig]
    implicit val mockConfiguration: Configuration = fakeApplication.injector.instanceOf[Configuration]
    implicit val mockAppContext: AppContext = mock[AppContext]

    implicit val logger: TestLoggerLike = new TestLoggerLike()
  }

  val noNinoFoundOnAccount: JsValue = parse("""{"code":"UNAUTHORIZED","message":"NINO does not exist on account"}""")
  val lowConfidenceLevelError: JsValue =
    parse("""{"code":"LOW_CONFIDENCE_LEVEL","message":"Confidence Level on account does not allow access"}""")

  val nino = "CS700100A"
  val incorrectNino = Nino("SC100700A")
  val renewalReference = RenewalReference("111111111111111")
  val tcrAuthToken = TcrAuthenticationToken("some-auth-token")
  val acceptHeader: (String, String) = "Accept" -> "application/vnd.hmrc.1.0+json"

  lazy val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(
    "AuthToken" -> "Some Header"
  ).withHeaders(
    acceptHeader,
    "Authorization" -> "Some Header"
  )
  lazy val requestInvalidHeaders: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withSession(
    "AuthToken" -> "Some Header"
  ).withHeaders(
    "Authorization" -> "Some Header"
  )

  def basicAuthString(encodedAuth: String): String = "Basic " + encodedAuth

  def encodedAuth(nino: Nino, tcrRenewalReference: RenewalReference): String =
    new String(Base64.encode(s"${nino.value}:${tcrRenewalReference.value}".getBytes))

  def emptyRequestWithAcceptHeaderAndAuthHeader(renewalsRef: RenewalReference, nino: Nino) =
    FakeRequest().withHeaders(acceptHeader, HeaderKeys.tcrAuthToken -> basicAuthString(encodedAuth(nino, renewalsRef)))

  def buildClaims(claims: Claims): Claims = {
    val applicantNotFound: Option[Applicant] = None
    val updated = claims.references.get.map { item =>
      val applicant1 = item.household.applicant1
      val newApp = applicant1.copy(nino = "AM242413B")
      val secondApp: Option[Applicant] =
        item.household.applicant2.fold(applicantNotFound) { found => Some(found.copy(nino = "AM242413B")) }
      val newHousehold = item.household.copy(applicant1 = newApp, applicant2 = secondApp)
      item.copy(household = newHousehold, renewal = item.renewal)
    }
    Claims(Some(updated))
  }
}