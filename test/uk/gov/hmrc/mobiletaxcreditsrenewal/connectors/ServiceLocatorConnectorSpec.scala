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

package uk.gov.hmrc.mobiletaxcreditsrenewal.connectors

import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.Writes
import uk.gov.hmrc.api.connector.ServiceLocatorConnector
import uk.gov.hmrc.api.domain.Registration
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class ServiceLocatorConnectorSpec extends UnitSpec with MockFactory with ScalaFutures {

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val serviceLocatorException = new RuntimeException
    val mockHttp: CorePost = mock[CorePost]
    val mockHandlerOK: () => Unit = mock[() => Unit]
    val mockHandlerError: Throwable => Unit = mock[(Throwable) => Unit]

    val connector: ServiceLocatorConnector = new ServiceLocatorConnector {
      override lazy val http: CorePost = mockHttp
      override lazy val appUrl: String = "http://api-microservice-template.service"
      override lazy val appName: String = "api-microservice-template"
      override lazy val serviceUrl: String = "https://SERVICE_LOCATOR"
      override lazy val handlerOK: () => Unit = mockHandlerOK
      override lazy val handlerError: Throwable => Unit = mockHandlerError
      override lazy val metadata: Option[Map[String, String]] = Some(Map("third-party-api" -> "true"))
    }
  }

  "register" should {
    "register the JSON API Definition into the Service Locator" in new Setup {

      val registration = Registration(serviceName = "api-microservice-template", serviceUrl = "http://api-microservice-template.service",
        metadata = Some(Map("third-party-api" -> "true")))

      (mockHttp.POST(_: String, _: Registration, _: Seq[(String, String)])
        (_: Writes[Registration], _: HttpReads[HttpResponse], _: HeaderCarrier, _:ExecutionContext)).expects(
        s"${connector.serviceUrl}/registration", registration, *, *, *, *, *).returning(Future.successful(HttpResponse(200)))
      (mockHandlerOK.apply _).expects()

      connector.register.futureValue shouldBe true
    }

    "fail registering in service locator" in new Setup {

      val registration = Registration(serviceName = "api-microservice-template", serviceUrl = "http://api-microservice-template.service",
        metadata = Some(Map("third-party-api" -> "true")))

      (mockHttp.POST(_: String, _: Registration, _: Seq[(String, String)])
      (_: Writes[Registration], _: HttpReads[HttpResponse], _: HeaderCarrier, _:ExecutionContext)).expects(
        s"${connector.serviceUrl}/registration", registration, *, *, *, *, *).returning(Future.failed(serviceLocatorException))
      (mockHandlerError.apply _).expects(*)

      connector.register.futureValue shouldBe false
    }

  }
}
