/*
 * Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "classfile/classFileStream.hpp"
#include "classfile/vmSymbols.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jni.h"
#include "prims/jvm.h"
#include "prims/unsafe.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/orderAccess.inline.hpp"
#include "runtime/reflection.hpp"
#include "runtime/vm_version.hpp"
#include "services/threadService.hpp"
#include "trace/tracing.hpp"
#include "utilities/copy.hpp"
#include "utilities/dtrace.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_ALL_GCS
#include "gc/g1/g1SATBCardTableModRefBS.hpp"
#endif // INCLUDE_ALL_GCS

/**
 * Implementation of the jdk.internal.misc.Unsafe class
 */


#define MAX_OBJECT_SIZE \
  ( arrayOopDesc::header_size(T_DOUBLE) * HeapWordSize \
    + ((julong)max_jint * sizeof(double)) )


#define UNSAFE_ENTRY(result_type, header) \
  JVM_ENTRY(static result_type, header)

#define UNSAFE_LEAF(result_type, header) \
  JVM_LEAF(static result_type, header)

#define UNSAFE_END JVM_END


static inline void* addr_from_java(jlong addr) {
  // This assert fails in a variety of ways on 32-bit systems.
  // It is impossible to predict whether native code that converts
  // pointers to longs will sign-extend or zero-extend the addresses.
  //assert(addr == (uintptr_t)addr, "must not be odd high bits");
  return (void*)(uintptr_t)addr;
}

static inline jlong addr_to_java(void* p) {
  assert(p == (void*)(uintptr_t)p, "must not be odd high bits");
  return (uintptr_t)p;
}


// Note: The VM's obj_field and related accessors use byte-scaled
// ("unscaled") offsets, just as the unsafe methods do.

// However, the method Unsafe.fieldOffset explicitly declines to
// guarantee this.  The field offset values manipulated by the Java user
// through the Unsafe API are opaque cookies that just happen to be byte
// offsets.  We represent this state of affairs by passing the cookies
// through conversion functions when going between the VM and the Unsafe API.
// The conversion functions just happen to be no-ops at present.

static inline jlong field_offset_to_byte_offset(jlong field_offset) {
  return field_offset;
}

static inline jlong field_offset_from_byte_offset(jlong byte_offset) {
  return byte_offset;
}

static inline void* index_oop_from_field_offset_long(oop p, jlong field_offset) {
  jlong byte_offset = field_offset_to_byte_offset(field_offset);

#ifdef ASSERT
  if (p != NULL) {
    assert(byte_offset >= 0 && byte_offset <= (jlong)MAX_OBJECT_SIZE, "sane offset");
    if (byte_offset == (jint)byte_offset) {
      void* ptr_plus_disp = (address)p + byte_offset;
      assert((void*)p->obj_field_addr<oop>((jint)byte_offset) == ptr_plus_disp,
             "raw [ptr+disp] must be consistent with oop::field_base");
    }
    jlong p_size = HeapWordSize * (jlong)(p->size());
    assert(byte_offset < p_size, "Unsafe access: offset " INT64_FORMAT " > object's size " INT64_FORMAT, byte_offset, p_size);
  }
#endif

  if (sizeof(char*) == sizeof(jint)) {   // (this constant folds!)
    return (address)p + (jint) byte_offset;
  } else {
    return (address)p +        byte_offset;
  }
}

// Externally callable versions:
// (Use these in compiler intrinsics which emulate unsafe primitives.)
jlong Unsafe_field_offset_to_byte_offset(jlong field_offset) {
  return field_offset;
}
jlong Unsafe_field_offset_from_byte_offset(jlong byte_offset) {
  return byte_offset;
}


///// Data read/writes on the Java heap and in native (off-heap) memory

/**
 * Helper class for accessing memory.
 *
 * Normalizes values and wraps accesses in
 * JavaThread::doing_unsafe_access() if needed.
 */
class MemoryAccess : StackObj {
  JavaThread* _thread;
  jobject _obj;
  jlong _offset;

  // Resolves and returns the address of the memory access
  void* addr() {
    return index_oop_from_field_offset_long(JNIHandles::resolve(_obj), _offset);
  }

  template <typename T>
  T normalize_for_write(T x) {
    return x;
  }

  jboolean normalize_for_write(jboolean x) {
    return x & 1;
  }

  template <typename T>
  T normalize_for_read(T x) {
    return x;
  }

  jboolean normalize_for_read(jboolean x) {
    return x != 0;
  }

  /**
   * Helper class to wrap memory accesses in JavaThread::doing_unsafe_access()
   */
  class GuardUnsafeAccess {
    JavaThread* _thread;
    bool _active;

  public:
    GuardUnsafeAccess(JavaThread* thread, jobject _obj) : _thread(thread) {
      if (JNIHandles::resolve(_obj) == NULL) {
        // native/off-heap access which may raise SIGBUS if accessing
        // memory mapped file data in a region of the file which has
        // been truncated and is now invalid
        _thread->set_doing_unsafe_access(true);
        _active = true;
      } else {
        _active = false;
      }
    }

    ~GuardUnsafeAccess() {
      if (_active) {
        _thread->set_doing_unsafe_access(false);
      }
    }
  };

public:
  MemoryAccess(JavaThread* thread, jobject obj, jlong offset)
    : _thread(thread), _obj(obj), _offset(offset) {
  }

  template <typename T>
  T get() {
    GuardUnsafeAccess guard(_thread, _obj);

    T* p = (T*)addr();

    T x = normalize_for_read(*p);

    return x;
  }

  template <typename T>
  void put(T x) {
    GuardUnsafeAccess guard(_thread, _obj);

    T* p = (T*)addr();

    *p = normalize_for_write(x);
  }


  template <typename T>
  T get_volatile() {
    GuardUnsafeAccess guard(_thread, _obj);

    T* p = (T*)addr();

    if (support_IRIW_for_not_multiple_copy_atomic_cpu) {
      OrderAccess::fence();
    }

    T x = OrderAccess::load_acquire((volatile T*)p);

    return normalize_for_read(x);
  }

  template <typename T>
  void put_volatile(T x) {
    GuardUnsafeAccess guard(_thread, _obj);

    T* p = (T*)addr();

    OrderAccess::release_store_fence((volatile T*)p, normalize_for_write(x));
  }


#ifndef SUPPORTS_NATIVE_CX8
  jlong get_jlong_locked() {
    GuardUnsafeAccess guard(_thread, _obj);

    MutexLockerEx mu(UnsafeJlong_lock, Mutex::_no_safepoint_check_flag);

    jlong* p = (jlong*)addr();

    jlong x = Atomic::load(p);

    return x;
  }

