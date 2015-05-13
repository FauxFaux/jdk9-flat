/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 6273541
 * @summary Test that the counter/gauge/string monitors emit a
 *          jmx.monitor.error.type notification when the attribute
 *          being monitored returns a non comparable value.
 * @author Luis-Miguel Alventosa
 * @modules java.management
 * @run clean NonComparableAttributeValueTest
 * @run build NonComparableAttributeValueTest
 * @run main NonComparableAttributeValueTest
 */

import javax.management.*;
import javax.management.monitor.*;

public class NonComparableAttributeValueTest implements NotificationListener {

    // Flag to notify that a message has been received
    private volatile boolean messageReceived = false;

    // MBean class
    public class ObservedObject implements ObservedObjectMBean {
        public Object getIntegerAttribute() {
            return new Object();
        }
        public Object getStringAttribute() {
            return new Object();
        }
    }

    // MBean interface
    public interface ObservedObjectMBean {
        public Object getIntegerAttribute();
        public Object getStringAttribute();
    }

    // Notification handler
    public void handleNotification(Notification notification,
                                   Object handback) {
        MonitorNotification n = (MonitorNotification) notification;
        echo("\tInside handleNotification...");
        String type = n.getType();
        try {
            if (type.equals(
                    MonitorNotification.OBSERVED_ATTRIBUTE_TYPE_ERROR)) {
                echo("\t\t" + n.getObservedAttribute() + " is null");
                echo("\t\tDerived Gauge = " + n.getDerivedGauge());
                echo("\t\tTrigger = " + n.getTrigger());

                synchronized (this) {
                    messageReceived = true;
                    notifyAll();
                }
            } else {
                echo("\t\tSkipping notification of type: " + type);
            }
        } catch (Exception e) {
            echo("\tError in handleNotification!");
            e.printStackTrace(System.out);
        }
    }

    /**
     * Update the counter and check for notifications
     */
    public int counterMonitorNotification() throws Exception {

        CounterMonitor counterMonitor = null;
        try {
            MBeanServer server = MBeanServerFactory.newMBeanServer();

            String domain = server.getDefaultDomain();

            // Create a new CounterMonitor MBean and add it to the MBeanServer.
            //
            echo(">>> CREATE a new CounterMonitor MBean");
            ObjectName counterMonitorName = new ObjectName(
                            domain + ":type=" + CounterMonitor.class.getName());
            counterMonitor = new CounterMonitor();
            server.registerMBean(counterMonitor, counterMonitorName);

            echo(">>> ADD a listener to the CounterMonitor");
            counterMonitor.addNotificationListener(this, null, null);

            //
            // MANAGEMENT OF A STANDARD MBEAN
            //

            echo(">>> CREATE a new ObservedObject MBean");

            ObjectName obsObjName =
                ObjectName.getInstance(domain + ":type=ObservedObject");
            ObservedObject obsObj = new ObservedObject();
            server.registerMBean(obsObj, obsObjName);

            echo(">>> SET the attributes of the CounterMonitor:");

            counterMonitor.addObservedObject(obsObjName);
            echo("\tATTRIBUTE \"ObservedObject\"    = " + obsObjName);

            counterMonitor.setObservedAttribute("IntegerAttribute");
            echo("\tATTRIBUTE \"ObservedAttribute\" = IntegerAttribute");

            counterMonitor.setNotify(true);
            echo("\tATTRIBUTE \"NotifyFlag\"        = true");

            Integer threshold = 2;
            counterMonitor.setInitThreshold(threshold);
            echo("\tATTRIBUTE \"Threshold\"         = " + threshold);

            int granularityperiod = 500;
            counterMonitor.setGranularityPeriod(granularityperiod);
            echo("\tATTRIBUTE \"GranularityPeriod\" = " + granularityperiod);

            echo(">>> START the CounterMonitor");
            counterMonitor.start();

            // Check if notification was received
            //
            doWait();
            if (messageReceived) {
                echo("\tOK: CounterMonitor notification received");
            } else {
                echo("\tKO: CounterMonitor notification missed or not emitted");
                return 1;
            }
        } finally {
            if (counterMonitor != null)
                counterMonitor.stop();
        }

        return 0;
    }

