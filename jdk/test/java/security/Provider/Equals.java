/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/**
 * @test
 * @bug 4918769
 * @summary make sure Provider.equals() behaves as expected with the id attributes
 * @author Andreas Sterbenz
 */

import java.security.*;

public class Equals {

    public static void main(String[] args) throws Exception {
        Provider p1 = new P1("foo", 1.0d, "foo");
        Provider p1b = new P1("foo", 1.0d, "foo");
        Provider p2 = new P2("foo", 1.0d, "foo");
        System.out.println(p1.entrySet());
        if (p1.equals(p2)) {
            throw new Exception("Objects are equal");
        }
        if (p1.equals(p1b) == false) {
            throw new Exception("Objects not equal");
        }
        p1.clear();
        if (p1.equals(p1b) == false) {
            throw new Exception("Objects not equal");
        }
        p1.put("Provider.id name", "bar");
        p1.remove("Provider.id version");
        if (p1.equals(p1b) == false) {
            throw new Exception("Objects not equal");
        }
    }

    private static class P1 extends Provider {
        P1(String name, double version, String info) {
            super(name, version, info);
        }
    }

    private static class P2 extends Provider {
        P2(String name, double version, String info) {
            super(name, version, info);
        }
    }

}