  void put_jlong_locked(jlong x) {
    GuardUnsafeAccess guard(_thread, _obj);

    MutexLockerEx mu(UnsafeJlong_lock, Mutex::_no_safepoint_check_flag);

    jlong* p = (jlong*)addr();

    Atomic::store(normalize_for_write(x),  p);
  }
#endif
};

// Get/PutObject must be special-cased, since it works with handles.

// These functions allow a null base pointer with an arbitrary address.
// But if the base pointer is non-null, the offset should make some sense.
// That is, it should be in the range [0, MAX_OBJECT_SIZE].
UNSAFE_ENTRY(jobject, Unsafe_GetObject(JNIEnv *env, jobject unsafe, jobject obj, jlong offset)) {
  oop p = JNIHandles::resolve(obj);
  oop v;

  if (UseCompressedOops) {
    narrowOop n = *(narrowOop*)index_oop_from_field_offset_long(p, offset);
    v = oopDesc::decode_heap_oop(n);
  } else {
    v = *(oop*)index_oop_from_field_offset_long(p, offset);
  }

  jobject ret = JNIHandles::make_local(env, v);

#if INCLUDE_ALL_GCS
  // We could be accessing the referent field in a reference
  // object. If G1 is enabled then we need to register non-null
  // referent with the SATB barrier.
  if (UseG1GC) {
    bool needs_barrier = false;

    if (ret != NULL) {
      if (offset == java_lang_ref_Reference::referent_offset && obj != NULL) {
        oop o = JNIHandles::resolve(obj);
        Klass* k = o->klass();
        if (InstanceKlass::cast(k)->reference_type() != REF_NONE) {
          assert(InstanceKlass::cast(k)->is_subclass_of(SystemDictionary::Reference_klass()), "sanity");
          needs_barrier = true;
        }
      }
    }

    if (needs_barrier) {
      oop referent = JNIHandles::resolve(ret);
      G1SATBCardTableModRefBS::enqueue(referent);
    }
  }
#endif // INCLUDE_ALL_GCS

  return ret;
} UNSAFE_END

UNSAFE_ENTRY(void, Unsafe_PutObject(JNIEnv *env, jobject unsafe, jobject obj, jlong offset, jobject x_h)) {
  oop x = JNIHandles::resolve(x_h);
  oop p = JNIHandles::resolve(obj);

  if (UseCompressedOops) {
    oop_store((narrowOop*)index_oop_from_field_offset_long(p, offset), x);
  } else {
    oop_store((oop*)index_oop_from_field_offset_long(p, offset), x);
  }
} UNSAFE_END

UNSAFE_ENTRY(jobject, Unsafe_GetObjectVolatile(JNIEnv *env, jobject unsafe, jobject obj, jlong offset)) {
  oop p = JNIHandles::resolve(obj);
  void* addr = index_oop_from_field_offset_long(p, offset);

  volatile oop v;

  if (UseCompressedOops) {
    volatile narrowOop n = *(volatile narrowOop*) addr;
    (void)const_cast<oop&>(v = oopDesc::decode_heap_oop(n));
  } else {
    (void)const_cast<oop&>(v = *(volatile oop*) addr);
  }

  OrderAccess::acquire();
  return JNIHandles::make_local(env, v);
} UNSAFE_END

UNSAFE_ENTRY(void, Unsafe_PutObjectVolatile(JNIEnv *env, jobject unsafe, jobject obj, jlong offset, jobject x_h)) {
  oop x = JNIHandles::resolve(x_h);
  oop p = JNIHandles::resolve(obj);
  void* addr = index_oop_from_field_offset_long(p, offset);
  OrderAccess::release();

  if (UseCompressedOops) {
    oop_store((narrowOop*)addr, x);
  } else {
    oop_store((oop*)addr, x);
  }

  OrderAccess::fence();
} UNSAFE_END

UNSAFE_ENTRY(jobject, Unsafe_GetUncompressedObject(JNIEnv *env, jobject unsafe, jlong addr)) {
  oop v = *(oop*) (address) addr;

  return JNIHandles::make_local(env, v);
} UNSAFE_END

#ifndef SUPPORTS_NATIVE_CX8

// VM_Version::supports_cx8() is a surrogate for 'supports atomic long memory ops'.
//
// On platforms which do not support atomic compare-and-swap of jlong (8 byte)
// values we have to use a lock-based scheme to enforce atomicity. This has to be
// applied to all Unsafe operations that set the value of a jlong field. Even so
// the compareAndSwapLong operation will not be atomic with respect to direct stores
// to the field from Java code. It is important therefore that any Java code that
// utilizes these Unsafe jlong operations does not perform direct stores. To permit
// direct loads of the field from Java code we must also use Atomic::store within the
// locked regions. And for good measure, in case there are direct stores, we also
// employ Atomic::load within those regions. Note that the field in question must be
// volatile and so must have atomic load/store accesses applied at the Java level.
//
// The locking scheme could utilize a range of strategies for controlling the locking
// granularity: from a lock per-field through to a single global lock. The latter is
// the simplest and is used for the current implementation. Note that the Java object
// that contains the field, can not, in general, be used for locking. To do so can lead
// to deadlocks as we may introduce locking into what appears to the Java code to be a
// lock-free path.
//
// As all the locked-regions are very short and themselves non-blocking we can treat
// them as leaf routines and elide safepoint checks (ie we don't perform any thread
// state transitions even when blocking for the lock). Note that if we do choose to
// add safepoint checks and thread state transitions, we must ensure that we calculate
// the address of the field _after_ we have acquired the lock, else the object may have
// been moved by the GC

UNSAFE_ENTRY(jlong, Unsafe_GetLongVolatile(JNIEnv *env, jobject unsafe, jobject obj, jlong offset)) {
  if (VM_Version::supports_cx8()) {
    return MemoryAccess(thread, obj, offset).get_volatile<jlong>();
  } else {
    return MemoryAccess(thread, obj, offset).get_jlong_locked();
  }
} UNSAFE_END

