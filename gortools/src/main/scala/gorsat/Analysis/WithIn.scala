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
import org.gorpipe.model.gor.RowObj

case class WithIn(chr: String, start: Int, stop: Int) extends Analysis {
  val refRow = RowObj(chr, stop, "")

  override def process(r: Row) {
    // check r.pos == 0 to allow lines through produced by group chrom (hence outside of wihtin boundary)
    if( r.pos == 0 ) super.process(r)
    else if (refRow atPriorPos r) reportWantsNoMore
    else if (chr.equals(r.chr) && r.pos >= start) {
      super.process(r)
    }
  }
}
