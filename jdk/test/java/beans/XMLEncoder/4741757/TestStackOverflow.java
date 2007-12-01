/*
 * Copyright 2006-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 * @test
 * @bug 4741757
 * @summary Tests stack overflow if equals is defined
 * @author Sergey Malenkov
 */

public final class TestStackOverflow extends AbstractTest {
    public static void main(String[] args) {
        test(new TestStackOverflow(5));
    }

    /**
     * The name of this field is the same as the name of property.
     */
    private int value = -1;

    private int property;

    public TestStackOverflow(int value) {
        this.property = value;
    }

    public int getValue() {
        return this.property;
    }

    public boolean equals(Object object) {
        if (object instanceof TestStackOverflow) {
            TestStackOverflow test = (TestStackOverflow) object;
            return test.property == this.property;
        }
        return false;
    }
}
