package org.lasersonlab.map

import cats.data.Ior
import hammerlab.or._
import org.hammerlab.cmp.CanEq
import org.hammerlab.test.Cmp
import org.lasersonlab.map.ConcurrentLRUMap.Value

class ConcurrentLRUMapTest
  extends hammerlab.Suite
     with Cmps
{
  test("simple") {
    val map = ConcurrentLRUMap[Int, String](6)
    import map._

    ===(map, Map())

    ===(get(111), None)
    ===(map, Map())

    ===(put(111, "111"), None)
    ===(map, Map(111 → ("111", 0L)))

    ===(get(111), Some("111"))
    ===(map, Map(111 → ("111", 1L)))

    ===(putIfAbsent(111, "111"), Some("111"))
    ===(map, Map(111 → ("111", 2L)))

    ===(get(111), Some("111"))
    ===(map, Map(111 → ("111", 3L)))
  }
}

/** Simple typeclass unifying Map[Nothing,Nothing] (as returned by the `Map()` constructor) with any type of Map */
trait Mapish[M, K, V] {
  def apply(m: M): Map[K, V]
}
object Mapish {
  implicit def id[K, V]: Mapish[Map[K, V], K, V] = { m ⇒ m }
  implicit def nothing[K, V]: Mapish[Map[Nothing, Nothing], K, V] = { _.asInstanceOf[Map[K, V]] }
}

trait Cmps {
  implicit def cmpVersioned[K, V, RHS](
    implicit
    c1: Cmp[Map[K,  V       ]],
    c2: Cmp[Map[K, (V, Long)]],
    map: Mapish[RHS, K, (V, Long)],
  ):
    CanEq.Aux[
      ConcurrentLRUMap[K, V],
      RHS,
      Or[
        c1.Diff,
        c2.Diff
      ]
    ] =
    CanEq {
      (l, rhs) ⇒
        val r = map(rhs)
        Ior.fromOptions(
          c1(
            l
              .map
              .toMap,
            r
              .mapValues { _._1 }
          ),
          c2(
            l
              .versionedMap
              .toMap
              .mapValues {
                case Value(v, g) ⇒ (v, g.asInstanceOf[Long])
              },
            r
          )
        )
    }
}
object Cmps extends Cmps
