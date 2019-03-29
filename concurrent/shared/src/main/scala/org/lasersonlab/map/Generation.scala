package org.lasersonlab.map

import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

trait Generation {
  type T
  implicit val ord: Ordering[T]
  def apply(): T
}

object Generation {
  def apply[T]()(implicit g: Generation): g.T = g()

  type Aux[_T] = Generation { type T = _T }

  object instant {
    implicit val instant: Aux[Instant] = {
      val _ord = implicitly[Ordering[Instant]]
      new Generation {
        type T = Instant
        val ord = _ord
        override def apply(): Instant = Instant.now()
      }
    }
  }

  implicit val long: Aux[Long] = {
    val _ord = implicitly[Ordering[Long]]
    new Generation {
      type T = Long
      val ord = _ord
      private val a = new AtomicLong()
      override def apply(): Long = a.getAndIncrement()
    }
  }
}
