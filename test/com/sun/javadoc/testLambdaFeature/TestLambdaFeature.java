/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8004893 8022738 8029143
 * @summary  Make sure that the lambda feature changes work fine in
 *           javadoc.
 * @author   bpatel
 * @library  ../lib/
 * @build    JavadocTester TestLambdaFeature
 * @run main TestLambdaFeature
 */

/*
 * NOTE : This test should be elided when version 1.7 support is removed from the JDK
 *              or the negative part of the test showing 1.7's non-support should be
 *              removed [ 8022738 ]
 */

public class TestLambdaFeature extends JavadocTester {

    //Test information.
    private static final String BUG_ID = "8004893-8022738";

    //Javadoc arguments.
    private static final String[] ARGS = new String[] {
        "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg", "pkg1"
    };

    private static final String[] ARGS_1 = new String[] {
        "-d", BUG_ID + "-2", "-sourcepath", SRC_DIR, "-source", "1.7", "pkg1"
    };

    //Input for string search tests.
    private static final String[][] TEST = {
        {BUG_ID + "/pkg/A.html",
            "<td class=\"colFirst\"><code>default void</code></td>"},
        {BUG_ID + "/pkg/A.html",
            "<pre>default&nbsp;void&nbsp;defaultMethod()</pre>"},
        {BUG_ID + "/pkg/A.html",
            "<caption><span id=\"t0\" class=\"activeTableTab\"><span>" +
            "All Methods</span><span class=\"tabEnd\">&nbsp;</span></span>" +
            "<span id=\"t2\" class=\"tableTab\"><span>" +
            "<a href=\"javascript:show(2);\">Instance Methods</a></span>" +
            "<span class=\"tabEnd\">&nbsp;</span></span><span id=\"t3\" " +
            "class=\"tableTab\"><span><a href=\"javascript:show(4);\">" +
            "Abstract Methods</a></span><span class=\"tabEnd\">&nbsp;</span>" +
            "</span><span id=\"t5\" class=\"tableTab\"><span>" +
            "<a href=\"javascript:show(16);\">Default Methods</a></span>" +
            "<span class=\"tabEnd\">&nbsp;</span></span></caption>"},
        {BUG_ID + "/pkg/A.html",
            "<dl>\n" +
            "<dt>Functional Interface:</dt>\n" +
            "<dd>This is a functional interface and can therefore be used as " +
            "the assignment target for a lambda expression or method " +
            "reference.</dd>\n" +
            "</dl>"},
        {BUG_ID + "/pkg1/FuncInf.html",
            "<dl>\n" +
            "<dt>Functional Interface:</dt>\n" +
            "<dd>This is a functional interface and can therefore be used as " +
            "the assignment target for a lambda expression or method " +
            "reference.</dd>\n" +
            "</dl>"}
    };
    private static final String[][] NEGATED_TEST = {
        {BUG_ID + "/pkg/A.html",
            "<td class=\"colFirst\"><code>default default void</code></td>"},
        {BUG_ID + "/pkg/A.html",
            "<pre>default&nbsp;default&nbsp;void&nbsp;defaultMethod()</pre>"},
        {BUG_ID + "/pkg/B.html",
            "<td class=\"colFirst\"><code>default void</code></td>"},
        {BUG_ID + "/pkg1/NotAFuncInf.html",
            "<dl>\n" +
            "<dt>Functional Interface:</dt>\n" +
            "<dd>This is a functional interface and can therefore be used as " +
            "the assignment target for a lambda expression or method " +
            "reference.</dd>\n" +
            "</dl>"},
        {BUG_ID + "/pkg/B.html",
            "<dl>\n" +
            "<dt>Functional Interface:</dt>"}
    };
    private static final String[][] NEGATED_TEST_1 = {
        {BUG_ID + "-2/pkg1/FuncInf.html",
            "<dl>\n" +
            "<dt>Functional Interface:</dt>"}
    };

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestLambdaFeature tester = new TestLambdaFeature();
        tester.run(ARGS, TEST, NEGATED_TEST);
        tester.run(ARGS_1, NO_TEST, NEGATED_TEST_1);
        tester.printSummary();
    }

    /**
     * {@inheritDoc}
     */
    public String getBugId() {
        return BUG_ID;
    }

    /**
     * {@inheritDoc}
     */
    public String getBugName() {
        return getClass().getName();
    }
}
