package org.lasersonlab.map

import org.lasersonlab.map.ConcurrentLRUMap.Value

import scala.collection.concurrent.TrieMap

case class ConcurrentLRUMap[K, V](maxSize: Int)(implicit val generation: Generation) {

  implicit val g: Generation.Aux[G] = generation
  type G = generation.T
  import generation.ord

  private val _map = new TrieMap[K, Value[V, G]]()

  def map: collection.Map[K, V] = _map.mapValues(_.value)
  def versionedMap: collection.Map[K, Value[V, G]] = _map.readOnlySnapshot()

  def size = _map.size

  def put(k: K, v: V): Option[V] = {
    _map
      .put(k, Value(v))
      .map { _.value }
      .orElse {
        maybeCompact()
        None
      }
  }

  def putIfAbsent(k: K, v: V): Option[V] = {
    _map
      .putIfAbsent(k, Value(v))
      .map {
        case value @ Value(v, _) ⇒
          access(k, value)
          v
      }
      .orElse {
        maybeCompact()
        None
      }
  }

  /** If [[_map]] is too large, drop elements until we are at half of [[maxSize]] */
  private def maybeCompact(): Unit = {
    if (_map.size > maxSize) {
      _map
        .toVector
        .sortBy { _._2.generation }
        .iterator
        .drop { maxSize / 2 }
        .filterNot {
          case (k, v) ⇒
            _map.remove(k, v)
        }
    }
  }

  def +=(k: K, v: V): this.type = { _map.put(k, Value(v)); this }

  private def access(k: K, v: Value[V, G]): V = {
    _map.put(k, v) match {
      case Some(Value(v2, g)) if ord.lteq(g, v.generation) ⇒ v.value
      case Some(v) ⇒ access(k, v)
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

  def getOrElseUpdate(k: K, v: ⇒ V): V =
    _map
      .get(k)
      .fold {
        _map.putIfAbsent(k, Value(v)).fold { v } { _.value }
      } {
        _.value
      }

  def iterator: Iterator[(K, V)] = _map.iterator.map { case (k, Value(v, _)) ⇒ (k, v) }
}

object ConcurrentLRUMap {
  case class Value[V, G](value: V, generation: G) {
    def update(implicit g: Generation.Aux[G]): Value[V, g.T] = Value(value)
  }
  object Value {
    def apply[V, G](v: V)(implicit g: Generation): Value[V, g.T] = Value(v, g())
  }
}
