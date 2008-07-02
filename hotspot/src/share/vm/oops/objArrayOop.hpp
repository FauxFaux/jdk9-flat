/*
 * Copyright 1997-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

// An objArrayOop is an array containing oops.
// Evaluating "String arg[10]" will create an objArrayOop.

class objArrayOopDesc : public arrayOopDesc {
  friend class objArrayKlass;
  friend class Runtime1;
  friend class psPromotionManager;

  template <class T> T* obj_at_addr(int index) const {
    assert(is_within_bounds(index), "index out of bounds");
    return &((T*)base())[index];
  }

 public:
  // base is the address following the header.
  HeapWord* base() const      { return (HeapWord*) arrayOopDesc::base(T_OBJECT); }

  // Accessing
  oop obj_at(int index) const {
    // With UseCompressedOops decode the narrow oop in the objArray to an
    // uncompressed oop.  Otherwise this is simply a "*" operator.
    if (UseCompressedOops) {
      return load_decode_heap_oop(obj_at_addr<narrowOop>(index));
    } else {
      return load_decode_heap_oop(obj_at_addr<oop>(index));
    }
  }

  void obj_at_put(int index, oop value) {
    if (UseCompressedOops) {
      oop_store(obj_at_addr<narrowOop>(index), value);
    } else {
      oop_store(obj_at_addr<oop>(index), value);
    }
  }
  // Sizing
  static int header_size()    { return arrayOopDesc::header_size(T_OBJECT); }
  int object_size()           { return object_size(length()); }
  int array_size()            { return array_size(length()); }

  static int object_size(int length) {
    // This returns the object size in HeapWords.
    return align_object_size(header_size() + array_size(length));
  }

  // Give size of objArrayOop in HeapWords minus the header
  static int array_size(int length) {
    // Without UseCompressedOops, this is simply:
    // oop->length() * HeapWordsPerOop;
    // With narrowOops, HeapWordsPerOop is 1/2 or equal 0 as an integer.
    // The oop elements are aligned up to wordSize
    const int HeapWordsPerOop = heapOopSize/HeapWordSize;
    if (HeapWordsPerOop > 0) {
      return length * HeapWordsPerOop;
    } else {
      const int OopsPerHeapWord = HeapWordSize/heapOopSize;
      int word_len = align_size_up(length, OopsPerHeapWord)/OopsPerHeapWord;
      return word_len;
    }
  }

  // special iterators for index ranges, returns size of object
#define ObjArrayOop_OOP_ITERATE_DECL(OopClosureType, nv_suffix)     \
  int oop_iterate_range(OopClosureType* blk, int start, int end);

  ALL_OOP_OOP_ITERATE_CLOSURES_1(ObjArrayOop_OOP_ITERATE_DECL)
  ALL_OOP_OOP_ITERATE_CLOSURES_3(ObjArrayOop_OOP_ITERATE_DECL)
};
