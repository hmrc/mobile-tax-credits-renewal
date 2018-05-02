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

package uk.gov.hmrc.personalincome.config

import com.google.inject.Singleton
import com.google.inject.name.Named
import javax.inject.Inject
import play.api.Configuration
import java.util

import scala.collection.JavaConversions._

case class RenewalStatusTransform(name: String, statusValues: Seq[String])

@Singleton
class AppContext @Inject()(@Named("renewalStatus") val renewalStatus: util.List[Configuration]) {

  lazy val renewalStatusTransform: List[RenewalStatusTransform] = renewalStatus.toList map {
    item =>
      RenewalStatusTransform(item.getString("toStatus").get, item.getStringList("fromStatus").get)
  }
}