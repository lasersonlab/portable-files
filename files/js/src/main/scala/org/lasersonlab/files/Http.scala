package org.lasersonlab.files

import java.net.URI

import org.lasersonlab.files.caching.Config
import org.lasersonlab.files.http.{ BrowserHttp, Defaults, NodeHttp }

trait Http extends Uri
object Http {
  def apply(uri: URI)(
    implicit
    config: Config,
    defaults: Defaults,
    httpConfig: http.Config
  ): Http =
    if (js.node_?)
      NodeHttp(uri)
    else
      BrowserHttp(uri)
}
