/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng/othervm -Diters=20000 VarHandleTestMethodHandleAccessByte
 */

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.testng.Assert.*;

public class VarHandleTestMethodHandleAccessByte extends VarHandleBaseTest {
    static final byte static_final_v = (byte)0x01;

    static byte static_v;

    final byte final_v = (byte)0x01;

    byte v;

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    @BeforeClass
    public void setup() throws Exception {
        vhFinalField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodHandleAccessByte.class, "final_v", byte.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodHandleAccessByte.class, "v", byte.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodHandleAccessByte.class, "static_final_v", byte.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodHandleAccessByte.class, "static_v", byte.class);

        vhArray = MethodHandles.arrayElementVarHandle(byte[].class);
    }


    @DataProvider
    public Object[][] accessTestCaseProvider() throws Exception {
        List<AccessTestCase<?>> cases = new ArrayList<>();

        for (VarHandleToMethodHandle f : VarHandleToMethodHandle.values()) {
            cases.add(new MethodHandleAccessTestCase("Instance field",
                                                     vhField, f, hs -> testInstanceField(this, hs)));
            cases.add(new MethodHandleAccessTestCase("Instance field unsupported",
                                                     vhField, f, hs -> testInstanceFieldUnsupported(this, hs),
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Static field",
                                                     vhStaticField, f, VarHandleTestMethodHandleAccessByte::testStaticField));
            cases.add(new MethodHandleAccessTestCase("Static field unsupported",
                                                     vhStaticField, f, VarHandleTestMethodHandleAccessByte::testStaticFieldUnsupported,
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Array",
                                                     vhArray, f, VarHandleTestMethodHandleAccessByte::testArray));
            cases.add(new MethodHandleAccessTestCase("Array unsupported",
                                                     vhArray, f, VarHandleTestMethodHandleAccessByte::testArrayUnsupported,
                                                     false));
            cases.add(new MethodHandleAccessTestCase("Array index out of bounds",
                                                     vhArray, f, VarHandleTestMethodHandleAccessByte::testArrayIndexOutOfBounds,
                                                     false));
        }

        // Work around issue with jtreg summary reporting which truncates
        // the String result of Object.toString to 30 characters, hence
        // the first dummy argument
        return cases.stream().map(tc -> new Object[]{tc.toString(), tc}).toArray(Object[][]::new);
    }

    @Test(dataProvider = "accessTestCaseProvider")
    public <T> void testAccess(String desc, AccessTestCase<T> atc) throws Throwable {
        T t = atc.get();
        int iters = atc.requiresLoop() ? ITERS : 1;
        for (int c = 0; c < iters; c++) {
            atc.testAccess(t);
        }
    }


    static void testInstanceField(VarHandleTestMethodHandleAccessByte recv, Handles hs) throws Throwable {
        // Plain
        {
            hs.get(TestAccessMode.SET).invokeExact(recv, (byte)0x01);
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, (byte)0x01, "set byte value");
        }


        // Volatile
        {
            hs.get(TestAccessMode.SET_VOLATILE).invokeExact(recv, (byte)0x23);
            byte x = (byte) hs.get(TestAccessMode.GET_VOLATILE).invokeExact(recv);
            assertEquals(x, (byte)0x23, "setVolatile byte value");
        }

        // Lazy
        {
            hs.get(TestAccessMode.SET_RELEASE).invokeExact(recv, (byte)0x01);
            byte x = (byte) hs.get(TestAccessMode.GET_ACQUIRE).invokeExact(recv);
            assertEquals(x, (byte)0x01, "setRelease byte value");
        }

        // Opaque
        {
            hs.get(TestAccessMode.SET_OPAQUE).invokeExact(recv, (byte)0x23);
            byte x = (byte) hs.get(TestAccessMode.GET_OPAQUE).invokeExact(recv);
            assertEquals(x, (byte)0x23, "setOpaque byte value");
        }

        hs.get(TestAccessMode.SET).invokeExact(recv, (byte)0x01);

        // Compare
        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(recv, (byte)0x01, (byte)0x23);
            assertEquals(r, true, "success compareAndSet byte");
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, (byte)0x23, "success compareAndSet byte value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(recv, (byte)0x01, (byte)0x45);
            assertEquals(r, false, "failing compareAndSet byte");
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, (byte)0x23, "failing compareAndSet byte value");
        }

        {
            byte r = (byte) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_VOLATILE).invokeExact(recv, (byte)0x23, (byte)0x01);
            assertEquals(r, (byte)0x23, "success compareAndExchangeVolatile byte");
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, (byte)0x01, "success compareAndExchangeVolatile byte value");
        }

        {
            byte r = (byte) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_VOLATILE).invokeExact(recv, (byte)0x23, (byte)0x45);
            assertEquals(r, (byte)0x01, "failing compareAndExchangeVolatile byte");
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, (byte)0x01, "failing compareAndExchangeVolatile byte value");
        }

        {
            byte r = (byte) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(recv, (byte)0x01, (byte)0x23);
            assertEquals(r, (byte)0x01, "success compareAndExchangeAcquire byte");
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, (byte)0x23, "success compareAndExchangeAcquire byte value");
        }

        {
            byte r = (byte) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(recv, (byte)0x01, (byte)0x45);
            assertEquals(r, (byte)0x23, "failing compareAndExchangeAcquire byte");
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, (byte)0x23, "failing compareAndExchangeAcquire byte value");
        }

        {
            byte r = (byte) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(recv, (byte)0x23, (byte)0x01);
            assertEquals(r, (byte)0x23, "success compareAndExchangeRelease byte");
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, (byte)0x01, "success compareAndExchangeRelease byte value");
        }

        {
            byte r = (byte) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(recv, (byte)0x23, (byte)0x45);
            assertEquals(r, (byte)0x01, "failing compareAndExchangeRelease byte");
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, (byte)0x01, "failing compareAndExchangeRelease byte value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET).invokeExact(recv, (byte)0x01, (byte)0x23);
            }
            assertEquals(success, true, "weakCompareAndSet byte");
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, (byte)0x23, "weakCompareAndSet byte value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE).invokeExact(recv, (byte)0x23, (byte)0x01);
            }
            assertEquals(success, true, "weakCompareAndSetAcquire byte");
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, (byte)0x01, "weakCompareAndSetAcquire byte");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE).invokeExact(recv, (byte)0x01, (byte)0x23);
            }
            assertEquals(success, true, "weakCompareAndSetRelease byte");
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, (byte)0x23, "weakCompareAndSetRelease byte");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_VOLATILE).invokeExact(recv, (byte)0x23, (byte)0x01);
            }
            assertEquals(success, true, "weakCompareAndSetVolatile byte");
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, (byte)0x01, "weakCompareAndSetVolatile byte");
        }

        // Compare set and get
        {
            byte o = (byte) hs.get(TestAccessMode.GET_AND_SET).invokeExact(recv, (byte)0x23);
            assertEquals(o, (byte)0x01, "getAndSet byte");
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, (byte)0x23, "getAndSet byte value");
        }

        hs.get(TestAccessMode.SET).invokeExact(recv, (byte)0x01);

        // get and add, add and get
        {
            byte o = (byte) hs.get(TestAccessMode.GET_AND_ADD).invokeExact(recv, (byte)0x45);
            assertEquals(o, (byte)0x01, "getAndAdd byte");
            byte c = (byte) hs.get(TestAccessMode.ADD_AND_GET).invokeExact(recv, (byte)0x45);
            assertEquals(c, (byte)((byte)0x01 + (byte)0x45 + (byte)0x45), "getAndAdd byte value");
        }
    }

    static void testInstanceFieldUnsupported(VarHandleTestMethodHandleAccessByte recv, Handles hs) throws Throwable {

    }


    static void testStaticField(Handles hs) throws Throwable {
        // Plain
        {
            hs.get(TestAccessMode.SET).invokeExact((byte)0x01);
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, (byte)0x01, "set byte value");
        }


        // Volatile
        {
            hs.get(TestAccessMode.SET_VOLATILE).invokeExact((byte)0x23);
            byte x = (byte) hs.get(TestAccessMode.GET_VOLATILE).invokeExact();
            assertEquals(x, (byte)0x23, "setVolatile byte value");
        }

        // Lazy
        {
            hs.get(TestAccessMode.SET_RELEASE).invokeExact((byte)0x01);
            byte x = (byte) hs.get(TestAccessMode.GET_ACQUIRE).invokeExact();
            assertEquals(x, (byte)0x01, "setRelease byte value");
        }

        // Opaque
        {
            hs.get(TestAccessMode.SET_OPAQUE).invokeExact((byte)0x23);
            byte x = (byte) hs.get(TestAccessMode.GET_OPAQUE).invokeExact();
            assertEquals(x, (byte)0x23, "setOpaque byte value");
        }

        hs.get(TestAccessMode.SET).invokeExact((byte)0x01);

        // Compare
        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact((byte)0x01, (byte)0x23);
            assertEquals(r, true, "success compareAndSet byte");
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, (byte)0x23, "success compareAndSet byte value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact((byte)0x01, (byte)0x45);
            assertEquals(r, false, "failing compareAndSet byte");
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, (byte)0x23, "failing compareAndSet byte value");
        }

        {
            byte r = (byte) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_VOLATILE).invokeExact((byte)0x23, (byte)0x01);
            assertEquals(r, (byte)0x23, "success compareAndExchangeVolatile byte");
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, (byte)0x01, "success compareAndExchangeVolatile byte value");
        }

        {
            byte r = (byte) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_VOLATILE).invokeExact((byte)0x23, (byte)0x45);
            assertEquals(r, (byte)0x01, "failing compareAndExchangeVolatile byte");
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, (byte)0x01, "failing compareAndExchangeVolatile byte value");
        }

        {
            byte r = (byte) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact((byte)0x01, (byte)0x23);
            assertEquals(r, (byte)0x01, "success compareAndExchangeAcquire byte");
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, (byte)0x23, "success compareAndExchangeAcquire byte value");
        }

        {
            byte r = (byte) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact((byte)0x01, (byte)0x45);
            assertEquals(r, (byte)0x23, "failing compareAndExchangeAcquire byte");
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, (byte)0x23, "failing compareAndExchangeAcquire byte value");
        }

        {
            byte r = (byte) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact((byte)0x23, (byte)0x01);
            assertEquals(r, (byte)0x23, "success compareAndExchangeRelease byte");
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, (byte)0x01, "success compareAndExchangeRelease byte value");
        }

        {
            byte r = (byte) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact((byte)0x23, (byte)0x45);
            assertEquals(r, (byte)0x01, "failing compareAndExchangeRelease byte");
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, (byte)0x01, "failing compareAndExchangeRelease byte value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET).invokeExact((byte)0x01, (byte)0x23);
            }
            assertEquals(success, true, "weakCompareAndSet byte");
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, (byte)0x23, "weakCompareAndSet byte value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE).invokeExact((byte)0x23, (byte)0x01);
            }
            assertEquals(success, true, "weakCompareAndSetAcquire byte");
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, (byte)0x01, "weakCompareAndSetAcquire byte");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE).invokeExact((byte)0x01, (byte)0x23);
            }
            assertEquals(success, true, "weakCompareAndSetRelease byte");
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, (byte)0x23, "weakCompareAndSetRelease byte");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_VOLATILE).invokeExact((byte)0x23, (byte)0x01);
            }
            assertEquals(success, true, "weakCompareAndSetVolatile byte");
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, (byte)0x01, "weakCompareAndSetVolatile byte");
        }

        // Compare set and get
        {
            byte o = (byte) hs.get(TestAccessMode.GET_AND_SET).invokeExact( (byte)0x23);
            assertEquals(o, (byte)0x01, "getAndSet byte");
            byte x = (byte) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, (byte)0x23, "getAndSet byte value");
        }

        hs.get(TestAccessMode.SET).invokeExact((byte)0x01);

        // get and add, add and get
        {
            byte o = (byte) hs.get(TestAccessMode.GET_AND_ADD).invokeExact( (byte)0x45);
            assertEquals(o, (byte)0x01, "getAndAdd byte");
            byte c = (byte) hs.get(TestAccessMode.ADD_AND_GET).invokeExact((byte)0x45);
            assertEquals(c, (byte)((byte)0x01 + (byte)0x45 + (byte)0x45), "getAndAdd byte value");
        }
    }

    static void testStaticFieldUnsupported(Handles hs) throws Throwable {

    }


    static void testArray(Handles hs) throws Throwable {
        byte[] array = new byte[10];

        for (int i = 0; i < array.length; i++) {
            // Plain
            {
                hs.get(TestAccessMode.SET).invokeExact(array, i, (byte)0x01);
                byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, (byte)0x01, "get byte value");
            }


            // Volatile
            {
                hs.get(TestAccessMode.SET_VOLATILE).invokeExact(array, i, (byte)0x23);
                byte x = (byte) hs.get(TestAccessMode.GET_VOLATILE).invokeExact(array, i);
                assertEquals(x, (byte)0x23, "setVolatile byte value");
            }

            // Lazy
            {
                hs.get(TestAccessMode.SET_RELEASE).invokeExact(array, i, (byte)0x01);
                byte x = (byte) hs.get(TestAccessMode.GET_ACQUIRE).invokeExact(array, i);
                assertEquals(x, (byte)0x01, "setRelease byte value");
            }

            // Opaque
            {
                hs.get(TestAccessMode.SET_OPAQUE).invokeExact(array, i, (byte)0x23);
                byte x = (byte) hs.get(TestAccessMode.GET_OPAQUE).invokeExact(array, i);
                assertEquals(x, (byte)0x23, "setOpaque byte value");
            }

            hs.get(TestAccessMode.SET).invokeExact(array, i, (byte)0x01);

            // Compare
            {
                boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(array, i, (byte)0x01, (byte)0x23);
                assertEquals(r, true, "success compareAndSet byte");
                byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, (byte)0x23, "success compareAndSet byte value");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(array, i, (byte)0x01, (byte)0x45);
                assertEquals(r, false, "failing compareAndSet byte");
                byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, (byte)0x23, "failing compareAndSet byte value");
            }

            {
                byte r = (byte) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_VOLATILE).invokeExact(array, i, (byte)0x23, (byte)0x01);
                assertEquals(r, (byte)0x23, "success compareAndExchangeVolatile byte");
                byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, (byte)0x01, "success compareAndExchangeVolatile byte value");
            }

            {
                byte r = (byte) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_VOLATILE).invokeExact(array, i, (byte)0x23, (byte)0x45);
                assertEquals(r, (byte)0x01, "failing compareAndExchangeVolatile byte");
                byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, (byte)0x01, "failing compareAndExchangeVolatile byte value");
            }

            {
                byte r = (byte) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(array, i, (byte)0x01, (byte)0x23);
                assertEquals(r, (byte)0x01, "success compareAndExchangeAcquire byte");
                byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, (byte)0x23, "success compareAndExchangeAcquire byte value");
            }

            {
                byte r = (byte) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(array, i, (byte)0x01, (byte)0x45);
                assertEquals(r, (byte)0x23, "failing compareAndExchangeAcquire byte");
                byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, (byte)0x23, "failing compareAndExchangeAcquire byte value");
            }

            {
                byte r = (byte) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(array, i, (byte)0x23, (byte)0x01);
                assertEquals(r, (byte)0x23, "success compareAndExchangeRelease byte");
                byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, (byte)0x01, "success compareAndExchangeRelease byte value");
            }

            {
                byte r = (byte) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(array, i, (byte)0x23, (byte)0x45);
                assertEquals(r, (byte)0x01, "failing compareAndExchangeRelease byte");
                byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, (byte)0x01, "failing compareAndExchangeRelease byte value");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET).invokeExact(array, i, (byte)0x01, (byte)0x23);
                }
                assertEquals(success, true, "weakCompareAndSet byte");
                byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, (byte)0x23, "weakCompareAndSet byte value");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE).invokeExact(array, i, (byte)0x23, (byte)0x01);
                }
                assertEquals(success, true, "weakCompareAndSetAcquire byte");
                byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, (byte)0x01, "weakCompareAndSetAcquire byte");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE).invokeExact(array, i, (byte)0x01, (byte)0x23);
                }
                assertEquals(success, true, "weakCompareAndSetRelease byte");
                byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, (byte)0x23, "weakCompareAndSetRelease byte");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_VOLATILE).invokeExact(array, i, (byte)0x23, (byte)0x01);
                }
                assertEquals(success, true, "weakCompareAndSetVolatile byte");
                byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, (byte)0x01, "weakCompareAndSetVolatile byte");
            }

            // Compare set and get
            {
                byte o = (byte) hs.get(TestAccessMode.GET_AND_SET).invokeExact(array, i, (byte)0x23);
                assertEquals(o, (byte)0x01, "getAndSet byte");
                byte x = (byte) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, (byte)0x23, "getAndSet byte value");
            }

            hs.get(TestAccessMode.SET).invokeExact(array, i, (byte)0x01);

            // get and add, add and get
            {
                byte o = (byte) hs.get(TestAccessMode.GET_AND_ADD).invokeExact(array, i, (byte)0x45);
                assertEquals(o, (byte)0x01, "getAndAdd byte");
                byte c = (byte) hs.get(TestAccessMode.ADD_AND_GET).invokeExact(array, i, (byte)0x45);
                assertEquals(c, (byte)((byte)0x01 + (byte)0x45 + (byte)0x45), "getAndAdd byte value");
            }
        }
    }

    static void testArrayUnsupported(Handles hs) throws Throwable {
        byte[] array = new byte[10];

        final int i = 0;

    }

    static void testArrayIndexOutOfBounds(Handles hs) throws Throwable {
        byte[] array = new byte[10];

        for (int i : new int[]{-1, Integer.MIN_VALUE, 10, 11, Integer.MAX_VALUE}) {
            final int ci = i;

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET)) {
                checkIOOBE(am, () -> {
                    byte x = (byte) hs.get(am).invokeExact(array, ci);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.SET)) {
                checkIOOBE(am, () -> {
                    hs.get(am).invokeExact(array, ci, (byte)0x01);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_SET)) {
                checkIOOBE(am, () -> {
                    boolean r = (boolean) hs.get(am).invokeExact(array, ci, (byte)0x01, (byte)0x23);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_EXCHANGE)) {
                checkIOOBE(am, () -> {
                    byte r = (byte) hs.get(am).invokeExact(array, ci, (byte)0x23, (byte)0x01);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_SET)) {
                checkIOOBE(am, () -> {
                    byte o = (byte) hs.get(am).invokeExact(array, ci, (byte)0x01);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_ADD)) {
                checkIOOBE(am, () -> {
                    byte o = (byte) hs.get(am).invokeExact(array, ci, (byte)0x45);
                });
            }
        }
    }
}

