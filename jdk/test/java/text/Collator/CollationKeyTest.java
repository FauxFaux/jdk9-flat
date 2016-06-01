/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4106263
 * @summary Tests on the bug 4106263 - CollationKey became non-final extendable class.
 *          The implementation of CollationKey is moved to the new private class,
 *          RuleBasedCollationKey. This test basically tests on the two features:
 *          1. Existing code using CollationKey works (backward compatiblility)
 *          2. CollationKey can be extended by its subclass.
 */


public class CollationKeyTest {

    public static void main(String[] args) {
        CollationKeyTestImpl ck = new CollationKeyTestImpl("Testing the CollationKey");
        ck.run();
    }
}
