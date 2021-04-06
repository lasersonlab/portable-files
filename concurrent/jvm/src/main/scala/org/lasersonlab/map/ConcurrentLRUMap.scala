package org.lasersonlab.map

import hammerlab.option._
import org.lasersonlab.map.ConcurrentLRUMapBase.Value

import scala.collection.concurrent.TrieMap
import scala.math.max

case class ConcurrentLRUMap    [K, V](override val maxSize: Int)(implicit override val generation: Generation)
   extends ConcurrentLRUMapBase[K, V](             maxSize     )(                      generation            )
{
  private implicit val g: Generation.Aux[G] = generation
  import g.ord

  import logger._
  private val _map = new TrieMap[K, Value[V, G]]()

  def map: Map[K, V] = _map.mapValues(_.value).toMap
  def versionedMap: Map[K, Value[V, G]] = _map.readOnlySnapshot().toMap

  def size = _map.size

  def put(k: K, v: V): ?[V] = put(k, v, minSize)
  private def put(k: K, v: V, minSize: Int): ?[V] = {
    _map
    .put(k, Value(v))
    .map { _.value }
    .orElse {
      maybeCompact(minSize)
      None
    }
  }

  Map(1→2) + (1 → 2)

  def putIfAbsent(k: K, v: V): Option[V] = {
    val value = Value(v)
    _map
      .putIfAbsent(k, value)
      .map {
        case Value(existing, _) ⇒
          access(k, Value(existing, value.generation))
          existing
      }
      .orElse {
        maybeCompact(minSize)
        None
      }
  }

  /** If [[_map]] is too large, drop elements until we are at half of [[maxSize]] */
  protected def maybeCompact(minSize: Int): Unit = {
    val size = _map.size
    if (size > maxSize) {
      val numToRemove = size - minSize
      val removalFailures =
        _map
        .toVector
        .sortBy { _._2.generation }
        .iterator
        .take { numToRemove }
        .filterNot {
          case (k, v) ⇒
            if (_map.remove(k, v))
              true
            else {
              debug(s"Failed to page out $k → $v")
              false
            }
        }
        .size

      debug({
        val removed = numToRemove - removalFailures
        s"Compacted $size elements down to ${size - removed}; successfully removed $removed, $removalFailures were updated during compaction"
      })
    }
  }

  def +=(k: K, v: V): this.type = { put(k, v); this }

  def ++=(kvs: (K, V)*): this.type = {
    val minSize = max(this.minSize, kvs.size)
    for {
      (k, v) ← kvs
    } {
      put(k, v, minSize = minSize)
    }
    this
  }

  protected def access(k: K, v: Value[V, G]): V = {
    _map.put(k, v) match {
      case Some(value @ Value(v2, existing)) if ord.lteq(existing, v.generation) ⇒
        debug(s"Bumping key $k: $value → $v")
        v.value
      case Some(newer) ⇒
        debug(s"Access superceded: $k, $v by $newer")
        access(k, v)
      case None ⇒ v.value
    }
  }

  def get(k: K): ?[V] =
    _map
    .get(k)
    .map {
      v ⇒
        access(
          k,
          v.update
        )
    }

  def iterator: Iterator[(K, V)] = _map.iterator.map { case (k, Value(v, _)) ⇒ (k, v) }
}
