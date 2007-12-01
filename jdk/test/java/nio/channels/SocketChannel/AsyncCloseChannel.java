/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 6285901
 * @summary Check no data is written to wrong socket channel during async closing.
 * @author Xueming Shen
 */

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.net.*;

public class AsyncCloseChannel {
    static boolean failed = false;
    static boolean keepGoing = true;
    static String host = "127.0.0.1";
    static int sensorPort = 3010;
    static int targetPort = 3020;
    static int maxAcceptCount = 1000;
    static int acceptCount = 0;

    public static void main(String args[]) throws Exception {
        if (System.getProperty("os.name").startsWith("Windows")) {
            System.err.println("WARNING: Still does not work on Windows!");
            return;
        }
        Thread ss = new SensorServer(); ss.start();
        Thread ts = new TargetServer(); ts.start();
        Thread sc = new SensorClient(); sc.start();
        Thread tc = new TargetClient(); tc.start();

        while(acceptCount < maxAcceptCount && !failed) {
            Thread.yield();
        }
        keepGoing = false;
        try {
            ss.interrupt();
            ts.interrupt();
            sc.interrupt();
            tc.interrupt();
        } catch (Exception e) {}
        if (failed)
            throw new RuntimeException("AsyncCloseChannel2 failed after <"
                                       + acceptCount + "> times of accept!");
    }


    static class SensorServer extends ThreadEx {
        public void runEx() throws Exception {
            ServerSocket server;
            server = new ServerSocket(sensorPort);
            while(keepGoing) {
                try {
                    final Socket s = server.accept();
                    new Thread() {
                        public void run() {
                            try {
                                int c = s.getInputStream().read();
                                if(c != -1) {
                                    // No data is ever written to the peer's socket!
                                    System.out.println("Oops: read a character: "
                                                       + (char) c);
                                    failed = true;
                                }
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            } finally {
                                closeIt(s);
                            }
                        }
                    }.start();
                } catch (IOException ex) {
                    //ex.printStackTrace();
                }
            }
        }
    }

    static class TargetServer extends ThreadEx {
        public void runEx() throws Exception {

            ServerSocket server;
            server = new ServerSocket(targetPort);
            while (keepGoing) {
                try {
                    final Socket s = server.accept();
                    acceptCount++;
                    new Thread() {
                        public void run() {
                            boolean empty = true;
                            try {
                                for(;;) {
                                    int c = s.getInputStream().read();
                                    if(c == -1) {
                                        if(!empty)
                                        break;
                                    }
                                    empty = false;
                                }
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            } finally {
                                closeIt(s);
                            }
                        }
                    }.start();
                } catch (IOException ex) {
                    //ex.printStackTrace();
                }
            }
        }
    }

    static class SensorClient extends Thread {
        private static boolean wake;
        private static SensorClient theClient;
        public void run() {
            while (keepGoing) {
                Socket s = null;
                try {
                    s = new Socket();
                    synchronized(this) {
                        while(!wake) {
                            try {
                                wait();
                            } catch (InterruptedException ex) { }
                        }
                    }
                    wake = false;
                    s.connect(new InetSocketAddress(host, sensorPort));
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ex) { }
                } catch (IOException ex) {
                    System.out.println("Exception on sensor client " + ex.getMessage());
                } finally {
                    if(s != null) {
                        try {
                            s.close();
                        } catch(IOException ex) { ex.printStackTrace();}
                    }
                }
            }
        }

        public SensorClient() {
            theClient = this;
        }

        public static void wakeMe() {
            synchronized(theClient) {
                wake = true;
                theClient.notify();
            }
        }
    }

    static class TargetClient extends Thread {
        volatile boolean ready = false;
        public void run() {
            while(keepGoing) {
                try {
                    final SocketChannel s = SocketChannel.open(
                        new InetSocketAddress(host, targetPort));
                    s.finishConnect();
                    s.socket().setSoLinger(false, 0);
                    ready = false;
                    Thread t = new Thread() {
                        public void run() {
                            ByteBuffer b = ByteBuffer.allocate(1);
                            try {
                                for(;;) {
                                    b.clear();
                                    b.put((byte) 'A');
                                    b.flip();
                                    s.write(b);
                                    ready = true;
                                }
                            } catch (IOException ex) {
                                if(!(ex instanceof ClosedChannelException))
                                    System.out.println("Exception in target client child "
                                                       + ex.toString());
                            }
                        }
                    };
                    t.start();
                    while(!ready)
                        Thread.yield();
                    s.close();
                    SensorClient.wakeMe();
                    t.join();
                } catch (IOException ex) {
                     System.out.println("Exception in target client parent "
                                        + ex.getMessage());
                } catch (InterruptedException ex) {}
            }
        }
    }

    static abstract class ThreadEx extends Thread {
        public void run() {
            try {
                runEx();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        abstract void runEx() throws Exception;
    }


    public static void closeIt(Socket s) {
        try {
            if(s != null)
                s.close();
        } catch (IOException ex) { }
    }
}
