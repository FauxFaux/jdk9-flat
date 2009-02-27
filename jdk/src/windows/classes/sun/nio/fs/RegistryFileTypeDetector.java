/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.nio.fs;

import java.nio.file.*;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * File type detector that does lookup of file extension using Windows Registry.
 */

public class RegistryFileTypeDetector
    extends AbstractFileTypeDetector
{
    public RegistryFileTypeDetector() {
        super();
    }

    @Override
    public String implProbeContentType(FileRef file) throws IOException {
        if (!(file instanceof Path))
            return null;

        // get file extension
        Path name = ((Path)file).getName();
        if (name == null)
            return null;
        String filename = name.toString();
        int dot = filename.lastIndexOf('.');
        if ((dot < 0) || (dot == (filename.length()-1)))
            return null;

        // query HKEY_CLASSES_ROOT\<ext>
        String key = filename.substring(dot);
        NativeBuffer keyBuffer = WindowsNativeDispatcher.asNativeBuffer(key);
        NativeBuffer nameBuffer = WindowsNativeDispatcher.asNativeBuffer("Content Type");
        try {
            return queryStringValue(keyBuffer.address(), nameBuffer.address());
        } finally {
            nameBuffer.release();
            keyBuffer.release();
        }
    }

    private static native String queryStringValue(long subKey, long name);

    static {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                // nio.dll has dependency on net.dll
                System.loadLibrary("net");
                System.loadLibrary("nio");
                return null;
        }});
    }
}
