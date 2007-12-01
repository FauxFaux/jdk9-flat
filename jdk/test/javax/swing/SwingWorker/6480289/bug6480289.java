/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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

/* @test
 *
 * @bug 6480289
 * @author Igor Kushnirskiy
 * @summary tests if consequent workers are executed on the same thread and that VM can exit.
 */

import java.util.*;
import javax.swing.SwingWorker;

public class bug6480289 {
    private static final int ITERATIONS = 5;
    private static final Map<Thread, Integer> threadMap =
        Collections.synchronizedMap(new HashMap<Thread, Integer>());
    public static void main(String[] args) throws Exception {

        for (int i = 0; i < ITERATIONS; i++) {
            if (i != 0) {
                Thread.sleep(1000 * 5);
            }
            SwingWorker<?,?> worker =
                new SwingWorker<Void, Void>() {
                    @Override
                    protected Void doInBackground() {
                        Integer value = threadMap.get(Thread.currentThread());
                        value = Integer.valueOf(
                            ((value == null) ? 0 : value.intValue())
                            + 1);
                        threadMap.put(Thread.currentThread(), value);
                        return null;
                    }
                };
            worker.execute();
        }
        if (threadMap.keySet().size() != 1) {
            throw new RuntimeException("failed. More than one thread.");
        }
    }
}