UNSAFE_ENTRY(void, Unsafe_PutLongVolatile(JNIEnv *env, jobject unsafe, jobject obj, jlong offset, jlong x)) {
  if (VM_Version::supports_cx8()) {
    MemoryAccess(thread, obj, offset).put_volatile<jlong>(x);
  } else {
    MemoryAccess(thread, obj, offset).put_jlong_locked(x);
  }
} UNSAFE_END

#endif // not SUPPORTS_NATIVE_CX8

UNSAFE_LEAF(jboolean, Unsafe_isBigEndian0(JNIEnv *env, jobject unsafe)) {
#ifdef VM_LITTLE_ENDIAN
  return false;
#else
  return true;
#endif
} UNSAFE_END

UNSAFE_LEAF(jint, Unsafe_unalignedAccess0(JNIEnv *env, jobject unsafe)) {
  return UseUnalignedAccesses;
} UNSAFE_END

#define DEFINE_GETSETOOP(java_type, Type) \
 \
UNSAFE_ENTRY(java_type, Unsafe_Get##Type(JNIEnv *env, jobject unsafe, jobject obj, jlong offset)) { \
  return MemoryAccess(thread, obj, offset).get<java_type>(); \
} UNSAFE_END \
 \
UNSAFE_ENTRY(void, Unsafe_Put##Type(JNIEnv *env, jobject unsafe, jobject obj, jlong offset, java_type x)) { \
  MemoryAccess(thread, obj, offset).put<java_type>(x); \
} UNSAFE_END \
 \
// END DEFINE_GETSETOOP.

DEFINE_GETSETOOP(jboolean, Boolean)
DEFINE_GETSETOOP(jbyte, Byte)
DEFINE_GETSETOOP(jshort, Short);
DEFINE_GETSETOOP(jchar, Char);
DEFINE_GETSETOOP(jint, Int);
DEFINE_GETSETOOP(jlong, Long);
DEFINE_GETSETOOP(jfloat, Float);
DEFINE_GETSETOOP(jdouble, Double);

#undef DEFINE_GETSETOOP

#define DEFINE_GETSETOOP_VOLATILE(java_type, Type) \
 \
UNSAFE_ENTRY(java_type, Unsafe_Get##Type##Volatile(JNIEnv *env, jobject unsafe, jobject obj, jlong offset)) { \
  return MemoryAccess(thread, obj, offset).get_volatile<java_type>(); \
} UNSAFE_END \
 \
UNSAFE_ENTRY(void, Unsafe_Put##Type##Volatile(JNIEnv *env, jobject unsafe, jobject obj, jlong offset, java_type x)) { \
  MemoryAccess(thread, obj, offset).put_volatile<java_type>(x); \
} UNSAFE_END \
 \
// END DEFINE_GETSETOOP_VOLATILE.

DEFINE_GETSETOOP_VOLATILE(jboolean, Boolean)
DEFINE_GETSETOOP_VOLATILE(jbyte, Byte)
DEFINE_GETSETOOP_VOLATILE(jshort, Short);
DEFINE_GETSETOOP_VOLATILE(jchar, Char);
DEFINE_GETSETOOP_VOLATILE(jint, Int);
DEFINE_GETSETOOP_VOLATILE(jfloat, Float);
DEFINE_GETSETOOP_VOLATILE(jdouble, Double);

#ifdef SUPPORTS_NATIVE_CX8
DEFINE_GETSETOOP_VOLATILE(jlong, Long);
#endif

#undef DEFINE_GETSETOOP_VOLATILE

UNSAFE_LEAF(void, Unsafe_LoadFence(JNIEnv *env, jobject unsafe)) {
  OrderAccess::acquire();
} UNSAFE_END

UNSAFE_LEAF(void, Unsafe_StoreFence(JNIEnv *env, jobject unsafe)) {
  OrderAccess::release();
} UNSAFE_END

UNSAFE_LEAF(void, Unsafe_FullFence(JNIEnv *env, jobject unsafe)) {
  OrderAccess::fence();
} UNSAFE_END

////// Allocation requests

UNSAFE_ENTRY(jobject, Unsafe_AllocateInstance(JNIEnv *env, jobject unsafe, jclass cls)) {
  ThreadToNativeFromVM ttnfv(thread);
  return env->AllocObject(cls);
} UNSAFE_END

UNSAFE_ENTRY(jlong, Unsafe_AllocateMemory0(JNIEnv *env, jobject unsafe, jlong size)) {
  size_t sz = (size_t)size;

  sz = round_to(sz, HeapWordSize);
  void* x = os::malloc(sz, mtInternal);

  return addr_to_java(x);
} UNSAFE_END

UNSAFE_ENTRY(jlong, Unsafe_ReallocateMemory0(JNIEnv *env, jobject unsafe, jlong addr, jlong size)) {
  void* p = addr_from_java(addr);
  size_t sz = (size_t)size;
  sz = round_to(sz, HeapWordSize);

  void* x = os::realloc(p, sz, mtInternal);

  return addr_to_java(x);
} UNSAFE_END

UNSAFE_ENTRY(void, Unsafe_FreeMemory0(JNIEnv *env, jobject unsafe, jlong addr)) {
  void* p = addr_from_java(addr);

  os::free(p);
} UNSAFE_END

UNSAFE_ENTRY(void, Unsafe_SetMemory0(JNIEnv *env, jobject unsafe, jobject obj, jlong offset, jlong size, jbyte value)) {
  size_t sz = (size_t)size;

  oop base = JNIHandles::resolve(obj);
  void* p = index_oop_from_field_offset_long(base, offset);

  Copy::fill_to_memory_atomic(p, sz, value);
} UNSAFE_END

UNSAFE_ENTRY(void, Unsafe_CopyMemory0(JNIEnv *env, jobject unsafe, jobject srcObj, jlong srcOffset, jobject dstObj, jlong dstOffset, jlong size)) {
  size_t sz = (size_t)size;

  oop srcp = JNIHandles::resolve(srcObj);
  oop dstp = JNIHandles::resolve(dstObj);

  void* src = index_oop_from_field_offset_long(srcp, srcOffset);
  void* dst = index_oop_from_field_offset_long(dstp, dstOffset);

  Copy::conjoint_memory_atomic(src, dst, sz);
} UNSAFE_END

