/*
 * Copyright 2001 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 4494818
 * @summary Test reading 0 bytes over a pipe
 */

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.channels.spi.SelectorProvider;

/**
 * Testing PipeChannel
 */
public class EmptyRead {
    public static void main(String[] args) throws Exception {
        SelectorProvider sp = SelectorProvider.provider();
        Pipe p = sp.openPipe();
        Pipe.SinkChannel sink = p.sink();
        Pipe.SourceChannel source = p.source();

        byte[] someBytes = new byte[0];
        ByteBuffer outgoingdata = ByteBuffer.wrap(someBytes);

        int totalWritten = 0;
        int written = sink.write(outgoingdata);
        if (written < 0)
            throw new Exception("Write failed");

        ByteBuffer incomingdata = ByteBuffer.allocateDirect(0);
        int read = source.read(incomingdata);
        if (read < 0)
            throw new Exception("Read EOF");

        sink.close();
        source.close();
    }
}
