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

import org.joda.time.DateTime
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.TaxCreditsNino
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.userdata._
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils

trait TaxCreditBrokerConnectorStub extends UnitSpec {

  val expectedNextDueDate: DateTime = DateTime.parse("2015-07-16")
  val expectedPaymentWTC = FuturePayment(160.34, expectedNextDueDate, oneOffPayment = false)
  val expectedPaymentCTC = FuturePayment(140.12, expectedNextDueDate, oneOffPayment = false)
  val paymentSectionCTC = PaymentSection(List(expectedPaymentCTC), "weekly")
  val paymentSectionWTC = PaymentSection(List(expectedPaymentWTC), "weekly")
  val paymentSummary = PaymentSummary(Some(paymentSectionWTC), Some(paymentSectionCTC), paymentEnabled = true)

  val AGE16: DateTime = DateTimeUtils.now.minusYears(16)
  val AGE15: DateTime = DateTimeUtils.now.minusYears(15)
  val AGE13: DateTime = DateTimeUtils.now.minusYears(13)
  val AGE21: DateTime = DateTimeUtils.now.minusYears(21)
  val DECEASED_DATE: DateTime = DateTimeUtils.now.minusYears(1)

  val SarahSmith = Child("Sarah", "Smith", new DateTime(AGE16), hasFTNAE = false, hasConnexions = false, isActive = true, None)
  val JosephSmith = Child("Joseph", "Smith", new DateTime(AGE15), hasFTNAE = false, hasConnexions = false, isActive = true, None)
  val MarySmith = Child("Mary", "Smith", new DateTime(AGE13), hasFTNAE = false, hasConnexions = false, isActive = true, None)
  val JennySmith = Child("Jenny", "Smith", new DateTime(AGE21), hasFTNAE = false, hasConnexions = false, isActive = true, None)
  val PeterSmith = Child("Peter", "Smith", new DateTime(AGE13), hasFTNAE = false, hasConnexions = false, isActive = false, Some(new DateTime(DECEASED_DATE)))
  val SimonSmith = Child("Simon", "Smith", new DateTime(AGE13), hasFTNAE = false, hasConnexions = false, isActive = true, Some(new DateTime(DECEASED_DATE)))

  val address = uk.gov.hmrc.mobiletaxcreditsrenewal.domain.userdata.Address("addressLine1", "addressLine2", Some("addressLine3"), Some("addressLine4"), Some("postcode"))

  def personalDetails(nino: String) = PersonalDetails("firstname", "surname", TaxCreditsNino(nino), address, None, None, None, None)

  def partnerDetails(nino: String) = PartnerDetails("forename", Some("othernames"), "surname", TaxCreditsNino(nino), address, None, None, None, None)
}
