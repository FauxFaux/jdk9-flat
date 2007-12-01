/*
 * Copyright 1996 Sun Microsystems, Inc.  All Rights Reserved.
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
package sun.rmi.transport.tcp;

/**
 * MultiplexConnectionInfo groups related information about a
 * virtual connection managed by a ConnectionMultiplexer object.
 *
 * @author Peter Jones
 */
class MultiplexConnectionInfo {

    /** integer that uniquely identifies this connection */
    int id;

    /** input stream for reading from connection */
    MultiplexInputStream in = null;

    /** output stream for writing to connection */
    MultiplexOutputStream out = null;

    /** true if this connection has been closed */
    boolean closed = false;

    /**
     * Create information structure for given connection identifier.
     * @param id connection identifier
     */
    MultiplexConnectionInfo(int id)
    {
        this.id  = id;
    }
}
