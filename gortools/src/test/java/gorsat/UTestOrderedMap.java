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

package gorsat;

import org.gorpipe.exceptions.GorParsingException;
import org.gorpipe.test.utils.FileTestUtils;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

public class UTestOrderedMap {
    private static File leftFile;
    private static File rightFile;
    private static File rightFileMixedCase;
    private static File rightFileNoHeader;
    private static File rightFileNoHashInHeader;
    private static File rightFileMissingValues;
    private static File rightFileEmptyColumns;

    @ClassRule
    public static TemporaryFolder workDir = new TemporaryFolder();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setupTest() throws IOException {
        leftFile = FileTestUtils.createTempFile(workDir.getRoot(), "left.tsv",
                "#First\tSecond\tThird\n" +
                        "a\t1\ta1\n" +
                        "b\t2\tb2\n" +
                        "b\t2+\tb2+\n" +
                        "c\t3\tc3");

        rightFile = FileTestUtils.createTempFile(workDir.getRoot(), "right.tsv",
                "#A\tB\tC\n" +
                        "a\tthis is a1\ta1\n" +
                        "b\tthis is b2\tb2\n" +
                        "b\tthis is also b2\tb2+\n" +
                        "c\tthis is c3\tc3\n");

        rightFileMissingValues = FileTestUtils.createTempFile(workDir.getRoot(), "rightMissingValues.tsv",
                "#A\tB\tC\n" +
                        "b\tthis is b2\tb2\n" +
                        "b\tthis is also b2\tb2+\n");

        rightFileMixedCase = FileTestUtils.createTempFile(workDir.getRoot(), "rightMixedCase.tsv",
                "#A\tB\tC\n" +
                        "a\tthis is a1\ta1\n" +
                        "B\tthis is b2\tb2\n" +
                        "b\tthis is also b2\tb2+\n" +
                        "C\tthis is c3\tc3\n");

        rightFileNoHeader = FileTestUtils.createTempFile(workDir.getRoot(), "rightNoHeader.tsv",
                        "a\tthis is a1\ta1\n" +
                        "b\tthis is b2\tb2\n" +
                        "b\tthis is also b2\tb2+\n" +
                        "c\tthis is c3\tc3\n");

        rightFileNoHashInHeader = FileTestUtils.createTempFile(workDir.getRoot(), "rightNoHashInHeader.tsv",
                "A\tB\tC\n" +
                        "a\tthis is a1\ta1\n" +
                        "b\tthis is b2\tb2\n" +
                        "b\tthis is also b2\tb2+\n" +
                        "c\tthis is c3\tc3\n");

        rightFileEmptyColumns = FileTestUtils.createTempFile(workDir.getRoot(), "rightEmptyColumns.tsv",
                "#A\tB\tC\n" +
                        "a\tthis is a1\ta1\n" +
                        "b\t\tb2\n" +
                        "b\tthis is also b2\t\n" +
                        "c\tthis is c3\tc3\n");

    }

    @Test
    public void simple() {
        String query = String.format("nor %s | map -c First -ordered %s", leftFile.getAbsoluteFile(), rightFile.getAbsoluteFile());
        String result = TestUtils.runGorPipe(query);
        String expected = "ChromNOR\tPosNOR\tFirst\tSecond\tThird\tB\tC\n" +
                "chrN\t0\ta\t1\ta1\tthis is a1\ta1\n" +
                "chrN\t0\tb\t2\tb2\tthis is b2,this is also b2\tb2,b2+\n" +
                "chrN\t0\tb\t2+\tb2+\tthis is b2,this is also b2\tb2,b2+\n" +
                "chrN\t0\tc\t3\tc3\tthis is c3\tc3\n";
        Assert.assertEquals(expected, result);
    }

    @Test
    public void simpleMissingValues() {
        String query = String.format("nor %s | map -c First -ordered %s", leftFile.getAbsoluteFile(), rightFileMissingValues.getAbsoluteFile());
        String result = TestUtils.runGorPipe(query);
        String expected = "ChromNOR\tPosNOR\tFirst\tSecond\tThird\tB\tC\n" +
                "chrN\t0\tb\t2\tb2\tthis is b2,this is also b2\tb2,b2+\n" +
                "chrN\t0\tb\t2+\tb2+\tthis is b2,this is also b2\tb2,b2+\n";
        Assert.assertEquals(expected, result);
    }

    @Test
    public void simpleEmptyColumns() {
        String query = String.format("nor %s | map -c First -ordered %s", leftFile.getAbsoluteFile(), rightFileEmptyColumns.getAbsoluteFile());
        String result = TestUtils.runGorPipe(query);
        String expected = "ChromNOR\tPosNOR\tFirst\tSecond\tThird\tB\tC\n" +
                "chrN\t0\ta\t1\ta1\tthis is a1\ta1\n" +
                "chrN\t0\tb\t2\tb2\t,this is also b2\tb2,\n" +
                "chrN\t0\tb\t2+\tb2+\t,this is also b2\tb2,\n" +
                "chrN\t0\tc\t3\tc3\tthis is c3\tc3\n";
        Assert.assertEquals(expected, result);
    }

