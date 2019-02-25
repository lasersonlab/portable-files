package org.lasersonlab.io

import java.io.IOException

import org.lasersonlab.files.Uri

case class FileNotFoundException(path: Uri) extends IOException
