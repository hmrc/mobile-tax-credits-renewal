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

import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.ConfidenceLevel.{L200, L50}
import uk.gov.hmrc.auth.core.syntax.retrieved._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mobiletaxcreditsrenewal.controllers.action.{Authorisation, Authority}
import uk.gov.hmrc.mobiletaxcreditsrenewal.stubs.AuthorisationStub

class AuthorisationSpec
    extends AnyWordSpecLike
    with Matchers
    with FutureAwaits
    with DefaultAwaitTimeout
    with AuthorisationStub
    with MockFactory {
  val nino          = "CS700100A"
  val incorrectNino = Nino("SC100700A")

  implicit val hc: HeaderCarrier = HeaderCarrier()

  implicit val mockAuthConnector: AuthConnector = mock[AuthConnector]

  def authorisation(mockAuthConnector: AuthConnector): Authorisation =
    new Authorisation {
      override val confLevel:     Int           = 200
      override def authConnector: AuthConnector = mockAuthConnector
    }

  "Authorisation grantAccess" should {

    "successfully grant access when nino exists and confidence level is 200" in {
      stubAuthorisationGrantAccess(Some(nino) and L200)
      val authority: Authority = await(authorisation(mockAuthConnector).grantAccess(Nino(nino)))
      authority.nino.value shouldBe nino
    }

    "error with unauthorised when account has low CL" in {
      stubAuthorisationGrantAccess(Some(nino) and L50)
      intercept[AccountWithLowCL] {
        await(authorisation(mockAuthConnector).grantAccess(Nino(nino)))
      }
    }

    "fail to return authority when no NINO exists" in {
      stubAuthorisationGrantAccess(None and L200)
      intercept[NinoNotFoundOnAccount] {
        await(authorisation(mockAuthConnector).grantAccess(Nino(nino)))
      }

      stubAuthorisationGrantAccess(Some("") and L200)
      intercept[NinoNotFoundOnAccount] {
        await(authorisation(mockAuthConnector).grantAccess(Nino(nino)))
      }
    }

    "fail to return authority when auth NINO does not match request NINO" in {
      stubAuthorisationGrantAccess(Some(nino) and L200)
      intercept[FailToMatchTaxIdOnAuth] {
        await(authorisation(mockAuthConnector).grantAccess(incorrectNino))
      }
    }
  }
}
