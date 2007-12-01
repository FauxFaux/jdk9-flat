/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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
 * The contents of this file are subject to the Sun Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://www.sun.com/, and in the file LICENSE.html in the
 * doc directory.
 *
 * The Original Code is HAT. The Initial Developer of the
 * Original Code is Bill Foote, with contributions from others
 * at JavaSoft/Sun. Portions created by Bill Foote and others
 * at Javasoft/Sun are Copyright (C) 1997-2004. All Rights Reserved.
 *
 * In addition to the formal license, I ask that you don't
 * change the history or donations files without permission.
 *
 */

package com.sun.tools.hat.internal.model;

import com.sun.tools.hat.internal.util.Misc;

/**
 *
 * @author      Bill Foote
 */


/**
 * Represents a member of the rootset, that is, one of the objects that
 * the GC starts from when marking reachable objects.
 */

public class Root {

    private long id;            // ID of the JavaThing we refer to
    private long refererId;     // Thread or Class responsible for this, or 0
    private int index = -1;             // Index in Snapshot.roots
    private int type;
    private String description;
    private JavaHeapObject referer = null;
    private StackTrace stackTrace = null;

    // Values for type.  Higher values are more interesting -- see getType().
    // See also getTypeName()
    public final static int INVALID_TYPE = 0;
    public final static int UNKNOWN = 1;
    public final static int SYSTEM_CLASS = 2;

    public final static int NATIVE_LOCAL = 3;
    public final static int NATIVE_STATIC = 4;
    public final static int THREAD_BLOCK = 5;
    public final static int BUSY_MONITOR = 6;
    public final static int JAVA_LOCAL = 7;
    public final static int NATIVE_STACK = 8;
    public final static int JAVA_STATIC = 9;


    public Root(long id, long refererId, int type, String description) {
        this(id, refererId, type, description, null);
    }


    public Root(long id, long refererId, int type, String description,
                StackTrace stackTrace) {
        this.id = id;
        this.refererId = refererId;
        this.type = type;
        this.description = description;
        this.stackTrace = stackTrace;
    }

    public long getId() {
        return id;
    }

    public String getIdString() {
        return Misc.toHex(id);
    }

    public String getDescription() {
        if ("".equals(description)) {
            return getTypeName() + " Reference";
        } else {
            return description;
        }
    }

    /**
     * Return type.  We guarantee that more interesting roots will have
     * a type that is numerically higher.
     */
    public int getType() {
        return type;
    }

    public String getTypeName() {
        switch(type) {
            case INVALID_TYPE:          return "Invalid (?!?)";
            case UNKNOWN:               return "Unknown";
            case SYSTEM_CLASS:          return "System Class";
            case NATIVE_LOCAL:          return "JNI Local";
            case NATIVE_STATIC:         return "JNI Global";
            case THREAD_BLOCK:          return "Thread Block";
            case BUSY_MONITOR:          return "Busy Monitor";
            case JAVA_LOCAL:            return "Java Local";
            case NATIVE_STACK:          return "Native Stack (possibly Java local)";
            case JAVA_STATIC:           return "Java Static";
            default:                    return "??";
        }
    }

    /**
     * Given two Root instances, return the one that is most interesting.
     */
    public Root mostInteresting(Root other) {
        if (other.type > this.type) {
            return other;
        } else {
            return this;
        }
    }

    /**
     * Get the object that's responsible for this root, if there is one.
     * This will be null, a Thread object, or a Class object.
     */
    public JavaHeapObject getReferer() {
        return referer;
    }

    /**
     * @return the stack trace responsible for this root, or null if there
     * is none.
     */
    public StackTrace getStackTrace() {
        return stackTrace;
    }

    /**
     * @return The index of this root in Snapshot.roots
     */
    public int getIndex() {
        return index;
    }

    void resolve(Snapshot ss) {
        if (refererId != 0) {
            referer = ss.findThing(refererId);
        }
        if (stackTrace != null) {
            stackTrace.resolve(ss);
        }
    }

    void setIndex(int i) {
        index = i;
    }

}
