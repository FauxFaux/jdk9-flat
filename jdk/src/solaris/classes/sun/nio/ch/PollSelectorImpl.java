/*
 * Copyright 2001-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.nio.ch;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.channels.spi.*;
import java.util.*;
import sun.misc.*;


/**
 * An implementation of Selector for Solaris.
 */

class PollSelectorImpl
    extends AbstractPollSelectorImpl
{

    // File descriptors used for interrupt
    private int fd0;
    private int fd1;

    // Lock for interrupt triggering and clearing
    private Object interruptLock = new Object();
    private boolean interruptTriggered = false;

    /**
     * Package private constructor called by factory method in
     * the abstract superclass Selector.
     */
    PollSelectorImpl(SelectorProvider sp) {
        super(sp, 1, 1);
        int[] fdes = new int[2];
        IOUtil.initPipe(fdes, false);
        fd0 = fdes[0];
        fd1 = fdes[1];
        pollWrapper = new PollArrayWrapper(INIT_CAP);
        pollWrapper.initInterrupt(fd0, fd1);
        channelArray = new SelectionKeyImpl[INIT_CAP];
    }

    protected int doSelect(long timeout)
        throws IOException
    {
        if (channelArray == null)
            throw new ClosedSelectorException();
        processDeregisterQueue();
        try {
            begin();
            pollWrapper.poll(totalChannels, 0, timeout);
        } finally {
            end();
        }
        processDeregisterQueue();
        int numKeysUpdated = updateSelectedKeys();
        if (pollWrapper.getReventOps(0) != 0) {
            // Clear the wakeup pipe
            pollWrapper.putReventOps(0, 0);
            synchronized (interruptLock) {
                IOUtil.drain(fd0);
                interruptTriggered = false;
            }
        }
        return numKeysUpdated;
    }

    protected void implCloseInterrupt() throws IOException {
        // prevent further wakeup
        synchronized (interruptLock) {
            interruptTriggered = true;
        }
        FileDispatcher.closeIntFD(fd0);
        FileDispatcher.closeIntFD(fd1);
        fd0 = -1;
        fd1 = -1;
        pollWrapper.release(0);
    }

    public Selector wakeup() {
        synchronized (interruptLock) {
            if (!interruptTriggered) {
                pollWrapper.interrupt();
                interruptTriggered = true;
            }
        }
        return this;
    }

}
