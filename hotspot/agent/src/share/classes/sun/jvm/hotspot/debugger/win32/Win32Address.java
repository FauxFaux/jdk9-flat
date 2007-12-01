/*
 * Copyright 2000-2002 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.jvm.hotspot.debugger.win32;

import sun.jvm.hotspot.debugger.*;

class Win32Address implements Address {
  protected Win32Debugger debugger;
  protected long addr;

  Win32Address(Win32Debugger debugger, long addr) {
    this.debugger = debugger;
    this.addr = addr;
  }

  //
  // Basic Java routines
  //

  public boolean equals(Object arg) {
    if (arg == null) {
      return false;
    }

    if (!(arg instanceof Win32Address)) {
      return false;
    }

    return (addr == ((Win32Address) arg).addr);
  }

  public int hashCode() {
    // FIXME: suggestions on a better hash code?
    return (int) addr;
  }

  public String toString() {
    return debugger.addressValueToString(addr);
  }

  //
  // C/C++-related routines
  //

  public long getCIntegerAt(long offset, long numBytes, boolean isUnsigned) throws UnalignedAddressException, UnmappedAddressException {
    return debugger.readCInteger(addr + offset, numBytes, isUnsigned);
  }

  public Address getAddressAt(long offset) throws UnalignedAddressException, UnmappedAddressException {
    return debugger.readAddress(addr + offset);
  }

  //
  // Java-related routines
  //

  public boolean getJBooleanAt(long offset) throws UnalignedAddressException, UnmappedAddressException {
    return debugger.readJBoolean(addr + offset);
  }

  public byte getJByteAt(long offset) throws UnalignedAddressException, UnmappedAddressException {
    return debugger.readJByte(addr + offset);
  }

  public char getJCharAt(long offset) throws UnalignedAddressException, UnmappedAddressException {
    return debugger.readJChar(addr + offset);
  }

  public double getJDoubleAt(long offset) throws UnalignedAddressException, UnmappedAddressException {
    return debugger.readJDouble(addr + offset);
  }

  public float getJFloatAt(long offset) throws UnalignedAddressException, UnmappedAddressException {
    return debugger.readJFloat(addr + offset);
  }

  public int getJIntAt(long offset) throws UnalignedAddressException, UnmappedAddressException {
    return debugger.readJInt(addr + offset);
  }

  public long getJLongAt(long offset) throws UnalignedAddressException, UnmappedAddressException {
    return debugger.readJLong(addr + offset);
  }

  public short getJShortAt(long offset) throws UnalignedAddressException, UnmappedAddressException {
    return debugger.readJShort(addr + offset);
  }

  public OopHandle getOopHandleAt(long offset)
    throws UnalignedAddressException, UnmappedAddressException, NotInHeapException {
    return debugger.readOopHandle(addr + offset);
  }

  //
  // C/C++-related mutators
  //

  public void setCIntegerAt(long offset, long numBytes, long value) {
    debugger.writeCInteger(addr + offset, numBytes, value);
  }
  public void setAddressAt(long offset, Address value) {
    debugger.writeAddress(addr + offset, (Win32Address) value);
  }

  //
  // Java-related mutators
  //

  public void       setJBooleanAt      (long offset, boolean value)
    throws UnmappedAddressException, UnalignedAddressException {
    debugger.writeJBoolean(addr + offset, value);
  }
  public void       setJByteAt         (long offset, byte value)
    throws UnmappedAddressException, UnalignedAddressException {
    debugger.writeJByte(addr + offset, value);
  }
  public void       setJCharAt         (long offset, char value)
    throws UnmappedAddressException, UnalignedAddressException {
    debugger.writeJChar(addr + offset, value);
  }
  public void       setJDoubleAt       (long offset, double value)
    throws UnmappedAddressException, UnalignedAddressException {
    debugger.writeJDouble(addr + offset, value);
  }
  public void       setJFloatAt        (long offset, float value)
    throws UnmappedAddressException, UnalignedAddressException {
    debugger.writeJFloat(addr + offset, value);
  }
  public void       setJIntAt          (long offset, int value)
    throws UnmappedAddressException, UnalignedAddressException {
    debugger.writeJInt(addr + offset, value);
  }
  public void       setJLongAt         (long offset, long value)
    throws UnmappedAddressException, UnalignedAddressException {
    debugger.writeJLong(addr + offset, value);
  }
  public void       setJShortAt        (long offset, short value)
    throws UnmappedAddressException, UnalignedAddressException {
    debugger.writeJShort(addr + offset, value);
  }
  public void       setOopHandleAt     (long offset, OopHandle value)
    throws UnmappedAddressException, UnalignedAddressException {
    debugger.writeOopHandle(addr + offset, (Win32OopHandle) value);
  }

  //
  // Arithmetic operations -- necessary evil.
  //

  public Address    addOffsetTo       (long offset) throws UnsupportedOperationException {
    long value = addr + offset;
    if (value == 0) {
      return null;
    }
    return new Win32Address(debugger, value);
  }

  public OopHandle  addOffsetToAsOopHandle(long offset) throws UnsupportedOperationException {
    long value = addr + offset;
    if (value == 0) {
      return null;
    }
    return new Win32OopHandle(debugger, value);
  }

  /** (FIXME: any signed/unsigned issues? Should this work for
      OopHandles?) */
  public long       minus(Address arg) {
    if (arg == null) {
      return addr;
    }
    return addr - ((Win32Address) arg).addr;
  }

  // Two's complement representation.
  // All negative numbers are larger than positive numbers.
  // Numbers with the same sign can be compared normally.
  // Test harness is below in main().

  public boolean    lessThan          (Address a) {
    if (a == null) {
      return false;
    }
    Win32Address arg = (Win32Address) a;
    if ((addr >= 0) && (arg.addr < 0)) {
      return true;
    }
    if ((addr < 0) && (arg.addr >= 0)) {
      return false;
    }
    return (addr < arg.addr);
  }

  public boolean    lessThanOrEqual   (Address a) {
    if (a == null) {
      return false;
    }
    Win32Address arg = (Win32Address) a;
    if ((addr >= 0) && (arg.addr < 0)) {
      return true;
    }
    if ((addr < 0) && (arg.addr >= 0)) {
      return false;
    }
    return (addr <= arg.addr);
  }

  public boolean    greaterThan       (Address a) {
    if (a == null) {
      return true;
    }
    Win32Address arg = (Win32Address) a;
    if ((addr >= 0) && (arg.addr < 0)) {
      return false;
    }
    if ((addr < 0) && (arg.addr >= 0)) {
      return true;
    }
    return (addr > arg.addr);
  }

  public boolean    greaterThanOrEqual(Address a) {
    if (a == null) {
      return true;
    }
    Win32Address arg = (Win32Address) a;
    if ((addr >= 0) && (arg.addr < 0)) {
      return false;
    }
    if ((addr < 0) && (arg.addr >= 0)) {
      return true;
    }
    return (addr >= arg.addr);
  }

  public Address    andWithMask(long mask) throws UnsupportedOperationException {
    long value = addr & mask;
    if (value == 0) {
      return null;
    }
    return new Win32Address(debugger, value);
  }

  public Address    orWithMask(long mask) throws UnsupportedOperationException {
    long value = addr | mask;
    if (value == 0) {
      return null;
    }
    return new Win32Address(debugger, value);
  }

  public Address    xorWithMask(long mask) throws UnsupportedOperationException {
    long value = addr ^ mask;
    if (value == 0) {
      return null;
    }
    return new Win32Address(debugger, value);
  }


  //--------------------------------------------------------------------------------
  // Internals only below this point
  //

  long getValue() {
    return addr;
  }


  private static void check(boolean arg, String failMessage) {
    if (!arg) {
      System.err.println(failMessage + ": FAILED");
      System.exit(1);
    }
  }

  // Test harness
  public static void main(String[] args) {
    // p/n indicates whether the interior address is really positive
    // or negative. In unsigned terms, p1 < p2 < n1 < n2.

    Win32Address p1 = new Win32Address(null, 0x7FFFFFFFFFFFFFF0L);
    Win32Address p2 = (Win32Address) p1.addOffsetTo(10);
    Win32Address n1 = (Win32Address) p2.addOffsetTo(10);
    Win32Address n2 = (Win32Address) n1.addOffsetTo(10);

    // lessThan positive tests
    check(p1.lessThan(p2), "lessThan 1");
    check(p1.lessThan(n1), "lessThan 2");
    check(p1.lessThan(n2), "lessThan 3");
    check(p2.lessThan(n1), "lessThan 4");
    check(p2.lessThan(n2), "lessThan 5");
    check(n1.lessThan(n2), "lessThan 6");

    // lessThan negative tests
    check(!p1.lessThan(p1), "lessThan 7");
    check(!p2.lessThan(p2), "lessThan 8");
    check(!n1.lessThan(n1), "lessThan 9");
    check(!n2.lessThan(n2), "lessThan 10");

    check(!p2.lessThan(p1), "lessThan 11");
    check(!n1.lessThan(p1), "lessThan 12");
    check(!n2.lessThan(p1), "lessThan 13");
    check(!n1.lessThan(p2), "lessThan 14");
    check(!n2.lessThan(p2), "lessThan 15");
    check(!n2.lessThan(n1), "lessThan 16");

    // lessThanOrEqual positive tests
    check(p1.lessThanOrEqual(p1), "lessThanOrEqual 1");
    check(p2.lessThanOrEqual(p2), "lessThanOrEqual 2");
    check(n1.lessThanOrEqual(n1), "lessThanOrEqual 3");
    check(n2.lessThanOrEqual(n2), "lessThanOrEqual 4");

    check(p1.lessThanOrEqual(p2), "lessThanOrEqual 5");
    check(p1.lessThanOrEqual(n1), "lessThanOrEqual 6");
    check(p1.lessThanOrEqual(n2), "lessThanOrEqual 7");
    check(p2.lessThanOrEqual(n1), "lessThanOrEqual 8");
    check(p2.lessThanOrEqual(n2), "lessThanOrEqual 9");
    check(n1.lessThanOrEqual(n2), "lessThanOrEqual 10");

    // lessThanOrEqual negative tests
    check(!p2.lessThanOrEqual(p1), "lessThanOrEqual 11");
    check(!n1.lessThanOrEqual(p1), "lessThanOrEqual 12");
    check(!n2.lessThanOrEqual(p1), "lessThanOrEqual 13");
    check(!n1.lessThanOrEqual(p2), "lessThanOrEqual 14");
    check(!n2.lessThanOrEqual(p2), "lessThanOrEqual 15");
    check(!n2.lessThanOrEqual(n1), "lessThanOrEqual 16");

    // greaterThan positive tests
    check(n2.greaterThan(p1), "greaterThan 1");
    check(n2.greaterThan(p2), "greaterThan 2");
    check(n2.greaterThan(n1), "greaterThan 3");
    check(n1.greaterThan(p1), "greaterThan 4");
    check(n1.greaterThan(p2), "greaterThan 5");
    check(p2.greaterThan(p1), "greaterThan 6");

    // greaterThan negative tests
    check(!p1.greaterThan(p1), "greaterThan 7");
    check(!p2.greaterThan(p2), "greaterThan 8");
    check(!n1.greaterThan(n1), "greaterThan 9");
    check(!n2.greaterThan(n2), "greaterThan 10");

    check(!p1.greaterThan(n2), "greaterThan 11");
    check(!p2.greaterThan(n2), "greaterThan 12");
    check(!n1.greaterThan(n2), "greaterThan 13");
    check(!p1.greaterThan(n1), "greaterThan 14");
    check(!p2.greaterThan(n1), "greaterThan 15");
    check(!p1.greaterThan(p2), "greaterThan 16");

    // greaterThanOrEqual positive tests
    check(p1.greaterThanOrEqual(p1), "greaterThanOrEqual 1");
    check(p2.greaterThanOrEqual(p2), "greaterThanOrEqual 2");
    check(n1.greaterThanOrEqual(n1), "greaterThanOrEqual 3");
    check(n2.greaterThanOrEqual(n2), "greaterThanOrEqual 4");

    check(n2.greaterThanOrEqual(p1), "greaterThanOrEqual 5");
    check(n2.greaterThanOrEqual(p2), "greaterThanOrEqual 6");
    check(n2.greaterThanOrEqual(n1), "greaterThanOrEqual 7");
    check(n1.greaterThanOrEqual(p1), "greaterThanOrEqual 8");
    check(n1.greaterThanOrEqual(p2), "greaterThanOrEqual 9");
    check(p2.greaterThanOrEqual(p1), "greaterThanOrEqual 10");

    // greaterThanOrEqual negative tests
    check(!p1.greaterThanOrEqual(n2), "greaterThanOrEqual 11");
    check(!p2.greaterThanOrEqual(n2), "greaterThanOrEqual 12");
    check(!n1.greaterThanOrEqual(n2), "greaterThanOrEqual 13");
    check(!p1.greaterThanOrEqual(n1), "greaterThanOrEqual 14");
    check(!p2.greaterThanOrEqual(n1), "greaterThanOrEqual 15");
    check(!p1.greaterThanOrEqual(p2), "greaterThanOrEqual 16");

    System.err.println("Win32Address: all tests passed successfully.");
  }
}
