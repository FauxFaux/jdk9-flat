/*
 * Copyright (c) 2006, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.tools.jinfo;

import java.util.Arrays;
import java.io.IOException;
import java.io.InputStream;

import com.sun.tools.attach.VirtualMachine;

import sun.tools.attach.HotSpotVirtualMachine;

/*
 * This class is the main class for the JInfo utility. It parses its arguments
 * and decides if the command should be satisfied using the VM attach mechanism
 * or an SA tool.
 */
final public class JInfo {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage(1); // no arguments
        }
        checkForUnsupportedOptions(args);

        boolean doFlag = false;
        boolean doFlags = false;
        boolean doSysprops = false;

        // Parse the options (arguments starting with "-" )
        int optionCount = 0;
        while (optionCount < args.length) {
            String arg = args[optionCount];
            if (!arg.startsWith("-")) {
                break;
            }

            optionCount++;

            if (arg.equals("-help") || arg.equals("-h")) {
                usage(0);
            }

            if (arg.equals("-flag")) {
                doFlag = true;
                continue;
            }

            if (arg.equals("-flags")) {
                doFlags = true;
                continue;
            }

            if (arg.equals("-sysprops")) {
                doSysprops = true;
                continue;
            }
        }

        // Next we check the parameter count. -flag allows extra parameters
        int paramCount = args.length - optionCount;
        if ((doFlag && paramCount != 2) || (paramCount != 1)) {
            usage(1);
        }

        if (!doFlag && !doFlags && !doSysprops) {
            // Print flags and sysporps if no options given
            sysprops(args[optionCount]);
            System.out.println();
            flags(args[optionCount]);
            System.out.println();
            commandLine(args[optionCount]);
        }

        if (doFlag) {
            flag(args[optionCount+1], args[optionCount]);
        }
        else {
            if (doFlags) {
                flags(args[optionCount]);
            }
            else if (doSysprops) {
                sysprops(args[optionCount]);
            }
        }
    }

    private static void flag(String pid, String option) throws IOException {
        HotSpotVirtualMachine vm = (HotSpotVirtualMachine) attach(pid);
        String flag;
        InputStream in;
        int index = option.indexOf('=');
        if (index != -1) {
            flag = option.substring(0, index);
            String value = option.substring(index + 1);
            in = vm.setFlag(flag, value);
        } else {
            char c = option.charAt(0);
            switch (c) {
                case '+':
                    flag = option.substring(1);
                    in = vm.setFlag(flag, "1");
                    break;
                case '-':
                    flag = option.substring(1);
                    in = vm.setFlag(flag, "0");
                    break;
                default:
                    flag = option;
                    in = vm.printFlag(flag);
                    break;
            }
        }

        drain(vm, in);
    }

    private static void flags(String pid) throws IOException {
        HotSpotVirtualMachine vm = (HotSpotVirtualMachine) attach(pid);
        InputStream in = vm.executeJCmd("VM.flags");
        System.out.println("VM Flags:");
        drain(vm, in);
    }

    private static void commandLine(String pid) throws IOException {
        HotSpotVirtualMachine vm = (HotSpotVirtualMachine) attach(pid);
        InputStream in = vm.executeJCmd("VM.command_line");
        drain(vm, in);
    }

    private static void sysprops(String pid) throws IOException {
        HotSpotVirtualMachine vm = (HotSpotVirtualMachine) attach(pid);
        InputStream in = vm.executeJCmd("VM.system_properties");
        System.out.println("Java System Properties:");
        drain(vm, in);
    }

    // Attach to <pid>, exiting if we fail to attach
    private static VirtualMachine attach(String pid) {
        try {
            return VirtualMachine.attach(pid);
        } catch (Exception x) {
            String msg = x.getMessage();
            if (msg != null) {
                System.err.println(pid + ": " + msg);
            } else {
                x.printStackTrace();
            }
            System.exit(1);
            return null; // keep compiler happy
        }
    }

    // Read the stream from the target VM until EOF, then detach
    private static void drain(VirtualMachine vm, InputStream in) throws IOException {
        // read to EOF and just print output
        byte b[] = new byte[256];
        int n;
        do {
            n = in.read(b);
            if (n > 0) {
                String s = new String(b, 0, n, "UTF-8");
                System.out.print(s);
            }
        } while (n > 0);
        in.close();
        vm.detach();
    }

    private static void checkForUnsupportedOptions(String[] args) {
        // Check arguments for -F, and non-numeric value
        // and warn the user that SA is not supported anymore

        int paramCount = 0;

        for (String s : args) {
            if (s.equals("-F")) {
                SAOptionError("-F option used");
            }

            if (! s.startsWith("-")) {
                if (! s.matches("[0-9]+")) {
                   SAOptionError("non PID argument");
                }
                paramCount += 1;
            }
        }

        if (paramCount > 1) {
            SAOptionError("More than one non-option argument");
        }
    }

    private static void SAOptionError(String msg) {
        System.err.println("Error: " + msg);
        System.err.println("Cannot connect to core dump or remote debug server. Use jhsdb jinfo instead");
        System.exit(1);
    }

     // print usage message
    private static void usage(int exit) {
        System.err.println("Usage:");
        System.err.println("    jinfo <option> <pid>");
        System.err.println("       (to connect to a running process)");
        System.err.println("");
        System.err.println("where <option> is one of:");
        System.err.println("    -flag <name>         to print the value of the named VM flag");
        System.err.println("    -flag [+|-]<name>    to enable or disable the named VM flag");
        System.err.println("    -flag <name>=<value> to set the named VM flag to the given value");
        System.err.println("    -flags               to print VM flags");
        System.err.println("    -sysprops            to print Java system properties");
        System.err.println("    <no option>          to print both VM flags and system properties");
        System.err.println("    -h | -help           to print this help message");
        System.exit(exit);
    }
}
