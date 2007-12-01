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
package sun.rmi.transport;

import java.io.*;

public interface Connection {
    /**
     * Gets the input stream for this connection.
     */
    public InputStream getInputStream() throws IOException;

    /*
     * Release the input stream for this connection.
     */
    public void releaseInputStream() throws IOException;

    /**
     * Gets the output stream for this connection
     */
    public OutputStream getOutputStream() throws IOException;

    /*
     * Release the output stream for this connection.
     */
    public void releaseOutputStream() throws IOException;

    /**
     * Return true if channel can be used for multiple operations.
     */
    public boolean isReusable();

    /**
     * Close connection.
     */
    public void close() throws IOException;

    /**
     * Returns the channel for this connection.
     */
    public Channel getChannel();
}
