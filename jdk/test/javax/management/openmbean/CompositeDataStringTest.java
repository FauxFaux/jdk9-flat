/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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

import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;

/*
 * @test
 * @bug 6610174
 * @summary Test that CompositeDataSupport.toString() represents arrays correctly
 * @author Eamonn McManus
 */
import javax.management.openmbean.ArrayType;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;

public class CompositeDataStringTest {
    public static void main(String[] args) throws Exception {
        CompositeType basicCT = new CompositeType(
                "basicCT", "basic CompositeType",
                new String[] {"name", "value"},
                new String[] {"name", "value"},
                new OpenType<?>[] {SimpleType.STRING, SimpleType.INTEGER});
        CompositeType ct = new CompositeType(
                "noddy", "descr",
                new String[] {"strings", "ints", "cds"},
                new String[] {"string array", "int array", "composite data array"},
                new OpenType<?>[] {
                    ArrayType.getArrayType(SimpleType.STRING),
                    ArrayType.getPrimitiveArrayType(int[].class),
                    ArrayType.getArrayType(basicCT)
                });
        CompositeData basicCD1 = new CompositeDataSupport(
                basicCT, new String[] {"name", "value"}, new Object[] {"ceathar", 4});
        CompositeData basicCD2 = new CompositeDataSupport(
                basicCT, new String[] {"name", "value"}, new Object[] {"naoi", 9});
        CompositeData cd = new CompositeDataSupport(
                ct,
                new String[] {"strings", "ints", "cds"},
                new Object[] {
                    new String[] {"fred", "jim", "sheila"},
                    new int[] {2, 3, 5, 7},
                    new CompositeData[] {basicCD1, basicCD2}
                });
        String s = cd.toString();
        System.out.println("CompositeDataSupport.toString(): " + s);
        String[] expected = {
            "fred, jim, sheila",
            "2, 3, 5, 7",
            "ceathar",
            "naoi",
        };
        boolean ok = true;
        for (String expect : expected) {
            if (s.contains(expect))
                System.out.println("OK: string contains <" + expect + ">");
            else {
                ok = false;
                System.out.println("NOT OK: string does not contain <" +
                        expect + ">");
            }
        }
        if (ok)
            System.out.println("TEST PASSED");
        else
            throw new Exception("TEST FAILED: string did not contain expected substrings");
    }
}
