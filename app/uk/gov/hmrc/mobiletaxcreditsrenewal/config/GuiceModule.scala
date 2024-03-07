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

package uk.gov.hmrc.mobiletaxcreditsrenewal.config

import com.google.inject.AbstractModule
import com.google.inject.name.Names.named
import play.api.Logger
import javax.inject.Inject
import play.api.{Configuration, Environment, LoggerLike}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{CoreGet, CorePost}
import uk.gov.hmrc.mobiletaxcreditsrenewal.controllers.api.ApiAccess
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.{TaxCreditsControl, TaxCreditsSubmissionControlConfig}
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpClient

import scala.jdk.CollectionConverters._

class GuiceModule @Inject() (
  environment:   Environment,
  configuration: Configuration)
    extends AbstractModule {

  val servicesConfig: ServicesConfig = new ServicesConfig(configuration)
  val logger: Logger = Logger(this.getClass)

  override def configure(): Unit = {
    bind(classOf[AuthConnector]).to(classOf[DefaultAuthConnector])
    bind(classOf[CoreGet]).to(classOf[WSHttpImpl])
    bind(classOf[CorePost]).to(classOf[WSHttpImpl])
    bind(classOf[HttpClient]).to(classOf[WSHttpImpl])
    bind(classOf[TaxCreditsControl]).to(classOf[TaxCreditsSubmissionControlConfig])

    bind(classOf[ApiAccess]).toInstance(
      ApiAccess("PRIVATE", configuration.underlying.getStringList("api.access.white-list.applicationIds").asScala)
    )

    bindConfigInt("controllers.confidenceLevel")
    bindConfigString("appUrl", "appUrl")
    bindConfigString("submission.startDate", "microservice.services.ntc.submission.startDate")
    bindConfigString("submission.endDate", "microservice.services.ntc.submission.endDate")
    bindConfigString("submission.endViewRenewalsDate", "microservice.services.ntc.submission.endViewRenewalsDate")
    bind(classOf[LoggerLike]).toInstance(logger)

    bind(classOf[String]).annotatedWith(named("ntc")).toInstance(servicesConfig.baseUrl("ntc"))
    bind(classOf[String])
      .annotatedWith(named("tax-credits-broker"))
      .toInstance(servicesConfig.baseUrl("tax-credits-broker"))
    bind(classOf[String])
      .annotatedWith(named("mobile-shuttering"))
      .toInstance(servicesConfig.baseUrl("mobile-shuttering"))
  }

  /**
    * Binds a configuration value using the `path` as the name for the binding.
    * Throws an exception if the configuration value does not exist or cannot be read as an Int.
    */
  private def bindConfigInt(path: String): Unit =
    bindConstant().annotatedWith(named(path)).to(configuration.underlying.getInt(path))

  private def bindConfigString(
    name: String,
    path: String
  ): Unit =
    bindConstant().annotatedWith(named(name)).to(configuration.underlying.getString(path))
}
