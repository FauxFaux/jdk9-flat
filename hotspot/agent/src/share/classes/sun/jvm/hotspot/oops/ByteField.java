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

package sun.jvm.hotspot.oops;

import sun.jvm.hotspot.debugger.*;

// The class for a byte field simply provides access to the value.
public class ByteField extends Field {
  public ByteField(FieldIdentifier id, long offset, boolean isVMField) {
    super(id, offset, isVMField);
  }

  public ByteField(sun.jvm.hotspot.types.JByteField vmField, long startOffset) {
    super(new NamedFieldIdentifier(vmField.getName()), vmField.getOffset() + startOffset, true);
  }

  public ByteField(InstanceKlass holder, int fieldArrayIndex) {
    super(holder, fieldArrayIndex);
  }

  public byte getValue(Oop obj) { return obj.getHandle().getJByteAt(getOffset()); }
  public void setValue(Oop obj, char value) throws MutationException {
    // Fix this: setJCharAt is missing in Address
  }
}
