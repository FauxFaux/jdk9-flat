/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8161906 8161596
 * @summary tests for "requires static"
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask ModuleTestBase
 * @run main RequiresStaticTest
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import toolbox.JavaTask;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.Task.OutputKind;

public class RequiresStaticTest extends ModuleTestBase {

    public static void main(String... args) throws Exception {
        RequiresStaticTest t = new RequiresStaticTest();
        t.runTests();
    }

    @Test
    public void testJavaSE_OK(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "module m { requires static java.se; }",
                "import java.awt.Frame;\n"  // in java.se
                + "class Test {\n"
                + "    Frame f;\n"
                + "}");
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        new JavacTask(tb, Task.Mode.CMDLINE)
                .files(findJavaFiles(src))
                .outdir(classes)
                .run()
                .writeAll();
    }

    @Test
    public void testJavaSE_Fail(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "module m { requires static java.se; }",
                "import com.sun.source.tree.Tree;\n" // not in java.se (in jdk.compiler)
                + "class Test {\n"
                + "    Tree t;\n"
                + "}");
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        String log = new JavacTask(tb, Task.Mode.CMDLINE)
                .options("-XDrawDiagnostics")
                .files(findJavaFiles(src))
                .outdir(classes)
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!log.contains("Test.java:1:27: compiler.err.doesnt.exist: com.sun.source.tree"))
            throw new Exception("expected output not found");
    }

    @Test
    public void testComplex_OK(Path base) throws Exception {
        Path src = getComplexSrc(base, "", "");
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        new JavacTask(tb, Task.Mode.CMDLINE)
                .options("--module-source-path", src.toString())
                .files(findJavaFiles(src))
                .outdir(classes)
                .run()
                .writeAll();
    }

    @Test
    public void testComplex_Fail(Path base) throws Exception {
        Path src = getComplexSrc(base,
                "import p5.C5; import p6.C6; import p7.C7; import p8.C8;\n",
                "C5 c5; C6 c6; C7 c7; C8 c8;\n");
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        String log = new JavacTask(tb, Task.Mode.CMDLINE)
                .options("-XDrawDiagnostics",
                        "--module-source-path", src.toString())
                .files(findJavaFiles(src))
                .outdir(classes)
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        String[] expect = {
            "C1.java:5:10: compiler.err.not.def.access.package.cant.access: p5.C5, p5",
            "C1.java:5:24: compiler.err.not.def.access.package.cant.access: p6.C6, p6",
            "C1.java:5:38: compiler.err.not.def.access.package.cant.access: p7.C7, p7",
            "C1.java:5:52: compiler.err.not.def.access.package.cant.access: p8.C8, p8",
            "C1.java:8:1: compiler.err.cant.resolve.location: kindname.class, C5, , , "
                + "(compiler.misc.location: kindname.class, p1.C1, null)",
            "C1.java:8:8: compiler.err.cant.resolve.location: kindname.class, C6, , , "
                + "(compiler.misc.location: kindname.class, p1.C1, null)",
            "C1.java:8:15: compiler.err.cant.resolve.location: kindname.class, C7, , , "
                + "(compiler.misc.location: kindname.class, p1.C1, null)",
            "C1.java:8:22: compiler.err.cant.resolve.location: kindname.class, C8, , , "
                + "(compiler.misc.location: kindname.class, p1.C1, null)"
        };

        for (String e: expect) {
            if (!log.contains(e))
                throw new Exception("expected output not found: " + e);
        }
    }

    /*
     * Set up the following module graph
     *     m1 -> m2 => m3 -=-> m4 --> m5
     *            \           /
     *              \       /
     *                v   v
     *                  m6 => m7 --> m8
     * where -> is requires, => is requires transitive, --> is requires static, -=-> is requires transitive static
     */
    Path getComplexSrc(Path base, String m1_extraImports, String m1_extraUses) throws Exception {
        Path src = base.resolve("src");

        Path src_m1 = src.resolve("m1");
        tb.writeJavaFiles(src_m1,
                "module m1 { requires m2; }",
                "package p1;\n"
                + "import p2.C2;\n"
                + "import p3.C3;\n"
                + "import p4.C4;\n"
                + m1_extraImports
                + "class C1 {\n"
                + "  C2 c2; C3 c3; C4 c4;\n"
                + m1_extraUses
                + "}\n");

        Path src_m2 = src.resolve("m2");
        tb.writeJavaFiles(src_m2,
                "module m2 {\n"
                + "  requires transitive m3;\n"
                + "  requires static m6;\n"
                + "  exports p2;\n"
                + "}",
                "package p2;\n"
                + "public class C2 {p7.C7 c7; p6.C6 c6; p4.C4 c4;}\n");

        Path src_m3 = src.resolve("m3");
        tb.writeJavaFiles(src_m3,
                "module m3 { requires transitive static m4; exports p3; }",
                "package p3;\n"
                + "public class C3 { }\n");

        Path src_m4 = src.resolve("m4");
        tb.writeJavaFiles(src_m4,
                "module m4 { requires m5; requires static m6; exports p4; }",
                "package p4;\n"
                + "public class C4 { p6.C6 c6; p7.C7 c7;}\n");

        Path src_m5 = src.resolve("m5");
        tb.writeJavaFiles(src_m5,
                "module m5 { exports p5; }",
                "package p5;\n"
                + "public class C5 { }\n");

        Path src_m6 = src.resolve("m6");
        tb.writeJavaFiles(src_m6,
                "module m6 { requires transitive m7; exports p6; }",
                "package p6;\n"
                + "public class C6 { p7.C7 c7; }\n");

        Path src_m7 = src.resolve("m7");
        tb.writeJavaFiles(src_m7,
                "module m7 { requires static m8; exports p7; }",
                "package p7;\n"
                + "public class C7 { p8.C8 c8; }\n");

        Path src_m8 = src.resolve("m8");
        tb.writeJavaFiles(src_m8,
                "module m8 { exports p8; }",
                "package p8;\n"
                        + "public class C8 { }\n");

        return src;
    }

    @Test
    public void testRequiresStatic(Path base) throws Exception {
        Path src = base.resolve("src");
        Path m1 = src.resolve("m1");
        tb.writeJavaFiles(m1,
                "module m1 { exports m1; }",
                "package m1;" +
                "public class Api { }\n");

        Path classes = base.resolve("classes");
        Path m1Classes = classes.resolve("m1");
        Files.createDirectories(m1Classes);

        new JavacTask(tb, Task.Mode.CMDLINE)
                .files(findJavaFiles(m1))
                .outdir(m1Classes)
                .run()
                .writeAll();

        Path m3 = src.resolve("m3");
        tb.writeJavaFiles(m3,
                "module m3 { requires static m1; }",
                "package m3;\n" +
                "public class Test {\n" +
                "    public static void main(String... args) {\n" +
                "        try {\n" +
                "           Class.forName(\"m1.Api\");\n" +
                "        } catch (ClassNotFoundException e) {\n" +
                "            System.err.println(\"ok\");\n" +
                "        }\n" +
                "    }\n" +
                "}",
                "package m3;\n" +
                "public class ApiUse{\n" +
                "    m1.Api api;\n" +
                "}");

        Path m3Classes = classes.resolve("m3");
        Files.createDirectories(m3Classes);

        new JavacTask(tb, Task.Mode.CMDLINE)
                .options("--module-path", m1Classes.toString())
                .files(findJavaFiles(m3))
                .outdir(m3Classes)
                .run()
                .writeAll();

        String log = new JavaTask(tb)
                .vmOptions("--module-path", m3Classes.toString(), "--add-modules", "m3")
                .className("m3.Test")
                .run()
                .writeAll()
                .getOutput(OutputKind.STDERR);

        String expected = "ok" + System.getProperty("line.separator");

        if (!expected.equals(log)) {
            throw new AssertionError("Unexpected output: " + log);
        }
    }

    @Test
    public void testRequiresTransitiveStatic(Path base) throws Exception {
        Path src = base.resolve("src");
        Path m1 = src.resolve("m1");
        tb.writeJavaFiles(m1,
                "module m1 { exports m1; }",
                "package m1;" +
                "public class Api { }\n");

        Path classes = base.resolve("classes");
        Path m1Classes = classes.resolve("m1");
        Files.createDirectories(m1Classes);

        new JavacTask(tb, Task.Mode.CMDLINE)
                .files(findJavaFiles(m1))
                .outdir(m1Classes)
                .run()
                .writeAll();

        Path m2 = src.resolve("m2");
        tb.writeJavaFiles(m2,
                "module m2 { requires transitive static m1; }");

        Path m2Classes = classes.resolve("m2");
        Files.createDirectories(m2Classes);

        new JavacTask(tb, Task.Mode.CMDLINE)
                .options("--module-path", m1Classes.toString())
                .files(findJavaFiles(m2))
                .outdir(m2Classes)
                .run()
                .writeAll();

        Path m3 = src.resolve("m3");
        tb.writeJavaFiles(m3,
                "module m3 { requires m2; }",
                "package m3;\n" +
                "public class Test {\n" +
                "    public static void main(String... args) {\n" +
                "        try {\n" +
                "           Class.forName(\"m1.Api\");\n" +
                "        } catch (ClassNotFoundException e) {\n" +
                "            System.err.println(\"ok\");\n" +
                "        }\n" +
                "    }\n" +
                "}",
                "package m3;\n" +
                "public class ApiUse{\n" +
                "    m1.Api api;\n" +
                "}");

        Path m3Classes = classes.resolve("m3");
        Files.createDirectories(m3Classes);

        new JavacTask(tb, Task.Mode.CMDLINE)
                .options("--module-path", m1Classes.toString() + File.pathSeparator + m2Classes.toString())
                .files(findJavaFiles(m3))
                .outdir(m3Classes)
                .run()
                .writeAll();

        String log = new JavaTask(tb)
                .vmOptions("--module-path", m2Classes.toString() + File.pathSeparator + m3Classes.toString(),
                           "--add-modules", "m3")
                .className("m3.Test")
                .run()
                .writeAll()
                .getOutput(OutputKind.STDERR);

        String expected = "ok" + System.getProperty("line.separator");

        if (!expected.equals(log)) {
            throw new AssertionError("Unexpected output: " + log);
        }
    }

    @Test
    public void testRequiresStaticTransitive(Path base) throws Exception {
        Path src = base.resolve("src");
        Path m1 = src.resolve("m1");
        tb.writeJavaFiles(m1,
                "module m1 { exports m1; }",
                "package m1;" +
                "public class Api { }\n");

        Path classes = base.resolve("classes");
        Path m1Classes = classes.resolve("m1");
        Files.createDirectories(m1Classes);

        new JavacTask(tb, Task.Mode.CMDLINE)
                .files(findJavaFiles(m1))
                .outdir(m1Classes)
                .run()
                .writeAll();

        Path m2 = src.resolve("m2");
        tb.writeJavaFiles(m2,
                "module m2 { requires transitive static m1; }");

        Path m2Classes = classes.resolve("m2");
        Files.createDirectories(m2Classes);

        new JavacTask(tb, Task.Mode.CMDLINE)
                .options("--module-path", m1Classes.toString())
                .files(findJavaFiles(m2))
                .outdir(m2Classes)
                .run()
                .writeAll();
    }
}
