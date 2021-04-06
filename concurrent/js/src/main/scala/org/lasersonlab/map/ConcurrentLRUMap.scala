package org.lasersonlab.map

import hammerlab.option._
import org.lasersonlab.map.ConcurrentLRUMapBase.Value

import scala.annotation.tailrec
import scala.collection.mutable.{ HashMap, TreeMap }

case class ConcurrentLRUMap    [K, V](override val maxSize: Int)(implicit override val generation: Generation)
   extends ConcurrentLRUMapBase[K, V](             maxSize     )(                      generation            )
{
  private implicit val g: Generation.Aux[G] = generation
  import g.ord

  private val _map = new HashMap[K, Value[V, G]]()
  private val  lru = new TreeMap[G, K]()

  def map: Map[K, V] = _map.toMap.mapValues { _.value }
  def versionedMap: Map[K, Value[V, G]] = _map.toMap

  override def size: Int = _map.size

  def put(k: K, v: V): ?[V] = {
    val prev =
      _map
        .get(k)
        .map {
          case Value(v, old) ⇒
            lru.remove(old)
            v
        }

    val gen = g()
    _map.put(k, Value(v, gen))
    lru.put(gen, k)

    maybeCompact()
    prev
  }

  override def +=(k: K, v: V): this.type = { put(k, v); this }

  override def ++=(kvs: (K, V)*): this.type = {
    for {
      (k, v) ← kvs
    } {
      put(k, v)
    }
    this
  }

  override def get(k: K): ?[V] =
    _map
      .get(k)
      .map {
        case Value(existing, old) ⇒
          lru.remove(old)
          val gen = g()
          lru.put(gen, k)
          _map.put(k, Value(existing, gen))
          existing
      }

  def putIfAbsent(k: K, v: V): ?[V] =
    get(k).orElse {
      put(k, v)
      None
    }

  @tailrec
  private def maybeCompact(): Unit =
    if (size > maxSize) {
      val (old, k) =
        lru
          .iterator
          .next()

      lru.remove(old)

      for {
        Value(v, gen) ← _map.get(k)
        if ord.lteq(gen, old)
      } {
        _map.remove(k)
      }

      maybeCompact()
    }

  def vector(lru: Boolean = true): Vector[(K, V)] =
    if (lru)
      this
        .lru
        .iterator
        .map {
          case (_, k) ⇒
            k → _map(k).value
        }
        .toVector
    else
      _map
        .iterator
        .map {
          case (k, Value(v, _)) ⇒
               (k,       v    )
        }
        .toVector

  def iterator(lru: Boolean = true): Iterator[(K, V)] = vector(lru).iterator
}
