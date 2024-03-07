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

package uk.gov.hmrc.mobiletaxcreditsrenewal.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.util.Try

class ClaimsDateConverter {

  private val formatters: Seq[DateTimeFormatter] =
    Seq(DateTimeFormatter.ofPattern("yyyy-MM-dd"), DateTimeFormatter.ofPattern("yyyyMMdd"))

  def convertDateFormat(date: String): Option[String] = {
    def convert(
      result:    Option[String],
      formatter: DateTimeFormatter
    ): Option[String] =
      result.orElse {
        Try {
          Some(LocalDate.parse(date, formatter).format(DateTimeFormatter.ofPattern("d/M/yyyy")))
        }.getOrElse {
          None
        }
      }

    formatters.foldLeft(None: Option[String])(convert)
  }
}