// This function is a leaf since if the source and destination are both in native memory
// the copy may potentially be very large, and we don't want to disable GC if we can avoid it.
// If either source or destination (or both) are on the heap, the function will enter VM using
// JVM_ENTRY_FROM_LEAF
UNSAFE_LEAF(void, Unsafe_CopySwapMemory0(JNIEnv *env, jobject unsafe, jobject srcObj, jlong srcOffset, jobject dstObj, jlong dstOffset, jlong size, jlong elemSize)) {
  size_t sz = (size_t)size;
  size_t esz = (size_t)elemSize;

  if (srcObj == NULL && dstObj == NULL) {
    // Both src & dst are in native memory
    address src = (address)srcOffset;
    address dst = (address)dstOffset;

    Copy::conjoint_swap(src, dst, sz, esz);
  } else {
    // At least one of src/dst are on heap, transition to VM to access raw pointers

    JVM_ENTRY_FROM_LEAF(env, void, Unsafe_CopySwapMemory0) {
      oop srcp = JNIHandles::resolve(srcObj);
      oop dstp = JNIHandles::resolve(dstObj);

      address src = (address)index_oop_from_field_offset_long(srcp, srcOffset);
      address dst = (address)index_oop_from_field_offset_long(dstp, dstOffset);

      Copy::conjoint_swap(src, dst, sz, esz);
    } JVM_END
  }
} UNSAFE_END

////// Random queries

UNSAFE_LEAF(jint, Unsafe_AddressSize0(JNIEnv *env, jobject unsafe)) {
  return sizeof(void*);
} UNSAFE_END

UNSAFE_LEAF(jint, Unsafe_PageSize()) {
  return os::vm_page_size();
} UNSAFE_END

static jint find_field_offset(jobject field, int must_be_static, TRAPS) {
  assert(field != NULL, "field must not be NULL");

  oop reflected   = JNIHandles::resolve_non_null(field);
  oop mirror      = java_lang_reflect_Field::clazz(reflected);
  Klass* k        = java_lang_Class::as_Klass(mirror);
  int slot        = java_lang_reflect_Field::slot(reflected);
  int modifiers   = java_lang_reflect_Field::modifiers(reflected);

  if (must_be_static >= 0) {
    int really_is_static = ((modifiers & JVM_ACC_STATIC) != 0);
    if (must_be_static != really_is_static) {
      THROW_0(vmSymbols::java_lang_IllegalArgumentException());
    }
  }

  int offset = InstanceKlass::cast(k)->field_offset(slot);
  return field_offset_from_byte_offset(offset);
}

UNSAFE_ENTRY(jlong, Unsafe_ObjectFieldOffset0(JNIEnv *env, jobject unsafe, jobject field)) {
  return find_field_offset(field, 0, THREAD);
} UNSAFE_END

UNSAFE_ENTRY(jlong, Unsafe_StaticFieldOffset0(JNIEnv *env, jobject unsafe, jobject field)) {
  return find_field_offset(field, 1, THREAD);
} UNSAFE_END

UNSAFE_ENTRY(jobject, Unsafe_StaticFieldBase0(JNIEnv *env, jobject unsafe, jobject field)) {
  assert(field != NULL, "field must not be NULL");

  // Note:  In this VM implementation, a field address is always a short
  // offset from the base of a a klass metaobject.  Thus, the full dynamic
  // range of the return type is never used.  However, some implementations
  // might put the static field inside an array shared by many classes,
  // or even at a fixed address, in which case the address could be quite
  // large.  In that last case, this function would return NULL, since
  // the address would operate alone, without any base pointer.

  oop reflected   = JNIHandles::resolve_non_null(field);
  oop mirror      = java_lang_reflect_Field::clazz(reflected);
  int modifiers   = java_lang_reflect_Field::modifiers(reflected);

  if ((modifiers & JVM_ACC_STATIC) == 0) {
    THROW_0(vmSymbols::java_lang_IllegalArgumentException());
  }

  return JNIHandles::make_local(env, mirror);
} UNSAFE_END

UNSAFE_ENTRY(void, Unsafe_EnsureClassInitialized0(JNIEnv *env, jobject unsafe, jobject clazz)) {
  assert(clazz != NULL, "clazz must not be NULL");

  oop mirror = JNIHandles::resolve_non_null(clazz);

  Klass* klass = java_lang_Class::as_Klass(mirror);
  if (klass != NULL && klass->should_be_initialized()) {
    InstanceKlass* k = InstanceKlass::cast(klass);
    k->initialize(CHECK);
  }
}
UNSAFE_END

UNSAFE_ENTRY(jboolean, Unsafe_ShouldBeInitialized0(JNIEnv *env, jobject unsafe, jobject clazz)) {
  assert(clazz != NULL, "clazz must not be NULL");

  oop mirror = JNIHandles::resolve_non_null(clazz);
  Klass* klass = java_lang_Class::as_Klass(mirror);

  if (klass != NULL && klass->should_be_initialized()) {
    return true;
  }

  return false;
}
UNSAFE_END

static void getBaseAndScale(int& base, int& scale, jclass clazz, TRAPS) {
  assert(clazz != NULL, "clazz must not be NULL");

  oop mirror = JNIHandles::resolve_non_null(clazz);
  Klass* k = java_lang_Class::as_Klass(mirror);

  if (k == NULL || !k->is_array_klass()) {
    THROW(vmSymbols::java_lang_InvalidClassException());
  } else if (k->is_objArray_klass()) {
    base  = arrayOopDesc::base_offset_in_bytes(T_OBJECT);
    scale = heapOopSize;
  } else if (k->is_typeArray_klass()) {
    TypeArrayKlass* tak = TypeArrayKlass::cast(k);
    base  = tak->array_header_in_bytes();
    assert(base == arrayOopDesc::base_offset_in_bytes(tak->element_type()), "array_header_size semantics ok");
    scale = (1 << tak->log2_element_size());
  } else {
    ShouldNotReachHere();
  }
}

UNSAFE_ENTRY(jint, Unsafe_ArrayBaseOffset0(JNIEnv *env, jobject unsafe, jclass clazz)) {
  int base = 0, scale = 0;
  getBaseAndScale(base, scale, clazz, CHECK_0);

  return field_offset_from_byte_offset(base);
} UNSAFE_END


