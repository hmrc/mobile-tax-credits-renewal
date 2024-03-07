import sbt._
import play.sbt.PlayImport._

private object AppDependencies {


  private val bootstrap28Version    = "7.20.0"
  private val domainVersion         = "8.1.0-play-28"
  private val playHmrcApiVersion    = "7.2.0-play-28"
  private val circuitBreakerVersion = "4.1.0"
  private val wiremockVersion       = "2.21.0"
  private val refinedVersion        = "0.9.26"
  private val pegdownVersion        = "1.6.0"
  private val scalaMockVersion      = "5.1.0"

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-backend-play-28" % bootstrap28Version,
    "uk.gov.hmrc" %% "play-hmrc-api"             % playHmrcApiVersion,
    "uk.gov.hmrc" %% "domain"                    % domainVersion,
    "uk.gov.hmrc" %% "reactive-circuit-breaker"  % circuitBreakerVersion,
    "eu.timepit"  %% "refined"                   % refinedVersion
  )

  trait TestDependencies {
    lazy val scope: String        = "test"
    lazy val test:  Seq[ModuleID] = ???
  }

  private def testCommon(scope: String) = Seq("uk.gov.hmrc" %% "bootstrap-test-play-28" % bootstrap28Version % scope)

  object Test {

    def apply(): Seq[ModuleID] =
      new TestDependencies {

        override lazy val test = testCommon(scope) ++ Seq(
            "org.scalamock" %% "scalamock"              % scalaMockVersion   % scope,
            "org.pegdown"   % "pegdown"                 % pegdownVersion     % scope,
            "uk.gov.hmrc"   %% "bootstrap-test-play-28" % bootstrap28Version % scope
          )
      }.test
  }

  object IntegrationTest {

    def apply(): Seq[ModuleID] =
      new TestDependencies {

        override lazy val scope: String = "it"

        override lazy val test = testCommon(scope) ++ Seq(
            "com.github.tomakehurst" % "wiremock" % wiremockVersion % scope
          )
      }.test
  }

  def apply(): Seq[ModuleID] = compile ++ Test() ++ IntegrationTest()
}
