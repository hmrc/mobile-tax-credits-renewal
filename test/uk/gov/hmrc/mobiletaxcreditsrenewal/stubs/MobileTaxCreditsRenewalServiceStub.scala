/*
 * Copyright 2019 HM Revenue & Customs
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

import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._
import uk.gov.hmrc.mobiletaxcreditsrenewal.services.MobileTaxCreditsRenewalService

import scala.concurrent.{ExecutionContext, Future}

trait MobileTaxCreditsRenewalServiceStub extends MockFactory {
  def stubAuthRenewalResponse(response: Option[TcrAuthenticationToken],
                              nino: Nino,
                              renewalReference: RenewalReference)(implicit mobileTaxCreditsRenewalService: MobileTaxCreditsRenewalService): Unit = {
    (mobileTaxCreditsRenewalService.authenticateRenewal(_: Nino, _: RenewalReference)(_: HeaderCarrier, _: ExecutionContext)).
      expects(nino, renewalReference, *, *).returning(Future successful response)
  }

  def stubClaimantDetailsResponse(response: ClaimantDetails, nino: Nino)(implicit mobileTaxCreditsRenewalService: MobileTaxCreditsRenewalService): Unit = {
    (mobileTaxCreditsRenewalService.claimantDetails(_: Nino)(_: HeaderCarrier, _: ExecutionContext)).expects(nino, *, *).returning(Future successful response)
  }

  def stubServiceClaimantClaims(claims: LegacyClaims, nino: Nino)(implicit mobileTaxCreditsRenewalService: MobileTaxCreditsRenewalService): Unit = {
    (mobileTaxCreditsRenewalService.legacyClaimantClaims(_: Nino)(_: HeaderCarrier, _: ExecutionContext)).expects(nino, *, *).returning(Future successful claims)
  }

  def stubServiceAuthenticateRenewal(token: TcrAuthenticationToken,
                                     nino: Nino,
                                     renewalReference: RenewalReference)(implicit mobileTaxCreditsRenewalService: MobileTaxCreditsRenewalService): Unit = {
    (mobileTaxCreditsRenewalService.authenticateRenewal(_: Nino, _: RenewalReference)(_: HeaderCarrier, _: ExecutionContext)).
      expects(nino, renewalReference, *, *).returning(Future successful Some(token))
  }

  def stubClaimantClaimsResponse(response: LegacyClaims, nino: Nino)(implicit mobileTaxCreditsRenewalService: MobileTaxCreditsRenewalService): Unit = {
    (mobileTaxCreditsRenewalService.legacyClaimantClaims(_: Nino)(_: HeaderCarrier, _: ExecutionContext)).expects(nino, *, *).returning(Future successful response)
  }

  def stubEmployedEarningsRti(employedEarningsRti: Option[EmployedEarningsRti], nino: Nino)(implicit mobileTaxCreditsRenewalService: MobileTaxCreditsRenewalService): Unit = {
    (mobileTaxCreditsRenewalService.employedEarningsRti(_: Nino)(_: HeaderCarrier, _: ExecutionContext)).expects(nino, *, *).returning(Future successful employedEarningsRti)
  }
}