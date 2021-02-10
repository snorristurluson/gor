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

package org.gorpipe.gor.cli;

import org.apache.commons.io.IOUtils;
import org.gorpipe.gor.manager.BucketManager;
import org.gorpipe.gor.manager.TableManager;
import org.gorpipe.gor.table.BaseTable;
import org.gorpipe.gor.table.BucketableTableEntry;
import org.gorpipe.gor.table.dictionary.DictionaryEntry;
import org.gorpipe.gor.table.lock.TableLock;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.gorpipe.gor.table.PathUtils.resolve;

public class GorCliManagerUtils {
    private static final Logger log = LoggerFactory.getLogger(GorCliManagerUtils.class);

    public GorCliManagerUtils() {
    }

    public void testBucketDirsHelper(TableManager man, BaseTable<DictionaryEntry> table, List<Path> bucketDirs, int fileCount) throws IOException {
        log.trace("Calling buckets dir helper with {}", bucketDirs);
        for (Path bucketDir : bucketDirs) {
            Path bucketDirFull = resolve(table.getRootPath(), bucketDir);
            if (!Files.exists(bucketDirFull)) {
                Files.createDirectories(bucketDirFull);
                bucketDirFull.toFile().deleteOnExit();
            }
        }
        List<Path> buckets = table.filter().get().stream().map(l -> l.getBucketPath()).filter(p -> p != null).distinct().collect(Collectors.toList());
        man.deleteBuckets(table.getPath(), buckets.toArray(new Path[buckets.size()]));
        BucketManager buc = new BucketManager(table);

        buc.setBucketDirs(bucketDirs);
        int bucketsCreated = buc.bucketize(BucketManager.BucketPackLevel.NO_PACKING, -1);
        Assert.assertEquals("Wrong number of buckets", fileCount / man.getBucketSize(), bucketsCreated);
        Assert.assertEquals("Not all lines bucketized", 0, table.needsBucketizing().size());
        buckets = table.filter().get().stream().map(l -> l.getBucketPath()).distinct().collect(Collectors.toList());
        List<Path> createdBucketFolders = buckets.stream().map(p -> p.getParent()).distinct().collect(Collectors.toList());
        Assert.assertEquals("Incorrect number of bucket folders", bucketDirs.size(), createdBucketFolders.size());
        Assert.assertEquals("Incorrect bucket folder(s)", new TreeSet(bucketDirs), new TreeSet(createdBucketFolders));
        for (Path bucket : buckets) {
            Assert.assertTrue("Bucket does not exists", Files.exists(table.getRootPath().resolve(bucket)));
        }
    }

    Process startGorManagerCommand(String command, String[] commandOptionsArgs, String workingDir)
            throws IOException {

        List<String> arguments = new ArrayList<String>();
        arguments.add("java");
        arguments.add("-classpath");
        arguments.add(System.getProperty("java.class.path"));
        arguments.add("-Dlogback.configurationFile=" + Paths.get("..").toFile().getAbsolutePath() + "/tests/config/logback-test.xml");
        arguments.add("org.gorpipe.gor.cli.GorCLI");
        arguments.add("manager");
        arguments.add(command);
        if (commandOptionsArgs != null) {
            arguments.addAll(Arrays.asList(commandOptionsArgs));
        }

        log.trace("Running: {}", String.join(" ", arguments));

        ProcessBuilder pb = new ProcessBuilder(arguments);
        if (workingDir != null) {
            pb.directory(new File(workingDir));
        }

        return pb.start();
    }

    String waitForProcessPlus(Process p) throws InterruptedException, IOException, ExecutionException {

        final long timeoutInS = 30;

        final StringWriter out_writer = new StringWriter();
        final StringWriter err_writer = new StringWriter();
        startProcessStreamEaters(p, out_writer, err_writer);
        boolean noTimeout = p.waitFor(timeoutInS, TimeUnit.SECONDS);

        final String processOutput = out_writer.toString();
        final String errorOutput = err_writer.toString();
        if (errorOutput != null && errorOutput.length() > 0) {
            log.warn("Process error output - ==================================== start ====================================");
            log.warn(errorOutput);
            log.warn("Process error output - ==================================== stop  ====================================");
        }

        if (noTimeout) {
            // Process did finish
            int errCode = p.exitValue();
            if (errCode != 0) {
                log.warn("Process output - ==================================== start ====================================");
                log.warn(processOutput);
                log.warn("Process output - ==================================== stop  ====================================");
                throw new ExecutionException("BaseTable manager command failed with exit code " + errCode, null);
            }
        } else {
            log.warn("Process output - ==================================== start ====================================");
            log.warn(processOutput);
            log.warn("Process output - ==================================== stop  ====================================");
            throw new ExecutionException("BaseTable manager command timed out in " + timeoutInS + " seconds", null);
        }

        return processOutput;
    }

    void startProcessStreamEaters(Process p, Writer outWriter, Writer errWriter) {
        new Thread(() -> {
            try {
                IOUtils.copy(p.getInputStream(), outWriter);
            } catch (IOException e) {
                // Ignore
            }
        }).start();
        new Thread(() -> {
            try {
                IOUtils.copy(p.getErrorStream(), errWriter);
            } catch (IOException e) {
                // Ignore
            }
        }).start();
    }

    public String executeGorManagerCommand(String command, String[] commandOptionsArgs, String workingDir, boolean sync)
            throws IOException, InterruptedException, ExecutionException {
        Process p = startGorManagerCommand(command, commandOptionsArgs, workingDir);
        if (sync) {
            return waitForProcessPlus(p);
        } else {
            return "";
        }
    }

    void waitForBucketizeToStart(BaseTable<BucketableTableEntry> table, Process p) throws InterruptedException, IOException, ExecutionException {
        long startTime = System.currentTimeMillis();
        while (true) {
            try (TableLock bucketizeLock = TableLock.acquireWrite(TableManager.DEFAULT_LOCK_TYPE, table, "bucketize", Duration.ofMillis(100))) {
                if (!bucketizeLock.isValid()) {
                    break;
                }
            }
            if (System.currentTimeMillis() - startTime > 5000) {
                log.info(waitForProcessPlus(p));
                Assert.assertTrue("Test not setup correctly, thread did not get bucketize lock, took too long.", false);
            }
            Thread.sleep(100);
        }
    }
}