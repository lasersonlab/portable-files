import scalajs._

default(
  group("org.lasersonlab"),
  versions(
    hammerlab.     math.utils → "2.4.0",
    hammerlab.          types → "1.6.0".snapshot,

    dom → "0.9.6",
    time → "2.0.0-M13",
  ),
  circe.version := "0.11.1",
)

lazy val concurrent =
  cross
    .settings(
      v"0.2.0",
      scalac.xms(1 GB),
      scalac.xmx(4 GB),
      dep(
        case_app,
        cats,
        hammerlab.types,
      ),
      hammerlab.test.version := "2.0.0".snapshot,
      takeFirstLog4JProperties,
    )
    .jvmSettings(
      dep(
        slf4j.slogging,
        slf4j.log4j tests,
      )
    )
    .jsSettings(
      dep(
        slogging
      )
    )
lazy val `concurrent-x` = concurrent.x

lazy val files =
  cross
    .settings(
      v"0.2.0",
      name := "portable-files",
      dep(
        cats,
        cats.effect,

        circe,
        circe.generic,
        circe.generic.extras,
        circe.parser,

        fs2,

        hammerlab.types,
        hammerlab.math.utils,

        sourcecode,
        sttp,
        time,
      ),
      enableMacroParadise,
      publishTestJar,
      utest
    )
    .jvmSettings(
      http4s.version := "0.19.0",
      dep(
        akka.actor,
        akka.stream,
        akka.http,
        akka.http.core,

        commons.io,

        http4s. dsl,
        http4s.`blaze-client`,

        slf4j.slogging,
        slf4j.simple,
      )
    )
    .jsSettings(
      scalaJSUseMainModuleInitializer := true,
      dep(
        slogging,
        dom,
        "io.scalajs.npm" ^^ "request" ^ "0.4.2"
      ),
    )
    .dependsOn(
      concurrent
    )
lazy val `files-x` = files.x

lazy val `portable-files` =
  root(
     `concurrent-x` ,
          `files-x` ,
  )
