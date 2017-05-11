/*
 * Copyright (c) 2007, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4742177
 * @summary Re-test IPv6 (and specifically MulticastSocket) with latest Linux & USAGI code
 */
import java.net.*;
import java.util.*;


public class SetOutgoingIf {
    private static int PORT = 9001;
    private static String osname;

    static boolean isWindows() {
        if (osname == null)
            osname = System.getProperty("os.name");
        return osname.contains("Windows");
    }

    static boolean isMacOS() {
        return System.getProperty("os.name").contains("OS X");
    }

    private static boolean hasIPv6() throws Exception {
        List<NetworkInterface> nics = Collections.list(
                                        NetworkInterface.getNetworkInterfaces());
        for (NetworkInterface nic : nics) {
            List<InetAddress> addrs = Collections.list(nic.getInetAddresses());
            for (InetAddress addr : addrs) {
                if (addr instanceof Inet6Address)
                    return true;
            }
        }

        return false;
    }

    public static void main(String[] args) throws Exception {
        if (isWindows()) {
            System.out.println("The test only run on non-Windows OS. Bye.");
            return;
        }

        if (!hasIPv6()) {
            System.out.println("No IPv6 available. Bye.");
            return;
        }

        // We need 2 or more network interfaces to run the test
        //
        List<NetIf> netIfs = new ArrayList<NetIf>();
        int index = 1;
        for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            // we should use only network interfaces with multicast support which are in "up" state
            if (!nic.isLoopback() && nic.supportsMulticast() && nic.isUp() && !isTestExcludedInterface(nic)) {
                NetIf netIf = NetIf.create(nic);

                // now determine what (if any) type of addresses are assigned to this interface
                for (InetAddress addr : Collections.list(nic.getInetAddresses())) {
                    if (addr.isAnyLocalAddress())
                        continue;

                    System.out.println("    addr " + addr);
                    if (addr instanceof Inet4Address) {
                        netIf.ipv4Address(true);
                    } else if (addr instanceof Inet6Address) {
                        netIf.ipv6Address(true);
                    }
                }
                if (netIf.ipv4Address() || netIf.ipv6Address()) {
                    netIf.index(index++);
                    netIfs.add(netIf);
                    debug("Using: " + nic);
                }
            } else {
                System.out.println("Ignore NetworkInterface nic == " + nic);
            }
        }
        if (netIfs.size() <= 1) {
            System.out.println("Need 2 or more network interfaces to run. Bye.");
            return;
        }

        // We will send packets to one ipv4, and one ipv6
        // multicast group using each network interface :-
        //      224.1.1.1        --|
        //      ff02::1:1        --|--> using network interface #1
        //      224.1.2.1        --|
        //      ff02::1:2        --|--> using network interface #2
        // and so on.
        //
        for (NetIf netIf : netIfs) {
            int NetIfIndex = netIf.index();
            List<InetAddress> groups = new ArrayList<InetAddress>();

            if (netIf.ipv4Address()) {
                InetAddress groupv4 = InetAddress.getByName("224.1." + NetIfIndex + ".1");
                groups.add(groupv4);
            }
            if (netIf.ipv6Address()) {
                InetAddress groupv6 = InetAddress.getByName("ff02::1:" + NetIfIndex);
                groups.add(groupv6);
            }

            debug("Adding " + groups + " groups for " + netIf.nic().getName());
            netIf.groups(groups);

            // use a separated thread to send to those 2 groups
            Thread sender = new Thread(new Sender(netIf,
                                                  groups,
                                                  PORT));
            sender.setDaemon(true); // we want sender to stop when main thread exits
            sender.start();
        }

        // try to receive on each group, then check if the packet comes
        // from the expected network interface
        //
        byte[] buf = new byte[1024];
        for (NetIf netIf : netIfs) {
            NetworkInterface nic = netIf.nic();
            for (InetAddress group : netIf.groups()) {
                MulticastSocket mcastsock = new MulticastSocket(PORT);
                mcastsock.setSoTimeout(5000);   // 5 second
                DatagramPacket packet = new DatagramPacket(buf, 0, buf.length);

                // the interface supports the IP multicast group
                debug("Joining " + group + " on " + nic.getName());
                mcastsock.joinGroup(new InetSocketAddress(group, PORT), nic);

                try {
                    mcastsock.receive(packet);
                    debug("received packet on " + packet.getAddress());
                } catch (Exception e) {
                    // test failed if any exception
                    throw new RuntimeException(e);
                }

                // now check which network interface this packet comes from
                NetworkInterface from = NetworkInterface.getByInetAddress(packet.getAddress());
                NetworkInterface shouldbe = nic;
                if (from != null) {
                    if (!from.equals(shouldbe)) {
                        System.out.println("Packets on group "
                                        + group + " should come from "
                                        + shouldbe.getName() + ", but came from "
                                        + from.getName());
                    }
                }

                mcastsock.leaveGroup(new InetSocketAddress(group, PORT), nic);
            }
        }
    }

    private static boolean isTestExcludedInterface(NetworkInterface nif) {
        if (isMacOS() && nif.getName().contains("awdl"))
            return true;
        String dName = nif.getDisplayName();
        if (isWindows() && dName != null && dName.contains("Teredo"))
            return true;
        return false;
    }

    private static boolean debug = true;

    static void debug(String message) {
        if (debug)
            System.out.println(message);
    }
}

class Sender implements Runnable {
    private NetIf netIf;
    private List<InetAddress> groups;
    private int port;

    public Sender(NetIf netIf,
                  List<InetAddress> groups,
                  int port) {
        this.netIf = netIf;
        this.groups = groups;
        this.port = port;
    }

    public void run() {
        try {
            MulticastSocket mcastsock = new MulticastSocket();
            mcastsock.setNetworkInterface(netIf.nic());
            List<DatagramPacket> packets = new LinkedList<DatagramPacket>();

            byte[] buf = "hello world".getBytes();
            for (InetAddress group : groups) {
                packets.add(new DatagramPacket(buf, buf.length, new InetSocketAddress(group, port)));
            }

            for (;;) {
                for (DatagramPacket packet : packets)
                    mcastsock.send(packet);

                Thread.sleep(1000);   // sleep 1 second
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

@SuppressWarnings("unchecked")
class NetIf {
    private boolean ipv4Address; //false
    private boolean ipv6Address; //false
    private int index;
    List<InetAddress> groups = Collections.EMPTY_LIST;
    private final NetworkInterface nic;

    private NetIf(NetworkInterface nic) {
        this.nic = nic;
    }

    static NetIf create(NetworkInterface nic) {
        return new NetIf(nic);
    }

    NetworkInterface nic() {
        return nic;
    }

    boolean ipv4Address() {
        return ipv4Address;
    }

    void ipv4Address(boolean ipv4Address) {
        this.ipv4Address = ipv4Address;
    }

    boolean ipv6Address() {
        return ipv6Address;
    }

    void ipv6Address(boolean ipv6Address) {
        this.ipv6Address = ipv6Address;
    }

    int index() {
        return index;
    }

    void index(int index) {
        this.index = index;
    }

    List<InetAddress> groups() {
        return groups;
    }

    void groups(List<InetAddress> groups) {
        this.groups = groups;
    }
}

