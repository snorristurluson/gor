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

package org.gorpipe.gor.cli.manager;

import org.gorpipe.gor.manager.TableManager;
import org.gorpipe.gor.table.BaseTable;
import org.gorpipe.gor.table.lock.TableLock;
import picocli.CommandLine;

import java.time.Duration;

@SuppressWarnings("squid:S106")
@CommandLine.Command(name = "readlock",
        description="Test read lock with lock name and lock period.",
        header="Test read lock.")
public class TestReadLockCommand extends ManagerOptions implements Runnable{

    @CommandLine.Parameters(index = "1",
            arity = "1",
            paramLabel = "LOCKNAME",
            description = "Lock name to test.")
    String lockName;

    @CommandLine.Parameters(index = "2",
            arity = "1",
            paramLabel = "PERIOD",
            description = "Lock duration in milliseconds")
    long period;

    @Override
    public void run() {
        Duration lockTimeoutDuration = Duration.ofSeconds(lockTimeout);
        TableManager tm = TableManager.newBuilder().useHistory(!nohistory).lockTimeout(lockTimeoutDuration).build();
        BaseTable table = tm.initTable(dictionaryFile.toPath());

        try (TableLock lock = TableLock.acquireRead(tm.getLockType(), table, lockName, lockTimeoutDuration)) {
            Thread.sleep(period);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