    @Test
    public void simpleEmptyColumnsFilteredOut() {
        String query = String.format("nor %s | map -c First -e -ordered %s", leftFile.getAbsoluteFile(), rightFileEmptyColumns.getAbsoluteFile());
        String result = TestUtils.runGorPipe(query);
        String expected = "ChromNOR\tPosNOR\tFirst\tSecond\tThird\tB\tC\n" +
                "chrN\t0\ta\t1\ta1\tthis is a1\ta1\n" +
                "chrN\t0\tb\t2\tb2\tthis is also b2\tb2\n" +
                "chrN\t0\tb\t2+\tb2+\tthis is also b2\tb2\n" +
                "chrN\t0\tc\t3\tc3\tthis is c3\tc3\n";
        Assert.assertEquals(expected, result);
    }

    @Test
    public void simpleEmptyValue() {
        String query = String.format("nor %s | map -c First -m NA -ordered %s", leftFile.getAbsoluteFile(), rightFileMissingValues.getAbsoluteFile());
        String result = TestUtils.runGorPipe(query);
        String expected = "ChromNOR\tPosNOR\tFirst\tSecond\tThird\tB\tC\n" +
                "chrN\t0\ta\t1\ta1\tNA\tNA\n" +
                "chrN\t0\tb\t2\tb2\tthis is b2,this is also b2\tb2,b2+\n" +
                "chrN\t0\tb\t2+\tb2+\tthis is b2,this is also b2\tb2,b2+\n" +
                "chrN\t0\tc\t3\tc3\tNA\tNA\n";
        Assert.assertEquals(expected, result);
    }

    @Test
    public void simpleMixedCase() {
        String query = String.format("nor %s | map -c First -ordered %s", leftFile.getAbsoluteFile(), rightFileMixedCase.getAbsoluteFile());
        String result = TestUtils.runGorPipe(query);
        String expected = "ChromNOR\tPosNOR\tFirst\tSecond\tThird\tB\tC\n" +
                "chrN\t0\ta\t1\ta1\tthis is a1\ta1\n" +
                "chrN\t0\tb\t2\tb2\tthis is also b2\tb2+\n" +
                "chrN\t0\tb\t2+\tb2+\tthis is also b2\tb2+\n";
        Assert.assertEquals(expected, result);
    }

    @Test
    public void simpleMixedCaseWithCisFlag() {
        String query = String.format("nor %s | map -c First -ordered -cis %s", leftFile.getAbsoluteFile(), rightFileMixedCase.getAbsoluteFile());
        String result = TestUtils.runGorPipe(query);
        String expected = "ChromNOR\tPosNOR\tFirst\tSecond\tThird\tB\tC\n" +
                "chrN\t0\ta\t1\ta1\tthis is a1\ta1\n" +
                "chrN\t0\tb\t2\tb2\tthis is b2,this is also b2\tb2,b2+\n" +
                "chrN\t0\tb\t2+\tb2+\tthis is b2,this is also b2\tb2,b2+\n" +
                "chrN\t0\tc\t3\tc3\tthis is c3\tc3\n";
        Assert.assertEquals(expected, result);
    }

    @Test
    public void simpleNoHeader() {
        String query = String.format("nor %s | map -c First -ordered %s", leftFile.getAbsoluteFile(), rightFileNoHeader.getAbsoluteFile());
        String result = TestUtils.runGorPipe(query);
        String expected = "ChromNOR\tPosNOR\tFirst\tSecond\tThird\tcol2\tcol3\n" +
                "chrN\t0\ta\t1\ta1\tthis is a1\ta1\n" +
                "chrN\t0\tb\t2\tb2\tthis is b2,this is also b2\tb2,b2+\n" +
                "chrN\t0\tb\t2+\tb2+\tthis is b2,this is also b2\tb2,b2+\n" +
                "chrN\t0\tc\t3\tc3\tthis is c3\tc3\n";
        Assert.assertEquals(expected, result);
    }

    @Test
    public void simpleNoHashInHeader() {
        String query = String.format("nor %s | map -c First -ordered %s", leftFile.getAbsoluteFile(), rightFileNoHashInHeader.getAbsoluteFile());
        String result = TestUtils.runGorPipe(query);
        String expected = "ChromNOR\tPosNOR\tFirst\tSecond\tThird\tcol2\tcol3\n" +
                "chrN\t0\ta\t1\ta1\tthis is a1\ta1\n" +
                "chrN\t0\tb\t2\tb2\tthis is b2,this is also b2\tb2,b2+\n" +
                "chrN\t0\tb\t2+\tb2+\tthis is b2,this is also b2\tb2,b2+\n" +
                "chrN\t0\tc\t3\tc3\tthis is c3\tc3\n";
        Assert.assertEquals(expected, result);
    }

