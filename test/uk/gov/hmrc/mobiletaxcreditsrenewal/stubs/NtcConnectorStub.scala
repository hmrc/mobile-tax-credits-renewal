/*
 * Copyright 2022 HM Revenue & Customs
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
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobiletaxcreditsrenewal.connectors.NtcConnector
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain._

import scala.concurrent.{ExecutionContext, Future}

trait NtcConnectorStub extends MockFactory {

  def stubAuthenticateRenewal(
    nino:                  TaxCreditsNino,
    renewalReference:      RenewalReference,
    token:                 TcrAuthenticationToken
  )(implicit ntcConnector: NtcConnector
  ): Unit =
    (ntcConnector
      .authenticateRenewal(_: TaxCreditsNino, _: RenewalReference)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, renewalReference, *, *)
      .returning(Future successful Some(token))

  def stubClaimantDetails(
    nino:                  TaxCreditsNino,
    claimantDetails:       ClaimantDetails
  )(implicit ntcConnector: NtcConnector
  ): Unit =
    (ntcConnector
      .claimantDetails(_: TaxCreditsNino)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, *)
      .returning(Future successful claimantDetails)

}
