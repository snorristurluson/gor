/*
 *  BEGIN_COPYRIGHT
 *
 *  Copyright (C) 2011-2013 deCODE genetics Inc.
 *  Copyright (C) 2013-2019 WuXi NextCode Inc.
 *  All Rights Reserved.
 *
 *  GORpipe is free software: you can redistribute it and/or modify
 *  it under the terms of the AFFERO GNU General Public License as published by
 *  the Free Software Foundation.
 *
 *  GORpipe is distributed "AS-IS" AND WITHOUT ANY WARRANTY OF ANY KIND,
 *  INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 *  NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR PURPOSE. See
 *  the AFFERO GNU General Public License for the complete license terms.
 *
 *  You should have received a copy of the AFFERO GNU General Public License
 *  along with GORpipe.  If not, see <http://www.gnu.org/licenses/agpl-3.0.html>
 *
 *  END_COPYRIGHT
 */

package gorsat.Outputs

import java.util.zip.Deflater
import gorsat.Commands.Output
import org.gorpipe.gor.binsearch.{GorIndexType, GorZipLexOutputStream}
import org.gorpipe.gor.model.Row

import java.nio.file.{Files, Paths}

/**
  * @param fileName Name of the file to be written.
  * @param header The header of the incoming source.
  * @param skipHeader Whether the header should be written or not.
  * @param append Whether we should write the output to the beginning or end of the file.
  * @param colcompress Whether a column compression should be used or not.
  * @param md5 Whether the md5 sum of the file's content should be written to a side file or not.
  * @param idx Whether and index file should be written.
  */
class GORzip(fileName: String, header: String = null, skipHeader: Boolean = false, append: Boolean = false, colcompress: Boolean = false, md5: Boolean = false, md5File: Boolean = true, idx: GorIndexType = GorIndexType.NONE, compressionLevel: Int = Deflater.BEST_SPEED, cardCol: String) extends Output {
  val out = new GorZipLexOutputStream(fileName, append, colcompress, md5, md5File, idx, compressionLevel)
  override def getName: String = fileName

  def setup() {
    if (cardCol != null) meta.initCardCol(cardCol, header)
    if (header != null & !skipHeader) out.setHeader(header)
  }

  def process(r: Row) {
    meta.updateRange(r)
    out.write(r)
  }

  def finish {
    out.close
    meta.setMd5(out.getMd5)
    val metapath = fileName + ".meta"
    val m = Paths.get(metapath)
    Files.writeString(m, meta.toString)
  }
}