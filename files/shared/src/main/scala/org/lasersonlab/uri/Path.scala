package org.lasersonlab.uri

import java.io.OutputStream
import java.net.URI
import java.nio.charset
import java.util
import java.util.concurrent.LinkedTransferQueue

import cats.MonadError
import cats.syntax.all._
import hammerlab.option._
import io.circe.Decoder
import io.circe.parser.decode
import Path.Charset
import org.lasersonlab.files.caching
import org.lasersonlab.files.caching.Config
import org.lasersonlab.map.ConcurrentLRUMap
import slogging.LazyLogging

import scala.collection.concurrent.TrieMap

trait Path[F[_], T] {
  implicit val F: MonadError[F, Throwable]

  def uri(t: T): URI

  def /(t: T, basename: String): T
  def ?(t: T, basename: String): F[T]
  def parent_?(t: T): ?[T]

  def exists(t: T): F[Boolean]
  def isDirectory(t: T): F[Boolean]
}

object Path {
  case class Charset(cs: charset.Charset) extends AnyVal
  trait Default {
    implicit val utf8 = Charset(charset.Charset.forName("UTF-8"))
  }
  object Charset
  extends Default {
    implicit def wrap(implicit cs: charset.Charset): Charset = Charset(cs)
    implicit def unwrap(cs: Charset): charset.Charset = cs.cs
  }

  trait syntax {
    implicit def makePathBasicOps[F[_], T](t: T): Ops[F, T] = new Ops(t)
  }

  final class Ops[F[_], T](val t: T) extends AnyVal {
    def uri(implicit path: Path[F, T]): URI = path.uri(t)

    def /(basename: String)(implicit path: Path[F, T]): T = path./(t, basename)
    def ?(basename: String)(implicit path: Path[F, T]): F[T] = path.?(t, basename)
    def parent_?(implicit path: Path[F, T]): ?[T] = path.parent_?(t)

    def exists(implicit path: Path[F, T]): F[Boolean] = path.exists(t)
    def isDirectory(implicit path: Path[F, T]): F[Boolean] = path.isDirectory(t)
  }
}

trait Metadata[F[_], T]
  extends Path[F, T] {
  def size(t: T): F[Long]

  def list(t: T): F[List[T]] =
    for {
      children ← children(t)
    } yield
      children.toList

  def children(t: T): F[Iterator[T]]
}

object Metadata {
  trait syntax {
    implicit def makePathMetadataOps[F[_], T](t: T): Ops[F, T] = new Ops(t)
  }

  final class Ops[F[_], T](val t: T) extends AnyVal {
    def size(implicit metadata: Metadata[F, T]): F[Long] = metadata.size(t)
    def list(implicit metadata: Metadata[F, T]): F[List[T]] = metadata.list(t)
    def children(implicit metadata: Metadata[F, T]): F[Iterator[T]] = metadata.children(t)
  }
}

trait Read[F[_], T]
  extends Path[F, T] {

  def bytes(t: T, start: Long, size: Int): F[Array[Byte]]

  def read(t: T): F[Array[Byte]]

  def string(t: T)(implicit charset: Charset): F[String] = read(t).map(new String(_, charset))
  def string(t: T, start: Long, size: Int)(implicit charset: Charset): F[String] = bytes(t, start, size).map(new String(_, charset))

  def json[A](t: T)(implicit d: Decoder[A]): F[A] =
    string(t)
      .map[Either[Throwable, A]] {
        decode[A](_)
      }
      .rethrow
}

object Read {
  trait syntax {
    implicit def makePathReadOps[F[_], T](t: T): Ops[F, T] = new Ops(t)
  }

  final class Ops[F[_], T](val t: T) extends AnyVal {
    def bytes(start: Long, size: Int)(implicit read: Read[F, T]): F[Array[Byte]] = read.bytes(t, start, size)
    def read(implicit read: Read[F, T]): F[Array[Byte]] = read.read(t)

    def string(implicit read: Read[F, T], charset: Charset): F[String] = read.string(t)
    def string(start: Long, size: Int)(implicit read: Read[F, T], charset: Charset): F[String] = read.string(t, start, size)

    def json[A](implicit d: Decoder[A], read: Read[F, T]): F[A] = read.json(t)
  }

  trait BlockCaching[F[_], T]
    extends Read[F, T]
       with LazyLogging {

    val cachingConfig: Config
    lazy val caching.Config(blockSize, maximumSize, maxNumBlocks) = cachingConfig

    private val blocks = ConcurrentLRUMap[Long, Array[Byte]](maxNumBlocks)

    def getBlock(t: T, idx: Long): F[Array[Byte]] =
      blocks
        .get(idx)
        .fold {
          val start = idx * blockSize
          logger.debug(s"fetching block $idx")
          bytes(t, start, blockSize).map {
            block ⇒
              blocks.put(idx, block)
              block
          }
        } {
          _.pure[F]
        }

    def blocks(t: T, from: Int = 0): F[List[Array[Byte]]] =
      getBlock(t, from)
      .flatMap {
        head ⇒
          if (head.length == blockSize)
            blocks(t, from + 1).map { head :: _ }
          else {
            logger.debug(s"${uri(t)}: got last block ($from; ${head.length})")
            (head :: Nil).pure[F]
          }
      }

    override def read(t: T): F[Array[Byte]] =
      blocks(t)
        .map {
          case block :: Nil ⇒ block
          case blocks ⇒
            val bytes = Array.newBuilder[Byte]
            blocks.foreach { bytes ++= _ }
            bytes.result()
        }
  }
}

trait Write[F[_], T]
  extends     Path[F, T]
     with Metadata[F, T] {

  def write(t: T, s: String)(implicit charset: Charset): F[Unit] = write(t, s.getBytes(charset))

  def write(t: T, bytes: Array[Byte]): F[Unit] =
    for {
      os ← outputStream(t: T)
    } yield {
      os.write(bytes)
      os.close()
    }

  def outputStream(t: T): F[OutputStream]

  def delete(t: T): F[Unit]
  def delete(t: T, recursive: Boolean = false): F[Unit] =
    if (recursive) {
      import cats.implicits.catsStdInstancesForList
      for {
        children ← children(t)
        _ ←
          children
            .map { delete(_, recursive = true) }
            .toList
            .sequence
        _ ← delete(t)
      } yield
        ()
    } else
      delete(t)
}

object Write {
  trait syntax {
    implicit def makePathWriteOps[F[_], T](t: T): Ops[F, T] = new Ops(t)
  }

  final class Ops[F[_], T](val t: T) extends AnyVal {
    def write(s: String)(implicit write: Write[F, T], charset: Charset): F[Unit] = write.write(t, s)

    def write(bytes: Array[Byte])(implicit write: Write[F, T]): F[Unit] = write.write(t, bytes)

    def outputStream(implicit write: Write[F, T]): F[OutputStream] = write.outputStream(t)

    def delete(implicit write: Write[F, T]): F[Unit] = write.delete(t)
    def delete(recursive: Boolean = false)(implicit write: Write[F, T]): F[Unit] = write.delete(t, recursive = recursive)
  }
}
