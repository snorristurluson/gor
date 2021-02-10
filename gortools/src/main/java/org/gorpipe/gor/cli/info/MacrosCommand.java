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

package org.gorpipe.gor.cli.info;

import gorsat.process.GorPipeMacros;
import org.gorpipe.gor.cli.HelpOptions;
import picocli.CommandLine;

@SuppressWarnings("squid:S106")
@CommandLine.Command(name = "macros",
        header = "List gor macros",
        description="List all macros in gor and their options")
public class MacrosCommand extends HelpOptions implements  Runnable{

    @Override
    public void run() {
        try {
            GorPipeMacros.register();
            System.out.print(GorPipeMacros.getMacroInfoTable());
        } catch (Exception e) {
            System.err.println("Error: \n");
            System.err.println(e.getMessage());
        }
    }
}
