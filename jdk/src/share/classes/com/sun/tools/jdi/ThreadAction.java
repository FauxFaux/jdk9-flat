/*
 * Copyright 1999 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.jdi;

import com.sun.jdi.*;
import java.util.EventObject;

/*
 * The name "action" is used to avoid confusion
 * with JDI events.
 */
class ThreadAction extends EventObject {
    // Event ids
    /*static final int THREAD_SUSPENDED = 1;*/
    static final int THREAD_RESUMABLE = 2;

    int id;

    ThreadAction(ThreadReference thread, int id) {
        super(thread);
        this.id = id;
    }
    ThreadReference thread() {
        return (ThreadReference)getSource();
    }
    int id() {
        return id;
    }
}
