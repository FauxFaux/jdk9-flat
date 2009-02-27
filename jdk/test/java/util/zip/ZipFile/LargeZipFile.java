import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.zip.*;

public class LargeZipFile {
    // If true, don't delete large ZIP file created for test.
    static final boolean debug = System.getProperty("debug") != null;

    static final int DATA_LEN = 1024 * 1024;
    static final int DATA_SIZE = 8;

    static long fileSize = 3L * 1024L * 1024L * 1024L; // 3GB

    static boolean userFile = false;

    static byte[] data;
    static File largeFile;
    static String lastEntryName;

    /* args can be empty, in which case check a 3 GB file which is created for
     * this test (and then deleted).  Or it can be a number, in which case
     * that designates the size of the file that's created for this test (and
     * then deleted).  Or it can be the name of a file to use for the test, in
     * which case it is *not* deleted.  Note that in this last case, the data
     * comparison might fail.
     */
    static void realMain (String[] args) throws Throwable {
        if (args.length > 0) {
            try {
                fileSize = Long.parseLong(args[0]);
                System.out.println("Testing with file of size " + fileSize);
            } catch (NumberFormatException ex) {
                largeFile = new File(args[0]);
                if (!largeFile.exists()) {
                    throw new Exception("Specified file " + args[0] + " does not exist");
                }
                userFile = true;
                System.out.println("Testing with user-provided file " + largeFile);
            }
        }
        File testDir = null;
        if (largeFile == null) {
            testDir = new File(System.getProperty("test.scratch", "."),
                                    "LargeZip");
            if (testDir.exists()) {
                if (!testDir.delete()) {
                    throw new Exception("Cannot delete already-existing test directory");
                }
            }
            check(!testDir.exists() && testDir.mkdirs());
            largeFile = new File(testDir, "largezip.zip");
            createLargeZip();
        }

        readLargeZip();

        if (!userFile && !debug) {
            check(largeFile.delete());
            check(testDir.delete());
        }
    }

    static void createLargeZip() throws Throwable {
        int iterations = DATA_LEN / DATA_SIZE;
        ByteBuffer bb = ByteBuffer.allocate(DATA_SIZE);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (int i = 0; i < iterations; i++) {
            bb.putDouble(0, Math.random());
            baos.write(bb.array(), 0, DATA_SIZE);
        }
        data = baos.toByteArray();

        ZipOutputStream zos = new ZipOutputStream(
            new BufferedOutputStream(new FileOutputStream(largeFile)));
        long length = 0;
        while (length < fileSize) {
            ZipEntry ze = new ZipEntry("entry-" + length);
            lastEntryName = ze.getName();
            zos.putNextEntry(ze);
            zos.write(data, 0, data.length);
            zos.closeEntry();
            length = largeFile.length();
        }
        System.out.println("Last entry written is " + lastEntryName);
        zos.close();
    }

    static void readLargeZip() throws Throwable {
        ZipFile zipFile = new ZipFile(largeFile);
        ZipEntry entry = null;
        String entryName = null;
        int count = 0;
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            entry = entries.nextElement();
            entryName = entry.getName();
            count++;
        }
        System.out.println("Number of entries read: " + count);
        System.out.println("Last entry read is " + entryName);
        check(!entry.isDirectory());
        if (check(entryName.equals(lastEntryName))) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            InputStream is = zipFile.getInputStream(entry);
            byte buf[] = new byte[4096];
            int len;
            while ((len = is.read(buf)) >= 0) {
                baos.write(buf, 0, len);
            }
            baos.close();
            is.close();
            check(Arrays.equals(data, baos.toByteArray()));
        }
        try {
          zipFile.close();
        } catch (IOException ioe) {/* what can you do */ }
    }

    //--------------------- Infrastructure ---------------------------
    static volatile int passed = 0, failed = 0;
    static void pass() {passed++;}
    static void pass(String msg) {System.out.println(msg); passed++;}
    static void fail() {failed++; Thread.dumpStack();}
    static void fail(String msg) {System.out.println(msg); fail();}
    static void unexpected(Throwable t) {failed++; t.printStackTrace();}
    static void unexpected(Throwable t, String msg) {
        System.out.println(msg); failed++; t.printStackTrace();}
    static boolean check(boolean cond) {if (cond) pass(); else fail(); return cond;}
    static void equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) pass();
        else fail(x + " not equal to " + y);}
    public static void main(String[] args) throws Throwable {
        try {realMain(args);} catch (Throwable t) {unexpected(t);}
        System.out.println("\nPassed = " + passed + " failed = " + failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
}