    /**
     * Update the gauge and check for notifications
     */
    public int gaugeMonitorNotification() throws Exception {

        GaugeMonitor gaugeMonitor = null;
        try {
            MBeanServer server = MBeanServerFactory.newMBeanServer();

            String domain = server.getDefaultDomain();

            // Create a new GaugeMonitor MBean and add it to the MBeanServer.
            //
            echo(">>> CREATE a new GaugeMonitor MBean");
            ObjectName gaugeMonitorName = new ObjectName(
                            domain + ":type=" + GaugeMonitor.class.getName());
            gaugeMonitor = new GaugeMonitor();
            server.registerMBean(gaugeMonitor, gaugeMonitorName);

            echo(">>> ADD a listener to the GaugeMonitor");
            gaugeMonitor.addNotificationListener(this, null, null);

            //
            // MANAGEMENT OF A STANDARD MBEAN
            //

            echo(">>> CREATE a new ObservedObject MBean");

            ObjectName obsObjName =
                ObjectName.getInstance(domain + ":type=ObservedObject");
            ObservedObject obsObj = new ObservedObject();
            server.registerMBean(obsObj, obsObjName);

            echo(">>> SET the attributes of the GaugeMonitor:");

            gaugeMonitor.addObservedObject(obsObjName);
            echo("\tATTRIBUTE \"ObservedObject\"    = " + obsObjName);

            gaugeMonitor.setObservedAttribute("IntegerAttribute");
            echo("\tATTRIBUTE \"ObservedAttribute\" = IntegerAttribute");

            gaugeMonitor.setNotifyLow(false);
            gaugeMonitor.setNotifyHigh(true);
            echo("\tATTRIBUTE \"Notify Low  Flag\"  = false");
            echo("\tATTRIBUTE \"Notify High Flag\"  = true");

            Double highThreshold = 3.0, lowThreshold = 2.5;
            gaugeMonitor.setThresholds(highThreshold, lowThreshold);
            echo("\tATTRIBUTE \"Low  Threshold\"    = " + lowThreshold);
            echo("\tATTRIBUTE \"High Threshold\"    = " + highThreshold);

            int granularityperiod = 500;
            gaugeMonitor.setGranularityPeriod(granularityperiod);
            echo("\tATTRIBUTE \"GranularityPeriod\" = " + granularityperiod);

            echo(">>> START the GaugeMonitor");
            gaugeMonitor.start();

            // Check if notification was received
            //
            doWait();
            if (messageReceived) {
                echo("\tOK: GaugeMonitor notification received");
            } else {
                echo("\tKO: GaugeMonitor notification missed or not emitted");
                return 1;
            }
        } finally {
            if (gaugeMonitor != null)
                gaugeMonitor.stop();
        }

        return 0;
    }

    /**
     * Update the string and check for notifications
     */
    public int stringMonitorNotification() throws Exception {

        StringMonitor stringMonitor = null;
        try {
            MBeanServer server = MBeanServerFactory.newMBeanServer();

            String domain = server.getDefaultDomain();

            // Create a new StringMonitor MBean and add it to the MBeanServer.
            //
            echo(">>> CREATE a new StringMonitor MBean");
            ObjectName stringMonitorName = new ObjectName(
                            domain + ":type=" + StringMonitor.class.getName());
            stringMonitor = new StringMonitor();
            server.registerMBean(stringMonitor, stringMonitorName);

            echo(">>> ADD a listener to the StringMonitor");
            stringMonitor.addNotificationListener(this, null, null);

            //
            // MANAGEMENT OF A STANDARD MBEAN
            //

            echo(">>> CREATE a new ObservedObject MBean");

            ObjectName obsObjName =
                ObjectName.getInstance(domain + ":type=ObservedObject");
            ObservedObject obsObj = new ObservedObject();
            server.registerMBean(obsObj, obsObjName);

            echo(">>> SET the attributes of the StringMonitor:");

            stringMonitor.addObservedObject(obsObjName);
            echo("\tATTRIBUTE \"ObservedObject\"    = " + obsObjName);

            stringMonitor.setObservedAttribute("StringAttribute");
            echo("\tATTRIBUTE \"ObservedAttribute\" = StringAttribute");

            stringMonitor.setNotifyMatch(true);
            echo("\tATTRIBUTE \"NotifyMatch\"       = true");

            stringMonitor.setNotifyDiffer(false);
            echo("\tATTRIBUTE \"NotifyDiffer\"      = false");

            stringMonitor.setStringToCompare("do_match_now");
            echo("\tATTRIBUTE \"StringToCompare\"   = \"do_match_now\"");

            int granularityperiod = 500;
            stringMonitor.setGranularityPeriod(granularityperiod);
            echo("\tATTRIBUTE \"GranularityPeriod\" = " + granularityperiod);

            echo(">>> START the StringMonitor");
            stringMonitor.start();

            // Check if notification was received
            //
            doWait();
            if (messageReceived) {
                echo("\tOK: StringMonitor notification received");
            } else {
                echo("\tKO: StringMonitor notification missed or not emitted");
                return 1;
            }
        } finally {
            if (stringMonitor != null)
                stringMonitor.stop();
        }

        return 0;
    }

    /**
     * Test the monitor notifications.
     */
    public int monitorNotifications() throws Exception {
        echo(">>> ----------------------------------------");
        messageReceived = false;
        int error = counterMonitorNotification();
        echo(">>> ----------------------------------------");
        messageReceived = false;
        error += gaugeMonitorNotification();
        echo(">>> ----------------------------------------");
        messageReceived = false;
        error += stringMonitorNotification();
        echo(">>> ----------------------------------------");
        return error;
    }

    /*
     * Print message
     */
    private static void echo(String message) {
        System.out.println(message);
    }

    /*
     * Wait messageReceived to be true
     */
    synchronized void doWait() {
        while (!messageReceived) {
            try {
                wait();
            } catch (InterruptedException e) {
                System.err.println("Got unexpected exception: " + e);
                e.printStackTrace();
                break;
            }
        }
    }

    /*
     * Standalone entry point.
     *
     * Run the test and report to stdout.
     */
    public static void main (String args[]) throws Exception {
        NonComparableAttributeValueTest test = new NonComparableAttributeValueTest();
        int error = test.monitorNotifications();
        if (error > 0) {
            echo(">>> Unhappy Bye, Bye!");
            throw new IllegalStateException("Test FAILED: Didn't get all " +
                                            "the notifications that were " +
                                            "expected by the test!");
        } else {
            echo(">>> Happy Bye, Bye!");
        }
    }
}
