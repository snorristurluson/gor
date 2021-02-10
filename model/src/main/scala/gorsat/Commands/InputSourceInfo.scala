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

package gorsat.Commands

import gorsat.Commands.CommandParseUtilities.validateCommandArguments
import org.gorpipe.exceptions.{GorParsingException, GorSystemException}
import org.gorpipe.gor.session.GorContext

case class InputSourceInfo(name:String,
                           commandArguments:CommandArguments,
                           isNorCommand: Boolean = false) {

  def init(context: GorContext, header: String, argString: String, args: Array[String]) : InputSourceParsingResult = {
    // Test if gor pipe session is valid, if not throw exception
    if (context == null) {
      throw new GorSystemException("Gor context cannot be null", null)
    }
    if (context.getSession == null) {
      throw new GorSystemException("Gor Pipe Session cannot be null", null)
    }

    val (arguments, illegalArguments) = validateCommandArguments(args, commandArguments)

    if (!commandArguments.ignoreIllegalArguments && illegalArguments.length > 0) {
      // We have invalid arguments and need to throw exception
      throw new GorParsingException(s"Invalid arguments in $name. Argument(s) ${illegalArguments.mkString(",")} not a part of this command")
    }

    if (commandArguments.minimumNumberOfArguments != -1 && arguments.length < commandArguments.minimumNumberOfArguments) {
      throw new GorParsingException(s"$name: Minimum number of arguments not reached. Requires at least ${commandArguments.minimumNumberOfArguments} argument(s).")
    }

    if (commandArguments.maximumNumberOfArguments != -1 && arguments.length > commandArguments.maximumNumberOfArguments) {
      throw new GorParsingException(s"$name: Maximum number of arguments reached. No more than ${commandArguments.maximumNumberOfArguments} argument(s) allowed.")
    }

    processArguments(context, argString, arguments, args)
  }

  /* THis method is allowed to return null pipeStep and header. In that case the pipestep is not used. This will not result in an error */
  def processArguments(context: GorContext, argString: String, inputs: Array[String], options : Array[String]) : InputSourceParsingResult = { throw new NotImplementedError() }

}
