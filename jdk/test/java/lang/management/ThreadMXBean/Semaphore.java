/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug     4530538
 * @summary A semaphore utility class.
 * @author  Mandy Chung
 */

public class Semaphore {
    private Object go = new Object();
    private int semaCount;
    private int waiters = 0;

    public Semaphore() {
        semaCount = 0;
    }

    public Semaphore(int initialCount) {
        semaCount = initialCount;
    }

    public void semaP() {
        synchronized (go) {
            waiters++;
            while (semaCount == 0) {
                try {
                    go.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    throw new InternalError();
                }
            }
            semaCount--;
            waiters--;
        }
    }
    public void semaV() {
        synchronized (go) {
            semaCount++;
            go.notify();
        }
    }

    public int getWaiterCount() {
        synchronized (go) {
            return waiters;
        }
    }

    public Object getLock() {
        return go;
    }
}
