
lazy val `account-transfers` =
  project
    .in(file("."))
    .settings(settings)
    .settings(
      libraryDependencies ++= Seq(
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