UNSAFE_ENTRY(jint, Unsafe_ArrayIndexScale0(JNIEnv *env, jobject unsafe, jclass clazz)) {
  int base = 0, scale = 0;
  getBaseAndScale(base, scale, clazz, CHECK_0);

  // This VM packs both fields and array elements down to the byte.
  // But watch out:  If this changes, so that array references for
  // a given primitive type (say, T_BOOLEAN) use different memory units
  // than fields, this method MUST return zero for such arrays.
  // For example, the VM used to store sub-word sized fields in full
  // words in the object layout, so that accessors like getByte(Object,int)
  // did not really do what one might expect for arrays.  Therefore,
  // this function used to report a zero scale factor, so that the user
  // would know not to attempt to access sub-word array elements.
  // // Code for unpacked fields:
  // if (scale < wordSize)  return 0;

  // The following allows for a pretty general fieldOffset cookie scheme,
  // but requires it to be linear in byte offset.
  return field_offset_from_byte_offset(scale) - field_offset_from_byte_offset(0);
} UNSAFE_END


static inline void throw_new(JNIEnv *env, const char *ename) {
  char buf[100];

  jio_snprintf(buf, 100, "%s%s", "java/lang/", ename);

  jclass cls = env->FindClass(buf);
  if (env->ExceptionCheck()) {
    env->ExceptionClear();
    tty->print_cr("Unsafe: cannot throw %s because FindClass has failed", buf);
    return;
  }

  env->ThrowNew(cls, NULL);
}

static jclass Unsafe_DefineClass_impl(JNIEnv *env, jstring name, jbyteArray data, int offset, int length, jobject loader, jobject pd) {
  // Code lifted from JDK 1.3 ClassLoader.c

  jbyte *body;
  char *utfName = NULL;
  jclass result = 0;
  char buf[128];

  assert(data != NULL, "Class bytes must not be NULL");
  assert(length >= 0, "length must not be negative: %d", length);

  if (UsePerfData) {
    ClassLoader::unsafe_defineClassCallCounter()->inc();
  }

  body = NEW_C_HEAP_ARRAY(jbyte, length, mtInternal);
  if (body == NULL) {
    throw_new(env, "OutOfMemoryError");
    return 0;
  }

  env->GetByteArrayRegion(data, offset, length, body);
  if (env->ExceptionOccurred()) {
    goto free_body;
  }

  if (name != NULL) {
    uint len = env->GetStringUTFLength(name);
    int unicode_len = env->GetStringLength(name);

    if (len >= sizeof(buf)) {
      utfName = NEW_C_HEAP_ARRAY(char, len + 1, mtInternal);
      if (utfName == NULL) {
        throw_new(env, "OutOfMemoryError");
        goto free_body;
      }
    } else {
      utfName = buf;
    }

    env->GetStringUTFRegion(name, 0, unicode_len, utfName);

    for (uint i = 0; i < len; i++) {
      if (utfName[i] == '.')   utfName[i] = '/';
    }
  }

  result = JVM_DefineClass(env, utfName, loader, body, length, pd);

  if (utfName && utfName != buf) {
    FREE_C_HEAP_ARRAY(char, utfName);
  }

 free_body:
  FREE_C_HEAP_ARRAY(jbyte, body);
  return result;
}


UNSAFE_ENTRY(jclass, Unsafe_DefineClass0(JNIEnv *env, jobject unsafe, jstring name, jbyteArray data, int offset, int length, jobject loader, jobject pd)) {
  ThreadToNativeFromVM ttnfv(thread);

  return Unsafe_DefineClass_impl(env, name, data, offset, length, loader, pd);
} UNSAFE_END


// define a class but do not make it known to the class loader or system dictionary
// - host_class:  supplies context for linkage, access control, protection domain, and class loader
// - data:  bytes of a class file, a raw memory address (length gives the number of bytes)
// - cp_patches:  where non-null entries exist, they replace corresponding CP entries in data

// When you load an anonymous class U, it works as if you changed its name just before loading,
// to a name that you will never use again.  Since the name is lost, no other class can directly
// link to any member of U.  Just after U is loaded, the only way to use it is reflectively,
// through java.lang.Class methods like Class.newInstance.

// Access checks for linkage sites within U continue to follow the same rules as for named classes.
// The package of an anonymous class is given by the package qualifier on the name under which it was loaded.
// An anonymous class also has special privileges to access any member of its host class.
// This is the main reason why this loading operation is unsafe.  The purpose of this is to
// allow language implementations to simulate "open classes"; a host class in effect gets
// new code when an anonymous class is loaded alongside it.  A less convenient but more
// standard way to do this is with reflection, which can also be set to ignore access
// restrictions.

// Access into an anonymous class is possible only through reflection.  Therefore, there
// are no special access rules for calling into an anonymous class.  The relaxed access
// rule for the host class is applied in the opposite direction:  A host class reflectively
// access one of its anonymous classes.

// If you load the same bytecodes twice, you get two different classes.  You can reload
// the same bytecodes with or without varying CP patches.

// By using the CP patching array, you can have a new anonymous class U2 refer to an older one U1.
// The bytecodes for U2 should refer to U1 by a symbolic name (doesn't matter what the name is).
// The CONSTANT_Class entry for that name can be patched to refer directly to U1.

// This allows, for example, U2 to use U1 as a superclass or super-interface, or as
// an outer class (so that U2 is an anonymous inner class of anonymous U1).
// It is not possible for a named class, or an older anonymous class, to refer by
// name (via its CP) to a newer anonymous class.

// CP patching may also be used to modify (i.e., hack) the names of methods, classes,
// or type descriptors used in the loaded anonymous class.

// Finally, CP patching may be used to introduce "live" objects into the constant pool,
// instead of "dead" strings.  A compiled statement like println((Object)"hello") can
// be changed to println(greeting), where greeting is an arbitrary object created before
// the anonymous class is loaded.  This is useful in dynamic languages, in which
// various kinds of metaobjects must be introduced as constants into bytecode.
// Note the cast (Object), which tells the verifier to expect an arbitrary object,
// not just a literal string.  For such ldc instructions, the verifier uses the
// type Object instead of String, if the loaded constant is not in fact a String.

