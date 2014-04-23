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
 * @bug 4684827 4633969
 * @summary This test verifies that throws tags in implementing class
 * override the throws tags in interface. This test also verifies that throws tags are inherited properly
 * the case where the name of one exception is not fully qualified.
 * @author jamieh
 * @library ../lib/
 * @build JavadocTester
 * @build TestThrowsTagInheritence
 * @run main TestThrowsTagInheritence
 */

public class TestThrowsTagInheritence extends JavadocTester {

    private static final String[][] TEST = {
        //The class should not inherit the tag from the interface.
        { "Foo.html", "Test 1 passes."}
    };
    private static final String[][] NEGATED_TEST = {
        //The class should not inherit the tag from the interface.
        { "C.html", "Test 1 fails."}

    };
    private static final String[] ARGS = new String[] {
        "-d", OUTPUT_DIR, "-sourcepath", SRC_DIR, SRC_DIR + "/C.java",
        SRC_DIR + "/I.java", SRC_DIR + "/Foo.java",
        SRC_DIR + "/Iface.java"
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestThrowsTagInheritence tester = new TestThrowsTagInheritence();
        tester.run(ARGS, TEST, NEGATED_TEST);
        tester.printSummary();
    }
}
