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

import gorsat.Commands.{Analysis, CommandParseUtilities, RowHeader}
import gorsat.parser.ReplaceCvp
import org.gorpipe.exceptions.{GorDataException, GorParsingException}
import org.gorpipe.gor.model.Row
import org.gorpipe.gor.session.GorContext

case class ReplaceAnalysis(context: GorContext, executeNor: Boolean, paramString: String, header: String,
                           columns: Array[Int]) extends Analysis with Expressions
{
  // We may have multiple expressions, comma separated
  var exprSrc: Array[String] = CommandParseUtilities.quoteSafeSplit(paramString, ',')

  var columnsToReplace: Array[Int] = columns

  if(exprSrc.length == 1) {
    columnsToReplace = columns.sorted
  } else if(exprSrc.length == columns.length) {
    val (cols, exp) = columns.zip(exprSrc).sorted.unzip
    columnsToReplace = cols
    exprSrc = exp
  } else {
    throw new GorParsingException("Number of expressions must match the number of columns")
  }

  override def isTypeInformationNeeded: Boolean = true
  override def isTypeInformationMaintained: Boolean = true

  override def setRowHeader(incomingHeader: RowHeader): Unit = {
    if(incomingHeader == null || incomingHeader.isMissingTypes) return

    // todo: Once header is passed safely through remove this
    rowHeader = RowHeader(header.split('\t'), incomingHeader.columnTypes)

    prepareExpressions(exprSrc.length, context, executeNor)
    compileExpressions(rowHeader, exprSrc, "REPLACE", "")

    if(pipeTo != null) {
      // The column types may change
      val columnTypes = rowHeader.columnTypes.clone()

      columnsToReplace.indices.foreach(i => {
        val exIx = if(i > expressionTypes.length - 1) 0 else i
        columnTypes(columnsToReplace(i)) = expressionTypes(exIx).toString
      })

      pipeTo.setRowHeader(RowHeader(rowHeader.columnNames, columnTypes))
    }
  }

  override def process(r: Row) {
    val cvp = ReplaceCvp(r)
    try {
       val columnValues = columnsToReplace.indices.map(i => {
         cvp.replaceCol = columnsToReplace(i)
         val exIx = if(i > expressions.length - 1) 0 else i
         evalFunction(cvp, expressions(exIx), expressionTypes(exIx))
       })
      r.setColumns(columnsToReplace, columnValues.toArray)
    } catch {
      case e: GorParsingException =>
        val msg = s"Error in step: REPLACE $paramString\n${e.getMessage}"
        throw new GorDataException(msg, -1, header, r.getAllCols.toString, e)
    }
    super.process(r)
  }

  override def finish(): Unit = {
    closeExpressions()
  }
}
