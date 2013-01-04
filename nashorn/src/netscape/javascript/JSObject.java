/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package netscape.javascript;

import java.applet.Applet;

/**
 * Stub for JSObject to get compilation going.
 */
public abstract class JSObject {

    /**
     * Get the window for an {@link Applet}. Not supported
     * by Nashorn
     *
     * @param a applet
     * @return the window instance
     */
    public static JSObject getWindow(final Applet a) {
        throw new UnsupportedOperationException("getWindow");
    }

    /**
     * Call a JavaScript method
     *
     * @param methodName name of method
     * @param args arguments to method
     * @return result of call
     */
    public abstract Object call(String methodName, Object args[]);

    /**
     * Evaluate a JavaScript expression
     *
     * @param s JavaScript expression to evaluate
     * @return evaluation result
     */
    public abstract Object eval(String s);

    /**
     * Retrieves a named member of a JavaScript object.
     *
     * @param name of member
     * @return member
     */
    public abstract Object getMember(String name);

    /**
     * Retrieves an indexed member of a JavaScript object.
     *
     * @param index index of member slot
     * @return member
     */
    public abstract Object getSlot(int index);

    /**
     * Remove a named member from a JavaScript object
     *
     * @param name name of member
     */
    public abstract void removeMember(String name);

    /**
     * Set a named member in a JavaScript object
     *
     * @param name  name of member
     * @param value value of member
     */
    public abstract void setMember(String name, Object value);

    /**
     * Set an indexed member in a JavaScript object
     *
     * @param index index of member slot
     * @param value value of member
     */
    public abstract void setSlot(int index, Object value);
}
