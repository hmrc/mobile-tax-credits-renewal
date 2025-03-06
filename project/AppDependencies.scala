import sbt._
import play.sbt.PlayImport._

private object AppDependencies {

  private val bootstrap30Version    = "9.11.0"
  private val domainVersion         = "10.0.0"
  private val playHmrcApiVersion    = "8.0.0"
  private val circuitBreakerVersion = "5.0.0"
  private val refinedVersion        = "0.11.3"
  private val scalaMockVersion      = "5.2.0"

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-backend-play-30" % bootstrap30Version,
    "uk.gov.hmrc" %% "play-hmrc-api-play-30"     % playHmrcApiVersion,
    "uk.gov.hmrc" %% "domain-play-30"            % domainVersion,
    "uk.gov.hmrc" %% "reactive-circuit-breaker"  % circuitBreakerVersion,
    "eu.timepit"  %% "refined"                   % refinedVersion
  )

  trait TestDependencies {
    lazy val scope: String        = "test"
    lazy val test:  Seq[ModuleID] = ???
  }

  private def testCommon(scope: String) = Seq("uk.gov.hmrc" %% "bootstrap-test-play-30" % bootstrap30Version % scope)

  object Test {

    def apply(): Seq[ModuleID] =
      new TestDependencies {

        override lazy val test = testCommon(scope) ++ Seq(
            "org.scalamock" %% "scalamock"              % scalaMockVersion   % scope,
            "uk.gov.hmrc"   %% "bootstrap-test-play-30" % bootstrap30Version % scope
          )
      }.test
  }

  object IntegrationTest {

    def apply(): Seq[ModuleID] =
      new TestDependencies {

        override lazy val scope: String = "it"

        override lazy val test = testCommon(scope) ++ Seq(
            )
      }.test
  }

  def apply(): Seq[ModuleID] = compile ++ Test() ++ IntegrationTest()
}