static instanceKlassHandle
Unsafe_DefineAnonymousClass_impl(JNIEnv *env,
                                 jclass host_class, jbyteArray data, jobjectArray cp_patches_jh,
                                 u1** temp_alloc,
                                 TRAPS) {
  assert(host_class != NULL, "host_class must not be NULL");
  assert(data != NULL, "data must not be NULL");

  if (UsePerfData) {
    ClassLoader::unsafe_defineClassCallCounter()->inc();
  }

  jint length = typeArrayOop(JNIHandles::resolve_non_null(data))->length();
  assert(length >= 0, "class_bytes_length must not be negative: %d", length);

  int class_bytes_length = (int) length;

  u1* class_bytes = NEW_C_HEAP_ARRAY(u1, length, mtInternal);
  if (class_bytes == NULL) {
    THROW_0(vmSymbols::java_lang_OutOfMemoryError());
  }

  // caller responsible to free it:
  *temp_alloc = class_bytes;

  jbyte* array_base = typeArrayOop(JNIHandles::resolve_non_null(data))->byte_at_addr(0);
  Copy::conjoint_jbytes(array_base, class_bytes, length);

  objArrayHandle cp_patches_h;
  if (cp_patches_jh != NULL) {
    oop p = JNIHandles::resolve_non_null(cp_patches_jh);
    assert(p->is_objArray(), "cp_patches must be an object[]");
    cp_patches_h = objArrayHandle(THREAD, (objArrayOop)p);
  }

  const Klass* host_klass = java_lang_Class::as_Klass(JNIHandles::resolve_non_null(host_class));

  // Make sure it's the real host class, not another anonymous class.
  while (host_klass != NULL && host_klass->is_instance_klass() &&
         InstanceKlass::cast(host_klass)->is_anonymous()) {
    host_klass = InstanceKlass::cast(host_klass)->host_klass();
  }

  // Primitive types have NULL Klass* fields in their java.lang.Class instances.
  if (host_klass == NULL) {
    THROW_0(vmSymbols::java_lang_IllegalArgumentException());
  }

  const char* host_source = host_klass->external_name();
  Handle      host_loader(THREAD, host_klass->class_loader());
  Handle      host_domain(THREAD, host_klass->protection_domain());

  GrowableArray<Handle>* cp_patches = NULL;

  if (cp_patches_h.not_null()) {
    int alen = cp_patches_h->length();

    for (int i = alen-1; i >= 0; i--) {
      oop p = cp_patches_h->obj_at(i);
      if (p != NULL) {
        Handle patch(THREAD, p);

        if (cp_patches == NULL) {
          cp_patches = new GrowableArray<Handle>(i+1, i+1, Handle());
        }

        cp_patches->at_put(i, patch);
      }
    }
  }

  ClassFileStream st(class_bytes, class_bytes_length, host_source, ClassFileStream::verify);

  Symbol* no_class_name = NULL;
  Klass* anonk = SystemDictionary::parse_stream(no_class_name,
                                                host_loader,
                                                host_domain,
                                                &st,
                                                host_klass,
                                                cp_patches,
                                                CHECK_NULL);
  if (anonk == NULL) {
    return NULL;
  }

  return instanceKlassHandle(THREAD, anonk);
}

UNSAFE_ENTRY(jclass, Unsafe_DefineAnonymousClass0(JNIEnv *env, jobject unsafe, jclass host_class, jbyteArray data, jobjectArray cp_patches_jh)) {
  ResourceMark rm(THREAD);

  instanceKlassHandle anon_klass;
  jobject res_jh = NULL;
  u1* temp_alloc = NULL;

  anon_klass = Unsafe_DefineAnonymousClass_impl(env, host_class, data, cp_patches_jh, &temp_alloc, THREAD);
  if (anon_klass() != NULL) {
    res_jh = JNIHandles::make_local(env, anon_klass->java_mirror());
  }

  // try/finally clause:
  if (temp_alloc != NULL) {
    FREE_C_HEAP_ARRAY(u1, temp_alloc);
  }

  // The anonymous class loader data has been artificially been kept alive to
  // this point.   The mirror and any instances of this class have to keep
  // it alive afterwards.
  if (anon_klass() != NULL) {
    anon_klass->class_loader_data()->dec_keep_alive();
  }

  // let caller initialize it as needed...

  return (jclass) res_jh;
} UNSAFE_END



UNSAFE_ENTRY(void, Unsafe_ThrowException(JNIEnv *env, jobject unsafe, jthrowable thr)) {
  ThreadToNativeFromVM ttnfv(thread);
  env->Throw(thr);
} UNSAFE_END

// JSR166 ------------------------------------------------------------------

UNSAFE_ENTRY(jobject, Unsafe_CompareAndExchangeObject(JNIEnv *env, jobject unsafe, jobject obj, jlong offset, jobject e_h, jobject x_h)) {
  oop x = JNIHandles::resolve(x_h);
  oop e = JNIHandles::resolve(e_h);
  oop p = JNIHandles::resolve(obj);
  HeapWord* addr = (HeapWord *)index_oop_from_field_offset_long(p, offset);
  oop res = oopDesc::atomic_compare_exchange_oop(x, addr, e, true);
  if (res == e) {
    update_barrier_set((void*)addr, x);
  }
  return JNIHandles::make_local(env, res);
} UNSAFE_END

UNSAFE_ENTRY(jint, Unsafe_CompareAndExchangeInt(JNIEnv *env, jobject unsafe, jobject obj, jlong offset, jint e, jint x)) {
  oop p = JNIHandles::resolve(obj);
  jint* addr = (jint *) index_oop_from_field_offset_long(p, offset);

  return (jint)(Atomic::cmpxchg(x, addr, e));
} UNSAFE_END

UNSAFE_ENTRY(jlong, Unsafe_CompareAndExchangeLong(JNIEnv *env, jobject unsafe, jobject obj, jlong offset, jlong e, jlong x)) {
  Handle p(THREAD, JNIHandles::resolve(obj));
  jlong* addr = (jlong*)index_oop_from_field_offset_long(p(), offset);

#ifdef SUPPORTS_NATIVE_CX8
  return (jlong)(Atomic::cmpxchg(x, addr, e));
#else
  if (VM_Version::supports_cx8()) {
    return (jlong)(Atomic::cmpxchg(x, addr, e));
  } else {
    MutexLockerEx mu(UnsafeJlong_lock, Mutex::_no_safepoint_check_flag);

    jlong val = Atomic::load(addr);
    if (val == e) {
      Atomic::store(x, addr);
    }
    return val;
  }
#endif
} UNSAFE_END

