/*
* Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.OutputAnalyzer;
import jdk.test.lib.ProcessTools;
import jdk.test.lib.Platform;

/*
 * @test TestCardTablePageCommits
 * @key gc
 * @bug 8059066
 * @summary Tests that the card table does not commit the same page twice
 * @requires vm.gc=="Parallel" | vm.gc=="null"
 * @library /testlibrary
 * @modules java.base/sun.misc
 *          java.management
 * @run driver TestCardTablePageCommits
 */
public class TestCardTablePageCommits {
    public static void main(String args[]) throws Exception {
        // The test is run with a small heap to make sure all pages in the card
        // table gets committed. Need 8 MB heap to trigger the bug on SPARC
        // because of 8kB pages, assume 4 KB pages for all other CPUs.
        String Xmx = Platform.isSparc() ? "-Xmx8m" : "-Xmx4m";

        String[] opts = {Xmx, "-XX:NativeMemoryTracking=detail", "-XX:+UseParallelGC", "-version"};
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(opts);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
    }
}
