/*
 * Copyright 2002-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.misc;

import java.net.URL;
import java.io.File;
import sun.net.www.ParseUtil;

/**
 * (Windows) Platform specific handling for file: URLs . In particular deals
 * with network paths mapping them to UNCs.
 *
 * @author      Michael McMahon
 */

public class FileURLMapper {

    URL url;
    String file;

    public FileURLMapper (URL url) {
        this.url = url;
    }

    /**
     * @returns the platform specific path corresponding to the URL, and in particular
     *  returns a UNC when the authority contains a hostname
     */

    public String getPath () {
        if (file != null) {
            return file;
        }
        String host = url.getHost();
        if (host != null && !host.equals("") &&
            !"localhost".equalsIgnoreCase(host)) {
            String rest = url.getFile();
            String s = host + ParseUtil.decode (url.getFile());
            file = "\\\\"+ s.replace('/', '\\');
            return file;
        }
        String path = url.getFile().replace('/', '\\');
        file = ParseUtil.decode(path);
        return file;
    }

    public boolean exists() {
        String path = getPath();
        File f = new File (path);
        return f.exists();
    }
}
