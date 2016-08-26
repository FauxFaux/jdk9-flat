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
 * @bug 8136421
 * @requires (vm.simpleArch == "x64" | vm.simpleArch == "sparcv9" | vm.simpleArch == "aarch64")
 * @library / /test/lib
 * @library ../common/patches
 * @modules java.base/jdk.internal.misc
 * @modules jdk.vm.ci/jdk.vm.ci.hotspot
 * @build jdk.vm.ci/jdk.vm.ci.hotspot.CompilerToVMHelper
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *                   compiler.jvmci.compilerToVM.InitializeConfigurationTest
 */

package compiler.jvmci.compilerToVM;

import jdk.test.lib.Asserts;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;

public class InitializeConfigurationTest {
    public static void main(String args[]) {
        new InitializeConfigurationTest().runTest();
    }

    private void runTest() {
        TestHotSpotVMConfig config = new TestHotSpotVMConfig(HotSpotJVMCIRuntime.runtime().getConfigStore());
        Asserts.assertNE(config.codeCacheHighBound, 0L, "Got null address");
        Asserts.assertNE(config.stubRoutineJintArrayCopy, 0L, "Got null address");
    }

    private static class TestHotSpotVMConfig extends HotSpotVMConfigAccess {

        private TestHotSpotVMConfig(HotSpotVMConfigStore store) {
            super(store);
        }

        final long codeCacheHighBound = getFieldValue("CodeCache::_high_bound", Long.class);
        final long stubRoutineJintArrayCopy = getFieldValue("StubRoutines::_jint_arraycopy", Long.class);
    }
}
