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

/**
 * @test
 * @bug 4922568
 * @run main/othervm -Djava.net.preferIPv4Stack=true IsHostReachableBug
 * @summary  isReachable returns true for IPv6
 */

import java.net.*;
import java.io.*;

public class IsHostReachableBug {
    public static void main(String[] args) throws Exception{
                String hostName = "fec0::1:a00:20ff:feed:b08d";
                BufferedReader in = null;
                FileWriter fw = null;
                String inString = " ";
                if (args.length > 0)
                        hostName = args[0];

                InetAddress addr = InetAddress.getByName(hostName);
                System.out.println("InetAddress is : " + addr);
                System.out.println("Is InetAddress instance of Inet6Address ? "
+ (addr instanceof Inet6Address));
                if (!addr.isReachable(10000)){
                        System.out.println(hostName + " is not reachable");
                }else {
                        throw new RuntimeException ("IPv6 address should not be reachable");
                }


    }
}
