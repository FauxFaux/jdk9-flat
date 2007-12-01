/*
 * Copyright 1998 Sun Microsystems, Inc.  All Rights Reserved.
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

// Skeleton class generated by rmic, do not edit.
// Contents subject to change without notice.

public final class ForceLogSnapshot_Skel
    implements java.rmi.server.Skeleton
{
    private static final java.rmi.server.Operation[] operations = {
        new java.rmi.server.Operation("void crash()"),
        new java.rmi.server.Operation("void ping(int, java.lang.String)")
    };

    private static final long interfaceHash = -5865767584502007357L;

    public java.rmi.server.Operation[] getOperations() {
        return (java.rmi.server.Operation[]) operations.clone();
    }

    public void dispatch(java.rmi.Remote obj, java.rmi.server.RemoteCall call, int opnum, long hash)
        throws java.lang.Exception
    {
        if (opnum < 0) {
            if (hash == 8484760490859430950L) {
                opnum = 0;
            } else if (hash == -1519179153769139224L) {
                opnum = 1;
            } else {
                throw new java.rmi.UnmarshalException("invalid method hash");
            }
        } else {
            if (hash != interfaceHash)
                throw new java.rmi.server.SkeletonMismatchException("interface hash mismatch");
        }

        ForceLogSnapshot server = (ForceLogSnapshot) obj;
        switch (opnum) {
        case 0: // crash()
        {
            call.releaseInputStream();
            server.crash();
            try {
                call.getResultStream(true);
            } catch (java.io.IOException e) {
                throw new java.rmi.MarshalException("error marshalling return", e);
            }
            break;
        }

        case 1: // ping(int, String)
        {
            int $param_int_1;
            java.lang.String $param_String_2;
            try {
                java.io.ObjectInput in = call.getInputStream();
                $param_int_1 = in.readInt();
                $param_String_2 = (java.lang.String) in.readObject();
            } catch (java.io.IOException e) {
                throw new java.rmi.UnmarshalException("error unmarshalling arguments", e);
            } catch (java.lang.ClassNotFoundException e) {
                throw new java.rmi.UnmarshalException("error unmarshalling arguments", e);
            } finally {
                call.releaseInputStream();
            }
            server.ping($param_int_1, $param_String_2);
            try {
                call.getResultStream(true);
            } catch (java.io.IOException e) {
                throw new java.rmi.MarshalException("error marshalling return", e);
            }
            break;
        }

        default:
            throw new java.rmi.UnmarshalException("invalid method number");
        }
    }
}
