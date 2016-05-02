/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#ifndef SHARE_VM_GC_G1_YOUNGLIST_HPP
#define SHARE_VM_GC_G1_YOUNGLIST_HPP

#include "memory/allocation.hpp"
#include "runtime/globals.hpp"

template <typename T>
class GrowableArray;

class YoungList : public CHeapObj<mtGC> {
private:
  G1CollectedHeap* _g1h;
  GrowableArray<HeapRegion*>* _survivor_regions;

  HeapRegion* _head;

  uint        _length;

  void         empty_list(HeapRegion* list);

public:
  YoungList(G1CollectedHeap* g1h);

  void         push_region(HeapRegion* hr);
  void         add_survivor_region(HeapRegion* hr);

  void         empty_list();
  bool         is_empty() { return _length == 0; }
  uint         length() { return _length; }
  uint         eden_length() { return length() - survivor_length(); }
  uint         survivor_length();

  const GrowableArray<HeapRegion*>* survivor_regions() const { return _survivor_regions; }

  // Currently we do not keep track of the used byte sum for the
  // young list and the survivors and it'd be quite a lot of work to
  // do so. When we'll eventually replace the young list with
  // instances of HeapRegionLinkedList we'll get that for free. So,
  // we'll report the more accurate information then.
  size_t       eden_used_bytes() {
    assert(length() >= survivor_length(), "invariant");
    return (size_t) eden_length() * HeapRegion::GrainBytes;
  }
  size_t       survivor_used_bytes() {
    return (size_t) survivor_length() * HeapRegion::GrainBytes;
  }

  // for development purposes
  void reset_auxilary_lists();
  void clear() { _head = NULL; _length = 0; }

  void clear_survivors() {
    _survivor_regions->clear();
  }

  HeapRegion* first_region() { return _head; }

  // debugging
  bool          check_list_well_formed();
  bool          check_list_empty();
  void          print();
};

#endif // SHARE_VM_GC_G1_YOUNGLIST_HPP
