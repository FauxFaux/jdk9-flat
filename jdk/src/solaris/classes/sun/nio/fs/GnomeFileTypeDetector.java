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

import java.nio.file.FileRef;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * File type detector that uses the GNOME I/O library or the deprecated
 * GNOME VFS to guess the MIME type of a file.
 */

public class GnomeFileTypeDetector
    extends AbstractFileTypeDetector
{
    private static final String GNOME_VFS_MIME_TYPE_UNKNOWN =
        "application/octet-stream";

    // true if GIO available
    private final boolean gioAvailable;

    // true if GNOME VFS available and GIO is not available
    private final boolean gnomeVfsAvailable;

    public GnomeFileTypeDetector() {
        gioAvailable = initializeGio();
        if (gioAvailable) {
            gnomeVfsAvailable = false;
        } else {
            gnomeVfsAvailable = initializeGnomeVfs();
        }
    }

    @Override
    public String implProbeContentType(FileRef obj) throws IOException {
        if (!gioAvailable && !gnomeVfsAvailable)
            return null;
        if (!(obj instanceof UnixPath))
            return null;

        UnixPath path = (UnixPath)obj;
        NativeBuffer buffer = NativeBuffers.asNativeBuffer(path.getByteArrayForSysCalls());
        try {
            if (gioAvailable) {
                byte[] type = probeUsingGio(buffer.address());
                return (type == null) ? null : new String(type);
            } else {
                byte[] type = probeUsingGnomeVfs(buffer.address());
                if (type == null)
                    return null;
                String s = new String(type);
                return s.equals(GNOME_VFS_MIME_TYPE_UNKNOWN) ? null : s;
            }

        } finally {
            buffer.release();
        }

    }

    // GIO
    private static native boolean initializeGio();
    private static native byte[] probeUsingGio(long pathAddress);

    // GNOME VFS
    private static native boolean initializeGnomeVfs();
    private static native byte[] probeUsingGnomeVfs(long pathAddress);

    static {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                System.loadLibrary("nio");
                return null;
        }});
    }
}
