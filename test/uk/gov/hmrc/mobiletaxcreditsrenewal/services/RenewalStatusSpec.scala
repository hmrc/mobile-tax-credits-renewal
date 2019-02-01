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

package uk.gov.hmrc.mobiletaxcreditsrenewal.services

import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpecLike}
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.{Applicant, Claim, Household, Renewal}

class RenewalStatusSpec extends WordSpecLike with Matchers with MockFactory {

  val renewalStatus: RenewalStatus = new RenewalStatus {}

  val household = Household("1010101", "1234", Applicant("NINO", "MR", "TOM", None, "SMITH"), None, None, None)

  def claim(renewalStatus: Option[String] = Some("SUPERCEDED")) = Claim(household, Renewal(None, None, renewalStatus, None, None))

  "Renewal status" should {
    "resolve to NOT_SUBMITTED" in {
      renewalStatus.resolveStatus(claim(Some("DISREGARD"))) shouldBe "NOT_SUBMITTED"
      renewalStatus.resolveStatus(claim(Some("UNKNOWN")))   shouldBe "NOT_SUBMITTED"
    }

    "resolve to SUBMITTED_AND_PROCESSING" in {
      renewalStatus.resolveStatus(claim(Some("S17 LOGGED")))                         shouldBe "SUBMITTED_AND_PROCESSING"
      renewalStatus.resolveStatus(claim(Some("SUPERCEDED")))                         shouldBe "SUBMITTED_AND_PROCESSING"
      renewalStatus.resolveStatus(claim(Some("PARTIAL CAPTURE")))                    shouldBe "SUBMITTED_AND_PROCESSING"
      renewalStatus.resolveStatus(claim(Some("AWAITING PROCESS")))                   shouldBe "SUBMITTED_AND_PROCESSING"
      renewalStatus.resolveStatus(claim(Some("INHIBITED")))                          shouldBe "SUBMITTED_AND_PROCESSING"
      renewalStatus.resolveStatus(claim(Some("AWAITING CHANGE OF CIRCUMSTANCES")))   shouldBe "SUBMITTED_AND_PROCESSING"
      renewalStatus.resolveStatus(claim(Some("1 REPLY FROM 2 APPLICANT HOUSEHOLD"))) shouldBe "SUBMITTED_AND_PROCESSING"
      renewalStatus.resolveStatus(claim(Some("DUPLICATE")))                          shouldBe "SUBMITTED_AND_PROCESSING"
    }

    "resolve to COMPLETE" in {
      renewalStatus.resolveStatus(claim(Some("REPLY USED FOR FINALISATION"))) shouldBe "COMPLETE"
      renewalStatus.resolveStatus(claim(Some("SYSTEM FINALISED")))            shouldBe "COMPLETE"
    }

    "handle unknown status as NOT_SUBMITTED" in {
      renewalStatus.resolveStatus(claim(None))        shouldBe "NOT_SUBMITTED"
      renewalStatus.resolveStatus(claim(Some("")))    shouldBe "NOT_SUBMITTED"
      renewalStatus.resolveStatus(claim(Some("foo"))) shouldBe "NOT_SUBMITTED"
    }

    "handles awaiting barcode" in {
      val noBarcodeClaim = claim().copy(household = household.copy(barcodeReference = "000000000000000"))

      renewalStatus.resolveStatus(noBarcodeClaim) shouldBe "AWAITING_BARCODE"
    }

    "transform case insensitively" in {
      renewalStatus.resolveStatus(claim(Some("SuPeRcEdEd"))) shouldBe "SUBMITTED_AND_PROCESSING"
    }

    "transform ignoring whitespace" in {
      renewalStatus.resolveStatus(claim(Some("   SUPERCEDED   "))) shouldBe "SUBMITTED_AND_PROCESSING"
    }
  }
}
