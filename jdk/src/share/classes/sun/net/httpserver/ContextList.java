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

package sun.net.httpserver;

import java.util.*;
import com.sun.net.httpserver.*;
import com.sun.net.httpserver.spi.*;

class ContextList {

    final static int MAX_CONTEXTS = 50;

    LinkedList<HttpContextImpl> list = new LinkedList<HttpContextImpl>();

    public synchronized void add (HttpContextImpl ctx) {
        assert ctx.getPath() != null;
        list.add (ctx);
    }

    public synchronized int size () {
        return list.size();
    }

   /* initially contexts are located only by protocol:path.
    * Context with longest prefix matches (currently case-sensitive)
    */
    synchronized HttpContextImpl findContext (String protocol, String path) {
        return findContext (protocol, path, false);
    }

    synchronized HttpContextImpl findContext (String protocol, String path, boolean exact) {
        protocol = protocol.toLowerCase();
        String longest = "";
        HttpContextImpl lc = null;
        for (HttpContextImpl ctx: list) {
            if (!ctx.getProtocol().equals(protocol)) {
                continue;
            }
            String cpath = ctx.getPath();
            if (exact && !cpath.equals (path)) {
                continue;
            } else if (!exact && !path.startsWith(cpath)) {
                continue;
            }
            if (cpath.length() > longest.length()) {
                longest = cpath;
                lc = ctx;
            }
        }
        return lc;
    }

    public synchronized void remove (String protocol, String path)
        throws IllegalArgumentException
    {
        HttpContextImpl ctx = findContext (protocol, path, true);
        if (ctx == null) {
            throw new IllegalArgumentException ("cannot remove element from list");
        }
        list.remove (ctx);
    }

    public synchronized void remove (HttpContextImpl context)
        throws IllegalArgumentException
    {
        for (HttpContextImpl ctx: list) {
            if (ctx.equals (context)) {
                list.remove (ctx);
                return;
            }
        }
        throw new IllegalArgumentException ("no such context in list");
    }
}
