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

package uk.gov.hmrc.mobiletaxcreditsrenewal.config

import com.google.inject.name.Named
import com.google.inject.name.Names.named
import com.google.inject.{AbstractModule, Provides}
import play.api.Mode.Mode
import play.api.{Configuration, Environment, Logger, LoggerLike}
import uk.gov.hmrc.api.connector.ServiceLocatorConnector
import uk.gov.hmrc.api.controllers.DocumentationController
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.CoreGet
import uk.gov.hmrc.mobiletaxcreditsrenewal.controllers.{ConfiguredShuttering, Shuttering}
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.{TaxCreditsControl, TaxCreditsSubmissionControlConfig}
import uk.gov.hmrc.mobiletaxcreditsrenewal.tasks.ServiceLocatorRegistrationTask
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.config.{AppName, ServicesConfig}

class GuiceModule(environment: Environment, configuration: Configuration) extends AbstractModule with ServicesConfig {

  override protected lazy val mode: Mode = environment.mode
  override protected lazy val runModeConfiguration: Configuration = configuration

  override def configure(): Unit = {
    bind(classOf[ServiceLocatorConnector]).to(classOf[ApiServiceLocatorConnector])
    bind(classOf[AuthConnector]).to(classOf[DefaultAuthConnector])
    bind(classOf[CoreGet]).to(classOf[WSHttpImpl])
    bind(classOf[HttpClient]).to(classOf[WSHttpImpl])
    bind(classOf[DocumentationController]).toInstance(DocumentationController)
    bind(classOf[ServiceLocatorRegistrationTask]).asEagerSingleton()
    bind(classOf[TaxCreditsControl]).to(classOf[TaxCreditsSubmissionControlConfig])
    bind(classOf[Shuttering]).to(classOf[ConfiguredShuttering])

    bindConfigBoolean("shuttering.shuttered", ("shuttering.shuttered"))
    bindConfigBase64String("shuttering.title")
    bindConfigBase64String("shuttering.message")

    bindConfigInt("controllers.confidenceLevel")
    bindConfigString("appUrl", "appUrl")
    bindConfigBoolean("submission.submissionShuttered", "microservice.services.ntc.submission.submissionShuttered")
    bindConfigString("submission.startDate", "microservice.services.ntc.submission.startDate")
    bindConfigString("submission.endDate", "microservice.services.ntc.submission.endDate")
    bindConfigString("submission.endViewRenewalsDate", "microservice.services.ntc.submission.endViewRenewalsDate")
    bind(classOf[LoggerLike]).toInstance(Logger)

    bind(classOf[String]).annotatedWith(named("ntc")).toInstance(baseUrl("ntc"))
  }

  @Provides
  @Named("appName")
  def appName: String = AppName(configuration).appName

  /**
    * Binds a configuration value using the `path` as the name for the binding.
    * Throws an exception if the configuration value does not exist or cannot be read as an Int.
    */
  private def bindConfigInt(path: String): Unit =
    bindConstant().annotatedWith(named(path)).to(configuration.underlying.getInt(path))

  private def bindConfigLong(path: String): Unit =
    bindConstant().annotatedWith(named(path)).to(configuration.underlying.getLong(path))

  private def bindConfigString(name: String, path: String): Unit =
    bindConstant().annotatedWith(named(name)).to(configuration.underlying.getString(path))

  private def bindConfigBoolean(name: String, path: String): Unit =
    bindConstant().annotatedWith(named(name)).to(configuration.underlying.getBoolean(path))

  private def bindConfigBase64String(path: String): Unit = {
    val encoded = configuration.underlying.getString(path)
    val decoded = Base64.decode(encoded)
    bindConstant().annotatedWith(named(path)).to(decoded)
  }

}
