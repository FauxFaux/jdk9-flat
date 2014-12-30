/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @summary verify that we can override sources to be compiled
 * @bug 8054689
 * @author Fredrik O
 * @author sogoel (rewrite)
 * @library /tools/lib
 * @build Wrapper ToolBox
 * @run main Wrapper CompileWithOverrideSources
 */

import java.util.*;
import java.nio.file.*;

public class CompileWithOverrideSources extends SJavacTester {
    public static void main(String... args) throws Exception {
        CompileWithOverrideSources cos = new CompileWithOverrideSources();
        cos.test();
    }

    // Compile gensrc and gensrc2. However do not compile broken beta.B in gensrc,
    // only compile ok beta.B in gensrc2
    void test() throws Exception {
        Files.createDirectory(BIN);
        clean(GENSRC, GENSRC2, GENSRC3, BIN);

        Map<String,Long> previous_bin_state = collectState(BIN);
        ToolBox tb = new ToolBox();
        tb.writeFile(GENSRC.resolve("alfa/omega/A.java"),
                 "package alfa.omega; import beta.B; import gamma.C; public class A { B b; C c; }");
        tb.writeFile(GENSRC.resolve("beta/B.java"),
                 "package beta; public class B { broken");
        tb.writeFile(GENSRC.resolve("gamma/C.java"),
                 "package gamma; public class C { }");

        tb.writeFile(GENSRC2.resolve("beta/B.java"),
                 "package beta; public class B { }");

        compile("-x", "beta", "gensrc", "gensrc2", "-d", "bin", "-h", "headers", "-j", "1",
                SERVER_ARG);
        Map<String,Long> new_bin_state = collectState(BIN);
        verifyThatFilesHaveBeenAdded(previous_bin_state, new_bin_state,
                                     "bin/alfa/omega/A.class",
                                     "bin/beta/B.class",
                                     "bin/gamma/C.class",
                                     "bin/javac_state");

        System.out.println("----- Compile with exluded beta went well!");
        clean(BIN);
        compileExpectFailure("gensrc", "gensrc2", "-d", "bin", "-h", "headers", "-j", "1",
                             SERVER_ARG);

        System.out.println("----- Compile without exluded beta failed, as expected! Good!");
        clean(GENSRC, GENSRC2, BIN);
    }
}