UNSAFE_ENTRY(jboolean, Unsafe_CompareAndSwapObject(JNIEnv *env, jobject unsafe, jobject obj, jlong offset, jobject e_h, jobject x_h)) {
  oop x = JNIHandles::resolve(x_h);
  oop e = JNIHandles::resolve(e_h);
  oop p = JNIHandles::resolve(obj);
  HeapWord* addr = (HeapWord *)index_oop_from_field_offset_long(p, offset);
  oop res = oopDesc::atomic_compare_exchange_oop(x, addr, e, true);
  if (res != e) {
    return false;
  }

  update_barrier_set((void*)addr, x);

  return true;
} UNSAFE_END

UNSAFE_ENTRY(jboolean, Unsafe_CompareAndSwapInt(JNIEnv *env, jobject unsafe, jobject obj, jlong offset, jint e, jint x)) {
  oop p = JNIHandles::resolve(obj);
  jint* addr = (jint *)index_oop_from_field_offset_long(p, offset);

  return (jint)(Atomic::cmpxchg(x, addr, e)) == e;
} UNSAFE_END

UNSAFE_ENTRY(jboolean, Unsafe_CompareAndSwapLong(JNIEnv *env, jobject unsafe, jobject obj, jlong offset, jlong e, jlong x)) {
  Handle p(THREAD, JNIHandles::resolve(obj));
  jlong* addr = (jlong*)index_oop_from_field_offset_long(p(), offset);

#ifdef SUPPORTS_NATIVE_CX8
  return (jlong)(Atomic::cmpxchg(x, addr, e)) == e;
#else
  if (VM_Version::supports_cx8()) {
    return (jlong)(Atomic::cmpxchg(x, addr, e)) == e;
  } else {
    MutexLockerEx mu(UnsafeJlong_lock, Mutex::_no_safepoint_check_flag);

    jlong val = Atomic::load(addr);
    if (val != e) {
      return false;
    }

    Atomic::store(x, addr);
    return true;
  }
#endif
} UNSAFE_END

UNSAFE_ENTRY(void, Unsafe_Park(JNIEnv *env, jobject unsafe, jboolean isAbsolute, jlong time)) {
  EventThreadPark event;
  HOTSPOT_THREAD_PARK_BEGIN((uintptr_t) thread->parker(), (int) isAbsolute, time);

  JavaThreadParkedState jtps(thread, time != 0);
  thread->parker()->park(isAbsolute != 0, time);

  HOTSPOT_THREAD_PARK_END((uintptr_t) thread->parker());

  if (event.should_commit()) {
    oop obj = thread->current_park_blocker();
    event.set_parkedClass((obj != NULL) ? obj->klass() : NULL);
    event.set_timeout(time);
    event.set_address((obj != NULL) ? (TYPE_ADDRESS) cast_from_oop<uintptr_t>(obj) : 0);
    event.commit();
  }
} UNSAFE_END

UNSAFE_ENTRY(void, Unsafe_Unpark(JNIEnv *env, jobject unsafe, jobject jthread)) {
  Parker* p = NULL;

  if (jthread != NULL) {
    oop java_thread = JNIHandles::resolve_non_null(jthread);
    if (java_thread != NULL) {
      jlong lp = java_lang_Thread::park_event(java_thread);
      if (lp != 0) {
        // This cast is OK even though the jlong might have been read
        // non-atomically on 32bit systems, since there, one word will
        // always be zero anyway and the value set is always the same
        p = (Parker*)addr_from_java(lp);
      } else {
        // Grab lock if apparently null or using older version of library
        MutexLocker mu(Threads_lock);
        java_thread = JNIHandles::resolve_non_null(jthread);

        if (java_thread != NULL) {
          JavaThread* thr = java_lang_Thread::thread(java_thread);
          if (thr != NULL) {
            p = thr->parker();
            if (p != NULL) { // Bind to Java thread for next time.
              java_lang_Thread::set_park_event(java_thread, addr_to_java(p));
            }
          }
        }
      }
    }
  }

  if (p != NULL) {
    HOTSPOT_THREAD_UNPARK((uintptr_t) p);
    p->unpark();
  }
} UNSAFE_END

UNSAFE_ENTRY(jint, Unsafe_GetLoadAverage0(JNIEnv *env, jobject unsafe, jdoubleArray loadavg, jint nelem)) {
  const int max_nelem = 3;
  double la[max_nelem];
  jint ret;

  typeArrayOop a = typeArrayOop(JNIHandles::resolve_non_null(loadavg));
  assert(a->is_typeArray(), "must be type array");

  ret = os::loadavg(la, nelem);
  if (ret == -1) {
    return -1;
  }

  // if successful, ret is the number of samples actually retrieved.
  assert(ret >= 0 && ret <= max_nelem, "Unexpected loadavg return value");
  switch(ret) {
    case 3: a->double_at_put(2, (jdouble)la[2]); // fall through
    case 2: a->double_at_put(1, (jdouble)la[1]); // fall through
    case 1: a->double_at_put(0, (jdouble)la[0]); break;
  }

  return ret;
} UNSAFE_END


/// JVM_RegisterUnsafeMethods

#define ADR "J"

#define LANG "Ljava/lang/"

#define OBJ LANG "Object;"
#define CLS LANG "Class;"
#define FLD LANG "reflect/Field;"
#define THR LANG "Throwable;"

#define DC_Args  LANG "String;[BII" LANG "ClassLoader;" "Ljava/security/ProtectionDomain;"
#define DAC_Args CLS "[B[" OBJ

#define CC (char*)  /*cast a literal from (const char*)*/
#define FN_PTR(f) CAST_FROM_FN_PTR(void*, &f)

