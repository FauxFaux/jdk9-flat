/*
 * Copyright 1999-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6786028
 * @summary This test verifys the use of <strong> HTML tag instead of <B> by Javadoc std doclet.
 * @author Bhavesh Patel
 * @library ../lib/
 * @build JavadocTester
 * @build TestHtmlStrongTag
 * @run main TestHtmlStrongTag
 */

public class TestHtmlStrongTag extends JavadocTester {

    private static final String BUG_ID = "6786028";
    private static final String[][] TEST1 = {
        {BUG_ID + FS + "pkg1" + FS + "C1.html", "<STRONG>Method Summary</STRONG>"},
        {BUG_ID + FS + "pkg1" + FS + "C1.html", "<STRONG>See Also:</STRONG>"},
        {BUG_ID + FS + "pkg1" + FS + "package-summary.html", "<STRONG>Class Summary</STRONG>"}};
    private static final String[][] NEGATED_TEST1 = {
        {BUG_ID + FS + "pkg1" + FS + "C1.html", "<B>"}};
    private static final String[][] TEST2 = {
        {BUG_ID + FS + "pkg2" + FS + "C2.html", "<STRONG>Method Summary</STRONG>"},
        {BUG_ID + FS + "pkg2" + FS + "C2.html", "<B>Comments:</B>"},
        {BUG_ID + FS + "pkg1" + FS + "package-summary.html", "<STRONG>Class Summary</STRONG>"}};

    private static final String[] ARGS1 =
        new String[] {
            "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg1"};
    private static final String[] ARGS2 =
        new String[] {
            "-d", BUG_ID, "-sourcepath", SRC_DIR, "pkg2"};

    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     */
    public static void main(String[] args) {
        TestHtmlStrongTag tester = new TestHtmlStrongTag();
        run(tester, ARGS1, TEST1, NEGATED_TEST1);
        run(tester, ARGS2, TEST2, NO_TEST);
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
