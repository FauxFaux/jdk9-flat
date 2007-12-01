/*
 * Copyright 2002 Sun Microsystems, Inc.  All Rights Reserved.
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
 *  @test
 *  @bug 4628726
 *  @summary Test class redefinition at each event cross tested with other tests
 *
 *  @author Robert Field
 *
 *  @run build TestScaffold VMConnection TargetAdapter TargetListener
 *  @run compile -g AccessSpecifierTest.java
 *  @run compile -g AfterThreadDeathTest.java
 *  @run compile -g ArrayRangeTest.java
 *  @run compile -g BacktraceFieldTest.java
 *  @run compile -g ClassesByName2Test.java
 *  @run compile -g DebuggerThreadTest.java
 *  @run compile -g DeleteEventRequestsTest.java
 *  @run compile -g ExceptionEvents.java
 *  @run compile -g ExpiredRequestDeletionTest.java
 *  @run compile -g FieldWatchpoints.java
 *  @run build InstanceFilter
 *  @run compile -g LocationTest.java
 *  @run compile -g NewInstanceTest.java
 *  @run compile -g PopSynchronousTest.java
 *  @run compile -g RepStepTarg.java
 *  @run compile -g RequestReflectionTest.java
 *
 *  @run main AccessSpecifierTest -redefstart -redefevent
 *  @run main AfterThreadDeathTest -redefstart -redefevent
 *  @run main ArrayRangeTest -redefstart -redefevent
 *  @run main BacktraceFieldTest -redefstart -redefevent
 *  @run main ClassesByName2Test -redefstart -redefevent
 *  @run main DebuggerThreadTest -redefstart -redefevent
 *  @run main DeleteEventRequestsTest -redefstart -redefevent
 *  @run main/othervm ExceptionEvents -redefstart -redefevent N A StackOverflowCaughtTarg java.lang.Exception
 *  @run main/othervm ExceptionEvents -redefstart -redefevent C A StackOverflowCaughtTarg null
 *  @run main/othervm ExceptionEvents -redefstart -redefevent C A StackOverflowCaughtTarg java.lang.StackOverflowError
 *  @run main/othervm ExceptionEvents -redefstart -redefevent N A StackOverflowCaughtTarg java.lang.NullPointerException
 *  @run main/othervm ExceptionEvents -redefstart -redefevent C T StackOverflowCaughtTarg java.lang.Error
 *  @run main/othervm ExceptionEvents -redefstart -redefevent N T StackOverflowCaughtTarg java.lang.NullPointerException
 *  @run main/othervm ExceptionEvents -redefstart -redefevent N N StackOverflowCaughtTarg java.lang.Exception
 *  @run main/othervm ExceptionEvents -redefstart -redefevent C N StackOverflowCaughtTarg java.lang.Error
 *  @run main/othervm ExceptionEvents -redefstart -redefevent N A StackOverflowUncaughtTarg java.lang.Exception
 *  @run main ExpiredRequestDeletionTest -redefstart -redefevent
 *  @run main/othervm FieldWatchpoints -redefstart -redefevent
 *  @run main/othervm InstanceFilter -redefstart -redefevent
 *  @run main LocationTest -redefstart -redefevent
 *  @run main NewInstanceTest -redefstart -redefevent
 *  @run main RequestReflectionTest -redefstart -redefevent
 */