#define DECLARE_GETPUTOOP(Type, Desc) \
    {CC "get" #Type,      CC "(" OBJ "J)" #Desc,       FN_PTR(Unsafe_Get##Type)}, \
    {CC "put" #Type,      CC "(" OBJ "J" #Desc ")V",   FN_PTR(Unsafe_Put##Type)}, \
    {CC "get" #Type "Volatile",      CC "(" OBJ "J)" #Desc,       FN_PTR(Unsafe_Get##Type##Volatile)}, \
    {CC "put" #Type "Volatile",      CC "(" OBJ "J" #Desc ")V",   FN_PTR(Unsafe_Put##Type##Volatile)}


static JNINativeMethod jdk_internal_misc_Unsafe_methods[] = {
    {CC "getObject",        CC "(" OBJ "J)" OBJ "",   FN_PTR(Unsafe_GetObject)},
    {CC "putObject",        CC "(" OBJ "J" OBJ ")V",  FN_PTR(Unsafe_PutObject)},
    {CC "getObjectVolatile",CC "(" OBJ "J)" OBJ "",   FN_PTR(Unsafe_GetObjectVolatile)},
    {CC "putObjectVolatile",CC "(" OBJ "J" OBJ ")V",  FN_PTR(Unsafe_PutObjectVolatile)},

    {CC "getUncompressedObject", CC "(" ADR ")" OBJ,  FN_PTR(Unsafe_GetUncompressedObject)},

    DECLARE_GETPUTOOP(Boolean, Z),
    DECLARE_GETPUTOOP(Byte, B),
    DECLARE_GETPUTOOP(Short, S),
    DECLARE_GETPUTOOP(Char, C),
    DECLARE_GETPUTOOP(Int, I),
    DECLARE_GETPUTOOP(Long, J),
    DECLARE_GETPUTOOP(Float, F),
    DECLARE_GETPUTOOP(Double, D),

    {CC "allocateMemory0",    CC "(J)" ADR,              FN_PTR(Unsafe_AllocateMemory0)},
    {CC "reallocateMemory0",  CC "(" ADR "J)" ADR,       FN_PTR(Unsafe_ReallocateMemory0)},
    {CC "freeMemory0",        CC "(" ADR ")V",           FN_PTR(Unsafe_FreeMemory0)},

    {CC "objectFieldOffset0", CC "(" FLD ")J",           FN_PTR(Unsafe_ObjectFieldOffset0)},
    {CC "staticFieldOffset0", CC "(" FLD ")J",           FN_PTR(Unsafe_StaticFieldOffset0)},
    {CC "staticFieldBase0",   CC "(" FLD ")" OBJ,        FN_PTR(Unsafe_StaticFieldBase0)},
    {CC "ensureClassInitialized0", CC "(" CLS ")V",      FN_PTR(Unsafe_EnsureClassInitialized0)},
    {CC "arrayBaseOffset0",   CC "(" CLS ")I",           FN_PTR(Unsafe_ArrayBaseOffset0)},
    {CC "arrayIndexScale0",   CC "(" CLS ")I",           FN_PTR(Unsafe_ArrayIndexScale0)},
    {CC "addressSize0",       CC "()I",                  FN_PTR(Unsafe_AddressSize0)},
    {CC "pageSize",           CC "()I",                  FN_PTR(Unsafe_PageSize)},

    {CC "defineClass0",       CC "(" DC_Args ")" CLS,    FN_PTR(Unsafe_DefineClass0)},
    {CC "allocateInstance",   CC "(" CLS ")" OBJ,        FN_PTR(Unsafe_AllocateInstance)},
    {CC "throwException",     CC "(" THR ")V",           FN_PTR(Unsafe_ThrowException)},
    {CC "compareAndSwapObject", CC "(" OBJ "J" OBJ "" OBJ ")Z", FN_PTR(Unsafe_CompareAndSwapObject)},
    {CC "compareAndSwapInt",  CC "(" OBJ "J""I""I"")Z",  FN_PTR(Unsafe_CompareAndSwapInt)},
    {CC "compareAndSwapLong", CC "(" OBJ "J""J""J"")Z",  FN_PTR(Unsafe_CompareAndSwapLong)},
    {CC "compareAndExchangeObjectVolatile", CC "(" OBJ "J" OBJ "" OBJ ")" OBJ, FN_PTR(Unsafe_CompareAndExchangeObject)},
    {CC "compareAndExchangeIntVolatile",  CC "(" OBJ "J""I""I"")I", FN_PTR(Unsafe_CompareAndExchangeInt)},
    {CC "compareAndExchangeLongVolatile", CC "(" OBJ "J""J""J"")J", FN_PTR(Unsafe_CompareAndExchangeLong)},

    {CC "park",               CC "(ZJ)V",                FN_PTR(Unsafe_Park)},
    {CC "unpark",             CC "(" OBJ ")V",           FN_PTR(Unsafe_Unpark)},

    {CC "getLoadAverage0",    CC "([DI)I",               FN_PTR(Unsafe_GetLoadAverage0)},

    {CC "copyMemory0",        CC "(" OBJ "J" OBJ "JJ)V", FN_PTR(Unsafe_CopyMemory0)},
    {CC "copySwapMemory0",    CC "(" OBJ "J" OBJ "JJJ)V", FN_PTR(Unsafe_CopySwapMemory0)},
    {CC "setMemory0",         CC "(" OBJ "JJB)V",        FN_PTR(Unsafe_SetMemory0)},

    {CC "defineAnonymousClass0", CC "(" DAC_Args ")" CLS, FN_PTR(Unsafe_DefineAnonymousClass0)},

    {CC "shouldBeInitialized0", CC "(" CLS ")Z",         FN_PTR(Unsafe_ShouldBeInitialized0)},

    {CC "loadFence",          CC "()V",                  FN_PTR(Unsafe_LoadFence)},
    {CC "storeFence",         CC "()V",                  FN_PTR(Unsafe_StoreFence)},
    {CC "fullFence",          CC "()V",                  FN_PTR(Unsafe_FullFence)},

    {CC "isBigEndian0",       CC "()Z",                  FN_PTR(Unsafe_isBigEndian0)},
    {CC "unalignedAccess0",   CC "()Z",                  FN_PTR(Unsafe_unalignedAccess0)}
};

#undef CC
#undef FN_PTR

#undef ADR
#undef LANG
#undef OBJ
#undef CLS
#undef FLD
#undef THR
#undef DC_Args
#undef DAC_Args

#undef DECLARE_GETPUTOOP


// This function is exported, used by NativeLookup.
// The Unsafe_xxx functions above are called only from the interpreter.
// The optimizer looks at names and signatures to recognize
// individual functions.

JVM_ENTRY(void, JVM_RegisterJDKInternalMiscUnsafeMethods(JNIEnv *env, jclass unsafeclass)) {
  ThreadToNativeFromVM ttnfv(thread);

  int ok = env->RegisterNatives(unsafeclass, jdk_internal_misc_Unsafe_methods, sizeof(jdk_internal_misc_Unsafe_methods)/sizeof(JNINativeMethod));
  guarantee(ok == 0, "register jdk.internal.misc.Unsafe natives");
} JVM_END
