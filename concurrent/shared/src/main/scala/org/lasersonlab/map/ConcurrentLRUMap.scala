package org.lasersonlab.map

import org.lasersonlab.map.ConcurrentLRUMap.Value
import slogging._

import scala.collection.concurrent.TrieMap
import math.max

case class ConcurrentLRUMap[K, V](maxSize: Int)(implicit val generation: Generation)
  extends LazyLogging {
  import logger._

  val minSize = maxSize / 2

  implicit val g: Generation.Aux[G] = generation
  type G = generation.T
  import generation.ord

  private val _map = new TrieMap[K, Value[V, G]]()

  def map: collection.Map[K, V] = _map.mapValues(_.value)
  def versionedMap: collection.Map[K, Value[V, G]] = _map.readOnlySnapshot()

  def size = _map.size

  def put(k: K, v: V, minSize: Int = minSize): Option[V] = {
    _map
      .put(k, Value(v))
      .map { _.value }
      .orElse {
        maybeCompact(minSize)
        None
      }
  }

  def putIfAbsent(k: K, v: V, minSize: Int = minSize): Option[V] = {
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
  private def maybeCompact(minSize: Int): Unit = {
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

  private def access(k: K, v: Value[V, G]): V = {
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

  def get(k: K): Option[V] =
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

object ConcurrentLRUMap {
  case class Value[V, G](value: V, generation: G) {
    def update(implicit g: Generation.Aux[G]): Value[V, g.T] = Value(value)
  }
  object Value {
    def apply[V, G](v: V)(implicit g: Generation): Value[V, g.T] = Value(v, g())
    implicit def wrap[V, G](v: V)(implicit g: Generation): Value[V, g.T] = Value(v, g())
  }
}
