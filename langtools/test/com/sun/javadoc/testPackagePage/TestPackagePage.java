/*
 * Copyright (c) 2002, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4492643 4689286
 * @summary Test that a package page is properly generated when a .java file
 * passed to Javadoc.  Also test that the proper package links are generated
 * when single or multiple packages are documented.
 * @author jamieh
 * @library ../lib/
 * @build JavadocTester
 * @build TestPackagePage
 * @run main TestPackagePage
 */

public class TestPackagePage extends JavadocTester {

    private static final String[][] TEST1 = {
        { "com/pkg/package-summary.html",
            "This is a package page."
        },
        //With just one package, all general pages link to the single package page.
        { "com/pkg/C.html",
            "<a href=\"../../com/pkg/package-summary.html\">Package</a>"
        },
        { "com/pkg/package-tree.html",
            "<li><a href=\"../../com/pkg/package-summary.html\">Package</a></li>"
        },
        { "deprecated-list.html",
            "<li><a href=\"com/pkg/package-summary.html\">Package</a></li>"
        },
        { "index-all.html",
            "<li><a href=\"com/pkg/package-summary.html\">Package</a></li>"
        },
        { "help-doc.html",
            "<li><a href=\"com/pkg/package-summary.html\">Package</a></li>"
        },
    };

    private static final String[][] TEST2 = {
        //With multiple packages, there is no package link in general pages.
        { "deprecated-list.html",
            "<li>Package</li>"
        },
        { "index-all.html",
            "<li>Package</li>"
        },
        { "help-doc.html",
            "<li>Package</li>"
        },
    };

    private static final String[] ARGS1 =
        new String[] {
            "-d", OUTPUT_DIR + "-1", "-sourcepath", SRC_DIR,
            SRC_DIR + "/com/pkg/C.java"
        };

    private static final String[] ARGS2 =
        new String[] {
            "-d", OUTPUT_DIR + "-2", "-sourcepath", SRC_DIR,
            "com.pkg", "pkg2"
        };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestPackagePage tester = new TestPackagePage();
        tester.run(ARGS1, TEST1, NO_TEST);
        tester.run(ARGS2, TEST2, NO_TEST);
        tester.printSummary();
    }
}
