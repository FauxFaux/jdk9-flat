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
 * @test
 * @bug 4886799
 * @summary Check that IIOP URLs have /ior/ in the path
 * @author Eamonn McManus
 * @run clean IIOPURLTest
 * @run build IIOPURLTest
 * @run main IIOPURLTest
 */

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

public class IIOPURLTest {

    public static void main(String[] args) throws Exception {
        JMXServiceURL inputAddr =
            new JMXServiceURL("service:jmx:iiop://");
        JMXConnectorServer s =
            JMXConnectorServerFactory.newJMXConnectorServer(inputAddr, null,
                                                            null);
        MBeanServer mbs = MBeanServerFactory.createMBeanServer();
        mbs.registerMBean(s, new ObjectName("a:b=c"));
        s.start();
        JMXServiceURL outputAddr = s.getAddress();
        if (!outputAddr.getURLPath().startsWith("/ior/IOR:")) {
            System.out.println("URL path should start with \"/ior/IOR:\": " +
                               outputAddr);
            System.exit(1);
        }
        System.out.println("IIOP URL path looks OK: " + outputAddr);
        JMXConnector c = JMXConnectorFactory.connect(outputAddr);
        System.out.println("Successfully got default domain: " +
                           c.getMBeanServerConnection().getDefaultDomain());
        c.close();
        s.stop();
    }
}