    @Test
    public void inSet() {
        String query = String.format("nor %s | inset -c First -ordered %s", leftFile.getAbsoluteFile(), rightFileMissingValues.getAbsoluteFile());
        String result = TestUtils.runGorPipe(query);
        String expected = "ChromNOR\tPosNOR\tFirst\tSecond\tThird\n" +
                "chrN\t0\tb\t2\tb2\n" +
                "chrN\t0\tb\t2+\tb2+\n";
        Assert.assertEquals(expected, result);
    }

    @Test
    public void inSetReturnMissing() {
        String query = String.format("nor %s | inset -c First -m NA -ordered %s", leftFile.getAbsoluteFile(), rightFileMissingValues.getAbsoluteFile());
        String result = TestUtils.runGorPipe(query);
        String expected = "ChromNOR\tPosNOR\tFirst\tSecond\tThird\n" +
                "chrN\t0\tb\t2\tb2\n" +
                "chrN\t0\tb\t2+\tb2+\n";
        Assert.assertEquals(expected, result);
    }

    @Test
    public void inSetMembershipColumn() {
        String query = String.format("nor %s | inset -c First -b -ordered %s", leftFile.getAbsoluteFile(), rightFileMissingValues.getAbsoluteFile());
        String result = TestUtils.runGorPipe(query);
        String expected = "ChromNOR\tPosNOR\tFirst\tSecond\tThird\tinSet\n" +
                "chrN\t0\ta\t1\ta1\t0\n" +
                "chrN\t0\tb\t2\tb2\t1\n" +
                "chrN\t0\tb\t2+\tb2+\t1\n" +
                "chrN\t0\tc\t3\tc3\t0\n";
        Assert.assertEquals(expected, result);
    }

    @Test
    public void notInSet() {
        String query = String.format("nor %s | inset -c First -not -ordered %s", leftFile.getAbsoluteFile(), rightFileMissingValues.getAbsoluteFile());
        String result = TestUtils.runGorPipe(query);
        String expected = "ChromNOR\tPosNOR\tFirst\tSecond\tThird\n" +
                "chrN\t0\ta\t1\ta1\n" +
                "chrN\t0\tc\t3\tc3\n";
        Assert.assertEquals(expected, result);
    }

    @Test
    public void notInSetMembershipColumn() {
        String query = String.format("nor %s | inset -c First -not -b -ordered %s", leftFile.getAbsoluteFile(), rightFileMissingValues.getAbsoluteFile());
        String result = TestUtils.runGorPipe(query);
        String expected = "ChromNOR\tPosNOR\tFirst\tSecond\tThird\tinSet\n" +
                "chrN\t0\ta\t1\ta1\t1\n" +
                "chrN\t0\tb\t2\tb2\t0\n" +
                "chrN\t0\tb\t2+\tb2+\t0\n" +
                "chrN\t0\tc\t3\tc3\t1\n";
        Assert.assertEquals(expected, result);
    }

    @Test
    public void multi() {
        String query = String.format("nor %s | multimap -c First -ordered %s", leftFile.getAbsoluteFile(), rightFile.getAbsoluteFile());
        String result = TestUtils.runGorPipe(query);
        String expected = "ChromNOR\tPosNOR\tFirst\tSecond\tThird\tB\tC\n" +
                "chrN\t0\ta\t1\ta1\tthis is a1\ta1\n" +
                "chrN\t0\tb\t2\tb2\tthis is b2\tb2\n" +
                "chrN\t0\tb\t2\tb2\tthis is also b2\tb2+\n" +
                "chrN\t0\tb\t2+\tb2+\tthis is b2\tb2\n" +
                "chrN\t0\tb\t2+\tb2+\tthis is also b2\tb2+\n" +
                "chrN\t0\tc\t3\tc3\tthis is c3\tc3\n";
        Assert.assertEquals(expected, result);
    }

    @Test
    public void cartesian() {
        String query = String.format("nor %s | map -c First -n B,C -cartesian -ordered %s", leftFile.getAbsoluteFile(), rightFile.getAbsoluteFile());
        thrown.expect(GorParsingException.class);
        String result = TestUtils.runGorPipe(query);
    }

    @Test
    public void cartesianMulti() {
        String query = String.format("nor %s | multimap -c First -n B,C -cartesian -ordered %s", leftFile.getAbsoluteFile(), rightFile.getAbsoluteFile());
        thrown.expect(GorParsingException.class);
        String result = TestUtils.runGorPipe(query);
    }
}
