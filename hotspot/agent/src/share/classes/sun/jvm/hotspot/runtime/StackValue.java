/*
 * Copyright 2000-2001 Sun Microsystems, Inc.  All Rights Reserved.
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
 *
 */

package sun.jvm.hotspot.runtime;

import java.io.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.utilities.*;

public class StackValue {
  private int       type;
  private OopHandle handleValue;
  private long      integerValue;

  public StackValue() {
    type = BasicType.getTConflict();
  }

  public StackValue(OopHandle h) {
    handleValue = h;
    type = BasicType.getTObject();
  }

  public StackValue(long i) {
    integerValue = i;
    type = BasicType.getTInt();
  }

  /** This returns one of the "enum" values in BasicType.java */
  public int getType() {
    return type;
  }

  public OopHandle getObject() {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(type == BasicType.getTObject(), "type check");
    }
    return handleValue;
  }

  public long getInteger() {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(type == BasicType.getTInt(), "type check");
    }
    return integerValue;
  }

  public boolean equals(Object arg) {
    if (arg == null) {
      return false;
    }

    if (!arg.getClass().equals(getClass())) {
      return false;
    }

    StackValue sv = (StackValue) arg;
    if (type != sv.type) {
      return false;
    }
    if (type == BasicType.getTObject()) {
      return handleValue.equals(sv.handleValue);
    } else if (type == BasicType.getTInt()) {
      return (integerValue == sv.integerValue);
    } else {
      // Conflict type (not yet used)
      return true;
    }
  }

  public int hashCode() {
    if (type == BasicType.getTObject()) {
      return handleValue.hashCode();
    } else {
      // Returns 0 for conflict type
      return (int) integerValue;
    }
  }

  public void print() {
    printOn(System.out);
  }

  public void printOn(PrintStream tty) {
    if (type == BasicType.getTInt()) {
      tty.print(integerValue + " (long) " + (int) integerValue + " (int)");
    } else if (type == BasicType.getTObject()) {
      tty.print("<" + handleValue + ">");
    } else if (type == BasicType.getTConflict()) {
      tty.print("conflict");
    } else {
      throw new RuntimeException("should not reach here");
    }
  }
}
