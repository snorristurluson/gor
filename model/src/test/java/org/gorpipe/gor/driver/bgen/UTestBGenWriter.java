package org.gorpipe.gor.driver.bgen;

import org.apache.commons.io.FileUtils;
import org.gorpipe.model.gor.RowObj;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

public class UTestBGenWriter {
    private static File tmpDir;

    @BeforeClass
    public static void setup() throws IOException {
        tmpDir = Files.createTempDirectory("bgenWriterTest").toFile();
    }

    @AfterClass
    public static void tearDown() throws IOException {
        FileUtils.deleteDirectory(tmpDir);
    }

    @Test
    public void test_basic() throws Exception {
        final String file = new File(tmpDir, "test_basic.bgen").getAbsolutePath();
        final BGenWriter<HardCallDataBlockFactory> writer = new BGenWriter<>(file, new HardCallDataBlockFactory(), 2, 3, -1, -1, 4);
        writer.write(RowObj.apply("chr1\t1\tA\tC\t0123"));
        writer.write(RowObj.apply("chr1\t2\tA\tC\t0123"));
        writer.close();

        final SimpleBGenDriver bGenDriver = new SimpleBGenDriver(file);
        Assert.assertEquals(20, bGenDriver.offset);
        Assert.assertEquals(20, bGenDriver.headerLength);
        Assert.assertEquals(2, bGenDriver.numberOfVariants);
        Assert.assertEquals(4, bGenDriver.numberOfSamples);
        Assert.assertEquals(2, bGenDriver.layOut);
        Assert.assertEquals(1, bGenDriver.compressionType);
        Assert.assertTrue(bGenDriver.noSampleIdentifiers);
        //Header block validated


        final Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file + ".bgi");
        final ResultSet rs = connection.createStatement().executeQuery("SELECT chromosome,position,file_start_position,size_in_bytes,number_of_alleles,reference_allele,alternative_alleles FROM Variant ORDER BY chromosome, position");

        Assert.assertTrue(bGenDriver.hasMore());
        Assert.assertTrue(rs.next());

        final SimpleBGenDriver.BGenBlock bGenBlock1 = bGenDriver.nextBlock();
        Assert.assertEquals(bGenBlock1.chr, rs.getString(1));
        Assert.assertEquals(bGenBlock1.pos, rs.getInt(2));
        Assert.assertEquals(bGenBlock1.offsetInFile, rs.getLong(3));
        Assert.assertEquals(bGenBlock1.sizeInBytes, rs.getLong(4));
        Assert.assertEquals(bGenBlock1.allCount, rs.getInt(5));
        Assert.assertEquals("A,C", String.join(",", bGenBlock1.alleles));

        Assert.assertTrue(bGenDriver.hasMore());
        Assert.assertTrue(rs.next());

        final SimpleBGenDriver.BGenBlock bGenBlock2 = bGenDriver.nextBlock();
        Assert.assertEquals(bGenBlock2.chr, rs.getString(1));
        Assert.assertEquals(bGenBlock2.pos, rs.getInt(2));
        Assert.assertEquals(bGenBlock2.offsetInFile, rs.getLong(3));
        Assert.assertEquals(bGenBlock2.sizeInBytes, rs.getLong(4));
        Assert.assertEquals(bGenBlock2.allCount, rs.getInt(5));
        Assert.assertEquals("A,C", String.join(",", bGenBlock2.alleles));

        Assert.assertFalse(bGenDriver.hasMore());
        Assert.assertFalse(rs.next());
    }
}
