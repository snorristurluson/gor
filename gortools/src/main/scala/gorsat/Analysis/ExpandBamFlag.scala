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

package gorsat.Analysis

import gorsat.Commands.Analysis
import org.gorpipe.gor.model.Row

case class ExpandBamFlag(flagCol : Int) extends Analysis {
  val extraCols = new StringBuilder(21)
  extraCols.setLength(21)
  var i = 1
  while (i < 21) { extraCols.setCharAt(i,'\t'); i += 2 }

  override def process(r : Row) {
    var i = 0
    while (i < 21) {
      extraCols.setCharAt(i,'0'); i += 2
    }

    val Flag = r.colAsInt(flagCol)
    var mask = 1
    i = 0
    while (i < 21) {
      if ((Flag & mask) > 0) extraCols.setCharAt(i,'1'); i += 2; mask = mask * 2
    }
    super.process(r.rowWithAddedColumn(extraCols))
  }
}
