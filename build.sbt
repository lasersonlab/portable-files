import scalajs._

default(
  group("org.lasersonlab"),
  versions(
    hammerlab.          bytes → "1.3.0",
    hammerlab.     math.utils → "2.4.0",
    hammerlab.          types → "1.5.0",

    dom → "0.9.6"
  ),
  circe.version := "0.11.1",
)

lazy val concurrent =
  cross
    .settings(
      v"0.1",
      dep(
        case_app,
        cats
      ),
    )
lazy val `concurrent-x` = concurrent.x

lazy val files =
  cross
    .settings(
      v"0.1",
      name := "portable-files",
      dep(
        cats,
        cats.effect,

        circe,
        circe.generic,
        circe.generic.extras,
        circe.parser,

        fs2,

        hammerlab.bytes,
        hammerlab.types,
        hammerlab.math.utils,

        sourcecode,
        sttp,

        "io.github.cquiroz" ^^ "scala-java-time" ^ "2.0.0-M13",
      ),
      enableMacroParadise,
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
        slf4j.simple
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

lazy val all =
  root(
     `concurrent-x` ,
          `files-x` ,
  )
