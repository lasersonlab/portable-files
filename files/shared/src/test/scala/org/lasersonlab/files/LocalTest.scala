package org.lasersonlab.files

import utest._

object LocalTest
  extends TestSuite {

  import scala.concurrent.ExecutionContext.Implicits.global

  val tests = Tests{
    'local - {
      // TODO: introduce a pseudo-recursive dep (the versions won't actually be recursive) on testing utils,
      //  to load this more cleanly
      val shared = "shared/src/test/resources/test.txt"
      val uri = s"files/$shared"
      val resource =
        if (Local(uri).existsSync)
          Local(uri)
        else
          Local(s"../$shared")

      resource
        .string
        .map {
          actual ⇒
          assert(
            actual ==
              """a
                |12
                |abc
                |1234
                |abcde
                |"""
                .stripMargin

          )
        }
    }
  }
}
