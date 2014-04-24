/*
 * Copyright (c) 2003, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4905786 6259611
 * @summary  Make sure that headings use the TH tag instead of the TD tag.
 * @author   jamieh
 * @library  ../lib/
 * @build    JavadocTester
 * @build    TestHeadings
 * @run main TestHeadings
 */

public class TestHeadings extends JavadocTester {

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", OUTPUT_DIR, "-sourcepath", SRC_DIR, "-use", "-header", "Test Files",
        "pkg1", "pkg2"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        //Package summary
        { "pkg1/package-summary.html",
            "<th class=\"colFirst\" scope=\"col\">" +
            "Class</th>\n" +
            "<th class=\"colLast\" scope=\"col\"" +
            ">Description</th>"
        },

        // Class documentation
        { "pkg1/C1.html",
            "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>\n" +
            "<th class=\"colLast\" scope=\"col\">Field and Description</th>"
        },
        { "pkg1/C1.html",
            "<h3>Methods inherited from class&nbsp;java.lang.Object</h3>"
        },

        // Class use documentation
        { "pkg1/class-use/C1.html",
            "<th class=\"colFirst\" scope=\"col\">Package</th>\n" +
            "<th class=\"colLast\" scope=\"col\">Description</th>"
        },
        { "pkg1/class-use/C1.html",
            "<th class=\"colFirst\" scope=\"col\">Modifier and Type</th>\n" +
            "<th class=\"colLast\" scope=\"col\">Field and Description</th>"
        },

        // Deprecated
        { "deprecated-list.html",
            "<th class=\"colOne\" scope=\"col\">Method and Description</th>"
        },

        // Constant values
        { "constant-values.html",
            "<th class=\"colFirst\" scope=\"col\">" +
            "Modifier and Type</th>\n" +
            "<th scope=\"col\">Constant Field</th>\n" +
            "<th class=\"colLast\" scope=\"col\">Value</th>"
        },

        // Serialized Form
        { "serialized-form.html",
            "<h2 title=\"Package\">Package&nbsp;pkg1</h2>"
        },
        { "serialized-form.html",
            "<h3>Class <a href=\"pkg1/C1.html\" title=\"class in pkg1\">" +
            "pkg1.C1</a> extends java.lang.Object implements Serializable</h3>"
        },
        { "serialized-form.html",
            "<h3>Serialized Fields</h3>"
        },

        // Overview Frame
        { "overview-frame.html",
            "<h1 title=\"Test Files\" class=\"bar\">Test Files</h1>"
        },
        { "overview-frame.html",
            "<title>Overview List</title>"
        },

        // Overview Summary
        { "overview-summary.html",
            "<title>Overview</title>"
        }
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestHeadings tester = new TestHeadings();
        tester.run(ARGS, TEST, NO_TEST);
        tester.printSummary();
    }
}
