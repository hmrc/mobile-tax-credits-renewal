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

package uk.gov.hmrc.mobiletaxcreditsrenewal.domain

import java.time.LocalDateTime

import play.api.libs.json._
import uk.gov.hmrc.domain.Nino

object Person {
  implicit val formats: OFormat[Person] = Json.format[Person]
}

case class Person(
  firstName:   Option[String],
  middleName:  Option[String],
  lastName:    Option[String],
  initials:    Option[String],
  title:       Option[String],
  honours:     Option[String],
  sex:         Option[String],
  dateOfBirth: Option[LocalDateTime],
  nino:        Option[Nino]) {

  lazy val shortName: Option[String] = for {
    f <- firstName
    l <- lastName
  } yield List(f, l).mkString(" ")
  lazy val fullName: String = List(title, firstName, middleName, lastName, honours).flatten.mkString(" ")
}

object Address {
  implicit val formats: OFormat[Address] = Json.format[Address]
}

case class Address(
  line1:     Option[String],
  line2:     Option[String],
  line3:     Option[String],
  line4:     Option[String],
  postcode:  Option[String],
  startDate: Option[LocalDateTime],
  `type`:    Option[String]) {
  lazy val lines: List[String] = List(line1, line2, line3, line4).flatten
}

case class PersonDetails(
  etag:                  String,
  person:                Person,
  address:               Option[Address],
  correspondenceAddress: Option[Address])

object PersonDetails {
  implicit val formats: OFormat[PersonDetails] = Json.format[PersonDetails]
}
