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

package uk.gov.hmrc.mobiletaxcreditsrenewal.config

import com.google.inject.AbstractModule
import com.google.inject.name.Names.named
import javax.inject.Inject
import play.api.{Configuration, Environment, Logger, LoggerLike}
import uk.gov.hmrc.api.connector.{ApiServiceLocatorConnector, ServiceLocatorConnector}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{CoreGet, CorePost}
import uk.gov.hmrc.mobiletaxcreditsrenewal.controllers.api.ApiAccess
import uk.gov.hmrc.mobiletaxcreditsrenewal.domain.{TaxCreditsControl, TaxCreditsSubmissionControlConfig}
import uk.gov.hmrc.mobiletaxcreditsrenewal.tasks.ServiceLocatorRegistrationTask
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.collection.JavaConverters._

class GuiceModule @Inject()(environment: Environment, configuration: Configuration) extends AbstractModule {

  val servicesConfig: ServicesConfig = new ServicesConfig(configuration, new RunMode(configuration, environment.mode))

  override def configure(): Unit = {
    bind(classOf[ServiceLocatorConnector]).to(classOf[ApiServiceLocatorConnector])
    bind(classOf[AuthConnector]).to(classOf[DefaultAuthConnector])
    bind(classOf[CoreGet]).to(classOf[WSHttpImpl])
    bind(classOf[CorePost]).to(classOf[WSHttpImpl])
    bind(classOf[HttpClient]).to(classOf[WSHttpImpl])
    bind(classOf[ServiceLocatorRegistrationTask]).asEagerSingleton()
    bind(classOf[TaxCreditsControl]).to(classOf[TaxCreditsSubmissionControlConfig])

    bind(classOf[ApiAccess]).toInstance(ApiAccess("PRIVATE", configuration.underlying.getStringList("api.access.white-list.applicationIds").asScala))

    bindConfigInt("controllers.confidenceLevel")
    bindConfigString("appUrl", "appUrl")
    bindConfigString("submission.startDate", "microservice.services.ntc.submission.startDate")
    bindConfigString("submission.endDate", "microservice.services.ntc.submission.endDate")
    bindConfigString("submission.endViewRenewalsDate", "microservice.services.ntc.submission.endViewRenewalsDate")
    bind(classOf[LoggerLike]).toInstance(Logger)

    bind(classOf[String]).annotatedWith(named("ntc")).toInstance(servicesConfig.baseUrl("ntc"))
  }

  /**
    * Binds a configuration value using the `path` as the name for the binding.
    * Throws an exception if the configuration value does not exist or cannot be read as an Int.
    */
  private def bindConfigInt(path: String): Unit =
    bindConstant().annotatedWith(named(path)).to(configuration.underlying.getInt(path))

  private def bindConfigString(name: String, path: String): Unit =
    bindConstant().annotatedWith(named(name)).to(configuration.underlying.getString(path))
}
