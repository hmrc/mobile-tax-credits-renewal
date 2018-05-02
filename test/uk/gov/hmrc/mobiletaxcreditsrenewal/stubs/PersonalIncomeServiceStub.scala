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

package uk.gov.hmrc.mobiletaxcreditsrenewal.stubs

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mockito.stubbing.OngoingStubbing
import play.api.Configuration
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobiletaxcreditsrenewal.config.{AppContext, RenewalStatusTransform}
import uk.gov.hmrc.mobiletaxcreditsrenewal.connectors.NtcConnector
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.{ClaimantDetails, Claims, RenewalReference, TcrAuthenticationToken}
import uk.gov.hmrc.mobiletaxcreditsrenewal.services.{LivePersonalIncomeService, PersonalIncomeService}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

trait PersonalIncomeServiceStub extends UnitSpec {
  def stubAuthRenewalResponse(response: Option[TcrAuthenticationToken])(implicit personalIncomeService: PersonalIncomeService): OngoingStubbing[Future[Option[TcrAuthenticationToken]]] = {
    when(personalIncomeService.authenticateRenewal(any[Nino](), any[RenewalReference]())(any[HeaderCarrier](), any[ExecutionContext]()))
      .thenReturn(response)
  }

  def stubClaimantDetailsResponse(response: ClaimantDetails)(implicit personalIncomeService: PersonalIncomeService): OngoingStubbing[Future[ClaimantDetails]] = {
    when(personalIncomeService.claimantDetails(any[Nino]())(any[HeaderCarrier](), any[ExecutionContext]())).thenReturn(response)
  }

  def stubServiceClaimantClaims(claims: Claims)(implicit personalIncomeService: PersonalIncomeService): OngoingStubbing[Future[Claims]] = {
    when(personalIncomeService.claimantClaims(any[Nino]())(any[HeaderCarrier](), any[ExecutionContext]())).thenReturn(claims)
  }

  def stubServiceAuthenticateRenewal(implicit personalIncomeService: PersonalIncomeService): OngoingStubbing[Future[Option[TcrAuthenticationToken]]] = {
    when(personalIncomeService.authenticateRenewal(any[Nino](), any[RenewalReference]())(any[HeaderCarrier](), any[ExecutionContext]())).thenReturn(Some(new TcrAuthenticationToken("token")))
  }

  def stubClaimantClaimsResponse(response: Claims)(implicit personalIncomeService: PersonalIncomeService): OngoingStubbing[Future[Claims]] = {
    when(personalIncomeService.claimantClaims(any[Nino]())(any[HeaderCarrier](), any[ExecutionContext]())).thenReturn(response)
  }

  class TestPersonalIncomeService(val ntcConnector: NtcConnector,
                                  override val auditConnector: AuditConnector,
                                  configuration: Configuration,
                                  appContext: AppContext)
    extends LivePersonalIncomeService(ntcConnector, auditConnector, configuration, appContext) {
    override lazy val config: List[RenewalStatusTransform] = List.empty
  }

}