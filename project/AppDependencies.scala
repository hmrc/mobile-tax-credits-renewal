import sbt._

private object AppDependencies {
  import play.core.PlayVersion
  import play.sbt.PlayImport._

  private val bootstrap27Version       = "5.1.0"
  private val domainVersion            = "5.11.0-play-27"
  private val playHmrcApiVersion       = "6.2.0-play-27"
  private val playUIVersion            = "9.2.0-play-27"
  private val circuitBreakerVersion    = "3.5.0"
  private val wiremockVersion          = "2.21.0"
  private val scalatestplusPlayVersion = "4.0.3"
  private val refinedVersion           = "0.9.4"
  private val pegdownVersion           = "1.6.0"
  private val scalaMockVersion         = "4.1.0"
  private val scalaTestVersion         = "3.0.8"
  private val timeVersion              = "3.19.0"

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "bootstrap-backend-play-27" % bootstrap27Version,
    "uk.gov.hmrc" %% "play-hmrc-api"             % playHmrcApiVersion,
    "uk.gov.hmrc" %% "domain"                    % domainVersion,
    "uk.gov.hmrc" %% "reactive-circuit-breaker"  % circuitBreakerVersion,
    "uk.gov.hmrc" %% "play-ui"                   % playUIVersion,
    "eu.timepit"  %% "refined"                   % refinedVersion,
    "uk.gov.hmrc" %% "time"                      % timeVersion
  )

  trait TestDependencies {
    lazy val scope: String        = "test"
    lazy val test:  Seq[ModuleID] = ???
  }

  object Test {

    def apply(): Seq[ModuleID] =
      new TestDependencies {

        override lazy val test = Seq(
          "com.typesafe.play" %% "play-test"              % PlayVersion.current % scope,
          "org.scalatest"     %% "scalatest"              % scalaTestVersion    % scope,
          "org.scalamock"     %% "scalamock"              % scalaMockVersion    % scope,
          "org.pegdown"       % "pegdown"                 % pegdownVersion      % scope,
          "uk.gov.hmrc"       %% "bootstrap-test-play-27" % bootstrap27Version  % scope
        )
      }.test
  }

  object IntegrationTest {

    def apply(): Seq[ModuleID] =
      new TestDependencies {

        override lazy val scope: String = "it"

        override lazy val test = Seq(
          "com.typesafe.play"      %% "play-test"          % PlayVersion.current      % scope,
          "com.github.tomakehurst" % "wiremock"            % wiremockVersion          % scope,
          "org.scalatestplus.play" %% "scalatestplus-play" % scalatestplusPlayVersion % scope
        )
      }.test
  }

  // Transitive dependencies in scalatest/scalatestplusplay drag in a newer version of jetty that is not
  // compatible with wiremock, so we need to pin the jetty stuff to the older version.
  // see https://groups.google.com/forum/#!topic/play-framework/HAIM1ukUCnI
  val jettyVersion = "9.2.13.v20150730"

  val jettyOverrides: Seq[ModuleID] = Seq(
    "org.eclipse.jetty"           % "jetty-server"       % jettyVersion,
    "org.eclipse.jetty"           % "jetty-servlet"      % jettyVersion,
    "org.eclipse.jetty"           % "jetty-security"     % jettyVersion,
    "org.eclipse.jetty"           % "jetty-servlets"     % jettyVersion,
    "org.eclipse.jetty"           % "jetty-continuation" % jettyVersion,
    "org.eclipse.jetty"           % "jetty-webapp"       % jettyVersion,
    "org.eclipse.jetty"           % "jetty-xml"          % jettyVersion,
    "org.eclipse.jetty"           % "jetty-client"       % jettyVersion,
    "org.eclipse.jetty"           % "jetty-http"         % jettyVersion,
    "org.eclipse.jetty"           % "jetty-io"           % jettyVersion,
    "org.eclipse.jetty"           % "jetty-util"         % jettyVersion,
    "org.eclipse.jetty.websocket" % "websocket-api"      % jettyVersion,
    "org.eclipse.jetty.websocket" % "websocket-common"   % jettyVersion,
    "org.eclipse.jetty.websocket" % "websocket-client"   % jettyVersion
  )

  def apply(): Seq[ModuleID] = compile ++ Test() ++ IntegrationTest()
}
