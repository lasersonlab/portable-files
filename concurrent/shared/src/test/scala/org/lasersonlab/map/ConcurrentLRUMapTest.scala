package org.lasersonlab.map

import hammerlab.or._
import org.hammerlab.cmp.CanEq
import org.hammerlab.test.Cmp
import org.lasersonlab.map.ConcurrentLRUMap.Value

class ConcurrentLRUMapTest
  extends hammerlab.Suite
     with Cmps
{
  type K = Int
  type V = String
  def ??[D](map: ConcurrentLRUMap[Int, String], expected: (K, (V, Long))*)(implicit c1: Cmp.Aux[Map[K, V], D]): Unit = {
    ==(
      map
        .versionedMap
        .toMap
        .mapValues {
          case Value(v, g) ⇒ (v, g.asInstanceOf[Long])
        }
        .toVector
      .sortBy { -_._2._2 },
      expected
    )
    ==(
      map.map.toMap,
      expected.toMap.mapValues { _._1 }
    )
  }

  test("simple") {
    val map = ConcurrentLRUMap[Int, String](6)
    import map.{ == ⇒ _, _ }

    ??(map)

    ==(get(111), None)
    ??(map)

    var `111` = 0L
    ==(put(111, "111"), None)
    ??(map, 111 → ("111", `111`))

    ==(get(111), Some("111")); `111` += 1
    ??(map, 111 → ("111", `111`))

    ==(putIfAbsent(111, "---"), Some("111")); `111` += 1
    ??(map, 111 → ("111", `111`))

    ==(get(111), Some("111")); `111` += 1
    ??(map, 111 → ("111", `111`))

    ==(get(222), None)
    ??(map, 111 → ("111", `111`))

    var `222` = `111` + 1
    ==(putIfAbsent(222, "222"), None)
    ??(
      map,
      222 → ("222", `222`),
      111 → ("111", `111`),
    )

    ==(get(222), Some("222")); `222` += 1
    ??(
      map,
      222 → ("222", `222`),
      111 → ("111", `111`),
    )

    ==(putIfAbsent(111, "---"), Some("111")); `111` = `222` + 1
    ??(
      map,
      111 → ("111", `111`),
      222 → ("222", `222`),
    )

    ==(putIfAbsent(222, "---"), Some("222")); `222` = `111` + 1
    ??(
      map,
      222 → ("222", `222`),
      111 → ("111", `111`),
    )

    var `333` = `222` + 1
    var `444` = `333` + 1
    var `555` = `444` + 1
    var `666` = `555` + 1
    ??(
      map ++= (333 → "333", 444 → "444", 555 → "555", 666 → "666"),
      666 → ("666", `666`),
      555 → ("555", `555`),
      444 → ("444", `444`),
      333 → ("333", `333`),
      222 → ("222", `222`),
      111 → ("111", `111`),
    )

    var `777` = `666` + 1
    ??(
      map += (777, "777"),
      777 → ("777", `777`),
      666 → ("666", `666`),
      555 → ("555", `555`),
    )

    ==(get(111), None)
    ==(get(222), None)
    ==(get(333), None)
    ==(get(444), None)
    ??(
      map,
      777 → ("777", `777`),
      666 → ("666", `666`),
      555 → ("555", `555`),
    )

    ==(putIfAbsent(444, "444"), None); `444` = `777` + 1
    ??(
      map,
      444 → ("444", `444`),
      777 → ("777", `777`),
      666 → ("666", `666`),
      555 → ("555", `555`),
    )

    ==(put(333, "ccc"), None); `333` = `444` + 1
    ??(
      map,
      333 → ("ccc", `333`),
      444 → ("444", `444`),
      777 → ("777", `777`),
      666 → ("666", `666`),
      555 → ("555", `555`),
    )

    ==(get(555), Some("555")); `555` = `333` + 1
    ??(
      map,
      555 → ("555", `555`),
      333 → ("ccc", `333`),
      444 → ("444", `444`),
      777 → ("777", `777`),
      666 → ("666", `666`),
    )

    var `888` = `555` + 1
    var `999` = `888` + 1
    ??(
      map ++= (888 → "888", 999 → "999"),
      999 → ("999", `999`),
      888 → ("888", `888`),
      555 → ("555", `555`),
    )

    ==(put(555, "eee"), Some("555")); `555` = `999` + 1
    ??(
      map,
      555 → ("eee", `555`),
      999 → ("999", `999`),
      888 → ("888", `888`),
    )
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
      c1.Diff ||
      c2.Diff
    ] =
    CanEq {
      (l, rhs) ⇒
        val r = map(rhs)
        c1(
          l
            .map
            .toMap,
          r
            .mapValues { _._1 }
        ) ||
        c2(
          l
            .versionedMap
            .toMap
            .mapValues {
              case Value(v, g) ⇒ (v, g.asInstanceOf[Long])
            },
          r
        )
    }
}
object Cmps extends Cmps
