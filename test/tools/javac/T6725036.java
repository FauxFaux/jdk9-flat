/*
 * Copyright (c) 2008, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 6725036 8016760
 * @summary javac returns incorrect value for lastModifiedTime() when
 *          source is a zip file archive
 * @library /tools/lib
 * @build ToolBox
 * @run main T6725036
 */

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.tools.*;

import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.file.RelativePath.RelativeFile;
import com.sun.tools.javac.file.ZipFileIndex;
import com.sun.tools.javac.file.ZipFileIndexArchive;
import com.sun.tools.javac.file.ZipFileIndexCache;
import com.sun.tools.javac.util.Context;

public class T6725036 {
    public static void main(String... args) throws Exception {
        new T6725036().run();
    }

    void run() throws Exception {
        RelativeFile TEST_ENTRY_NAME = new RelativeFile("java/lang/String.class");

        File testJar = createJar("test.jar", "java.lang.*");

        try (JarFile j = new JarFile(testJar)) {
            JarEntry je = j.getJarEntry(TEST_ENTRY_NAME.getPath());
            long jarEntryTime = je.getTime();

            ZipFileIndexCache zfic = ZipFileIndexCache.getSharedInstance();
            ZipFileIndex zfi = zfic.getZipFileIndex(testJar, null, false, null, false);
            long zfiTime = zfi.getLastModified(TEST_ENTRY_NAME);

            check(je, jarEntryTime, zfi + ":" + TEST_ENTRY_NAME.getPath(), zfiTime);

            Context context = new Context();
            JavacFileManager fm = new JavacFileManager(context, false, null);
            ZipFileIndexArchive zfia = new ZipFileIndexArchive(fm, zfi);
            JavaFileObject jfo =
                zfia.getFileObject(TEST_ENTRY_NAME.dirname(),
                                       TEST_ENTRY_NAME.basename());
            long jfoTime = jfo.getLastModified();

            check(je, jarEntryTime, jfo, jfoTime);

            if (errors > 0)
                throw new Exception(errors + " occurred");
        }
    }

    File createJar(String name, String... paths) throws IOException {
        JavaCompiler comp = ToolProvider.getSystemJavaCompiler();
        try (JavaFileManager fm = comp.getStandardFileManager(null, null, null)) {
            File f = new File(name);
            ToolBox tb = new ToolBox();
            tb.new JarTask(f.getPath())
                .files(fm, StandardLocation.PLATFORM_CLASS_PATH, paths)
                .run();
            return f;
        }
    }

    void check(Object ref, long refTime, Object test, long testTime) {
        if (refTime == testTime)
            return;
        System.err.println("Error: ");
        System.err.println("Expected: " + getText(ref, refTime));
        System.err.println("   Found: " + getText(test, testTime));
        errors++;
    }

    String getText(Object x, long t) {
        return String.format("%14d", t) + " (" + new Date(t) + ") from " + x;
    }

    int errors;
}
