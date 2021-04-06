package org.lasersonlab.map

import hammerlab.option._
import org.lasersonlab.map.ConcurrentLRUMapBase.Value
import slogging._

abstract class ConcurrentLRUMapBase[K, V](val maxSize: Int)(implicit val generation: Generation)
  extends LazyLogging {

  val minSize = maxSize / 2

  private implicit val g: Generation.Aux[G] = generation
  type G = generation.T

  def map: Map[K, V]
  def versionedMap: Map[K, Value[V, G]]

  def size: Int

  def put        (k: K, v: V): ?[V]
  def putIfAbsent(k: K, v: V): ?[V]

  def  +=(  k: K, v: V): this.type
  def ++=(kvs: (K, V)*): this.type

  def get(k: K): ?[V]
}

object ConcurrentLRUMapBase {
  case class Value[V, G](value: V, generation: G) {
    def update(implicit g: Generation.Aux[G]): Value[V, g.T] = Value(value)
  }
  object Value {
    def apply[V, G](v: V)(implicit g: Generation.Aux[G]): Value[V, G] = Value(v, g())
    implicit def wrap[V](v: V)(implicit g: Generation): Value[V, g.T] = Value(v, g())
  }
}
