
val http4sVersion = "0.18.4"

lazy val `account-transfers` =
  project
    .in(file("."))
    .settings(settings)
    .settings(
      libraryDependencies ++= Seq(
        "org.http4s"      %% "http4s-blaze-server" % http4sVersion,
        "org.http4s"      %% "http4s-circe"        % http4sVersion,
        "org.http4s"      %% "http4s-dsl"          % http4sVersion,
        "io.circe" %% "circe-generic" % "0.9.1",
        "ch.qos.logback" % "logback-classic" % "1.2.3",
        "io.monix" %% "monix" % "3.0.0-RC1",
        "org.scalacheck" %% "scalacheck" % "1.13.4" % Test,
        "org.scalatest" %% "scalatest" % "3.0.5" % Test
      )
    )


lazy val settings = Seq(
    scalaVersion := "2.12.5",
    organization := "com.svalaskevicius",
    organizationName := "svalaskevicius",
    startYear := Some(2018),
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-language:_",
      "-target:jvm-1.8",
      "-encoding", "UTF-8",
      "-Ypartial-unification",
      "-Ywarn-unused-import"
    )
)

