package com.obdobion;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;

import org.junit.Assert;

import com.obdobion.funnel.AppContext;
import com.obdobion.funnel.columns.OutputFormatHelper;
import com.obdobion.funnel.orderby.KeyContext;

/**
 * @author Chris DeGreef
 *
 */
public class Helper
{

    static final File          workDir = new File("target");
    public static final String DEBUG   = "ON";

    static public void compare(final File file, final List<String> expectedLines)
            throws IOException
    {
        try (final BufferedReader sorted = new BufferedReader(new FileReader(file)))
        {
            for (final String expected : expectedLines)
            {
                final String actual = sorted.readLine();
                Assert.assertEquals(expected, actual);
            }
        }
    }

    static public void compareFixed(final File file, final List<String> expectedData)
            throws IOException
    {
        try (final BufferedReader sorted = new BufferedReader(new FileReader(file)))
        {
            final char[] foundChar = new char[1];
            for (final String aRow : expectedData)
                for (final byte expected : aRow.getBytes())
                {
                    Assert.assertEquals("characters read", 1, sorted.read(foundChar));
                    Assert.assertEquals(expected, (byte) foundChar[0]);
                }
        }
    }

    static public void compareFixed(final File file, final String expectedData)
            throws IOException
    {
        try (final BufferedReader sorted = new BufferedReader(new FileReader(file)))
        {
            final char[] foundChar = new char[1];
            for (final byte expected : expectedData.getBytes())
            {
                Assert.assertEquals("characters read", 1, sorted.read(foundChar));
                Assert.assertEquals(expected, (byte) foundChar[0]);
            }
        }
    }

    static public AppContext config() throws IOException, ParseException
    {
        return new AppContext(System.getProperty("user.dir"));
    }

    static public File createFixedUnsortedFile(
            final String prefix,
            final List<String> lines,
            final int rowLength)
                    throws IOException
    {
        final File file = File.createTempFile(prefix + ".", ".in", workDir);
        try (final BufferedWriter out = new BufferedWriter(new FileWriter(file)))
        {
            for (int idx = 0; idx < lines.size(); idx++)
            {
                final String line = lines.get(idx);
                out.write(line);
                for (int fill = line.length(); fill < rowLength; fill++)
                    out.write(" ");
            }
        }
        return file;
    }

    static public File createUnsortedFile(final List<String> lines) throws IOException
    {
        return createUnsortedFile("funnel", lines, true);
    }

    static public File createUnsortedFile(final String prefix, final List<String> lines) throws IOException
    {
        return createUnsortedFile(prefix, lines, true);
    }

    static public File createUnsortedFile(
            final String prefix,
            final List<String> lines,
            final boolean includeTrailingLineTerminator)
                    throws IOException
    {
        final File file = File.createTempFile(prefix + ".", ".in", workDir);
        try (final BufferedWriter out = new BufferedWriter(new FileWriter(file)))
        {
            for (int idx = 0; idx < lines.size(); idx++)
            {
                final int lengthToWrite = OutputFormatHelper.lengthToWrite(lines.get(idx).getBytes(), 0,
                        lines.get(idx).length(), true);

                final String line = lines.get(idx);
                if (idx > 0)
                    out.newLine();
                out.write(line, 0, lengthToWrite);
            }
            if (includeTrailingLineTerminator)
                out.newLine();
        }
        return file;
    }

    static public KeyContext dummyKeyContext(final String rawBytes)
    {
        final KeyContext kx = new KeyContext();
        kx.key = new byte[255];
        kx.keyLength = 0;
        kx.rawRecordBytes = new byte[1][];
        kx.rawRecordBytes[0] = rawBytes.getBytes();
        kx.recordNumber = 0;
        return kx;
    }

    public static void initializeFor(final String testCaseName)
    {
        System.out.println();
        System.out.println(testCaseName);
    }

    public static String myDataCSVFileName()
    {
        return "./src/examples/data/MyDataCSV.in";
    }

    public static File outFile(final String testName)
    {
        return new File(outFileName(testName));
    }

    public static String outFileName(final String jUnitTestName)
    {
        return workDir + "\\" + jUnitTestName + ".out";
    }

    static public File outFileWhenInIsSysin() throws IOException
    {
        return File.createTempFile("funnel.", ".out", workDir);
    }

    static public String testName()
    {
        final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
        return ste[2].getMethodName();
    }
}
