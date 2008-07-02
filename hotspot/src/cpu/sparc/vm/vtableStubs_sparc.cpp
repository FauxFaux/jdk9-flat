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

#include "incls/_precompiled.incl"
#include "incls/_vtableStubs_sparc.cpp.incl"

// machine-dependent part of VtableStubs: create vtableStub of correct size and
// initialize its code

#define __ masm->


#ifndef PRODUCT
extern "C" void bad_compiled_vtable_index(JavaThread* thread, oopDesc* receiver, int index);
#endif


// Used by compiler only; may use only caller saved, non-argument registers
// NOTE:  %%%% if any change is made to this stub make sure that the function
//             pd_code_size_limit is changed to ensure the correct size for VtableStub
VtableStub* VtableStubs::create_vtable_stub(int vtable_index) {
  const int sparc_code_length = VtableStub::pd_code_size_limit(true);
  VtableStub* s = new(sparc_code_length) VtableStub(true, vtable_index);
  ResourceMark rm;
  CodeBuffer cb(s->entry_point(), sparc_code_length);
  MacroAssembler* masm = new MacroAssembler(&cb);

#ifndef PRODUCT
  if (CountCompiledCalls) {
    Address ctr(G5, SharedRuntime::nof_megamorphic_calls_addr());
    __ sethi(ctr);
    __ ld(ctr, G3_scratch);
    __ inc(G3_scratch);
    __ st(G3_scratch, ctr);
  }
#endif /* PRODUCT */

  assert(VtableStub::receiver_location() == O0->as_VMReg(), "receiver expected in O0");

  // get receiver klass
  address npe_addr = __ pc();
  __ load_klass(O0, G3_scratch);

  // set methodOop (in case of interpreted method), and destination address
  int entry_offset = instanceKlass::vtable_start_offset() + vtable_index*vtableEntry::size();
#ifndef PRODUCT
  if (DebugVtables) {
    Label L;
    // check offset vs vtable length
    __ ld(G3_scratch, instanceKlass::vtable_length_offset()*wordSize, G5);
    __ cmp(G5, vtable_index*vtableEntry::size());
    __ br(Assembler::greaterUnsigned, false, Assembler::pt, L);
    __ delayed()->nop();
    __ set(vtable_index, O2);
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, bad_compiled_vtable_index), O0, O2);
    __ bind(L);
  }
#endif
  int v_off = entry_offset*wordSize + vtableEntry::method_offset_in_bytes();
  if( __ is_simm13(v_off) ) {
    __ ld_ptr(G3, v_off, G5_method);
  } else {
    __ set(v_off,G5);
    __ ld_ptr(G3, G5, G5_method);
  }

#ifndef PRODUCT
  if (DebugVtables) {
    Label L;
    __ br_notnull(G5_method, false, Assembler::pt, L);
    __ delayed()->nop();
    __ stop("Vtable entry is ZERO");
    __ bind(L);
  }
#endif

  address ame_addr = __ pc();  // if the vtable entry is null, the method is abstract
                               // NOTE: for vtable dispatches, the vtable entry will never be null.

  __ ld_ptr(G5_method, in_bytes(methodOopDesc::from_compiled_offset()), G3_scratch);

  // jump to target (either compiled code or c2iadapter)
  __ JMP(G3_scratch, 0);
  // load methodOop (in case we call c2iadapter)
  __ delayed()->nop();

  masm->flush();
  s->set_exception_points(npe_addr, ame_addr);
  return s;
}


// NOTE:  %%%% if any change is made to this stub make sure that the function
//             pd_code_size_limit is changed to ensure the correct size for VtableStub
VtableStub* VtableStubs::create_itable_stub(int vtable_index) {
  const int sparc_code_length = VtableStub::pd_code_size_limit(false);
  VtableStub* s = new(sparc_code_length) VtableStub(false, vtable_index);
  ResourceMark rm;
  CodeBuffer cb(s->entry_point(), sparc_code_length);
  MacroAssembler* masm = new MacroAssembler(&cb);

  Register G3_klassOop = G3_scratch;
  Register G5_interface = G5;  // Passed in as an argument
  Label search;

  // Entry arguments:
  //  G5_interface: Interface
  //  O0:           Receiver
  assert(VtableStub::receiver_location() == O0->as_VMReg(), "receiver expected in O0");

  // get receiver klass (also an implicit null-check)
  address npe_addr = __ pc();
  __ load_klass(O0, G3_klassOop);
  __ verify_oop(G3_klassOop);

  // Push a new window to get some temp registers.  This chops the head of all
  // my 64-bit %o registers in the LION build, but this is OK because no longs
  // are passed in the %o registers.  Instead, longs are passed in G1 and G4
  // and so those registers are not available here.
  __ save(SP,-frame::register_save_words*wordSize,SP);
  Register I0_receiver = I0;    // Location of receiver after save

#ifndef PRODUCT
  if (CountCompiledCalls) {
    Address ctr(L0, SharedRuntime::nof_megamorphic_calls_addr());
    __ sethi(ctr);
    __ ld(ctr, L1);
    __ inc(L1);
    __ st(L1, ctr);
  }
#endif /* PRODUCT */

  // load start of itable entries into L0 register
  const int base = instanceKlass::vtable_start_offset() * wordSize;
  __ ld(Address(G3_klassOop, 0, instanceKlass::vtable_length_offset() * wordSize), L0);

  // %%% Could store the aligned, prescaled offset in the klassoop.
  __ sll(L0, exact_log2(vtableEntry::size() * wordSize), L0);
  // see code for instanceKlass::start_of_itable!
  const int vtable_alignment = align_object_offset(1);
  assert(vtable_alignment == 1 || vtable_alignment == 2, "");
  const int odd_bit = vtableEntry::size() * wordSize;
  if (vtable_alignment == 2) {
    __ and3(L0, odd_bit, L1);   // isolate the odd bit
  }
  __ add(G3_klassOop, L0, L0);
  if (vtable_alignment == 2) {
    __ add(L0, L1, L0);         // double the odd bit, to align up
  }

  // Loop over all itable entries until desired interfaceOop (G5_interface) found
  __ bind(search);

  // %%%% Could load both offset and interface in one ldx, if they were
  // in the opposite order.  This would save a load.
  __ ld_ptr(L0, base + itableOffsetEntry::interface_offset_in_bytes(), L1);

  // If the entry is NULL then we've reached the end of the table
  // without finding the expected interface, so throw an exception
  Label throw_icce;
  __ bpr(Assembler::rc_z, false, Assembler::pn, L1, throw_icce);
  __ delayed()->cmp(G5_interface, L1);
  __ brx(Assembler::notEqual, true, Assembler::pn, search);
  __ delayed()->add(L0, itableOffsetEntry::size() * wordSize, L0);

  // entry found and L0 points to it, move offset of vtable for interface into L0
  __ ld(L0, base + itableOffsetEntry::offset_offset_in_bytes(), L0);

  // Compute itableMethodEntry and get methodOop(G5_method) and entrypoint(L0) for compiler
  const int method_offset = (itableMethodEntry::size() * wordSize * vtable_index) + itableMethodEntry::method_offset_in_bytes();
  __ add(G3_klassOop, L0, L1);
  __ ld_ptr(L1, method_offset, G5_method);

#ifndef PRODUCT
  if (DebugVtables) {
    Label L01;
    __ ld_ptr(L1, method_offset, G5_method);
    __ bpr(Assembler::rc_nz, false, Assembler::pt, G5_method, L01);
    __ delayed()->nop();
    __ stop("methodOop is null");
    __ bind(L01);
    __ verify_oop(G5_method);
  }
#endif

  // If the following load is through a NULL pointer, we'll take an OS
  // exception that should translate into an AbstractMethodError.  We need the
  // window count to be correct at that time.
  __ restore();                 // Restore registers BEFORE the AME point

  address ame_addr = __ pc();   // if the vtable entry is null, the method is abstract
  __ ld_ptr(G5_method, in_bytes(methodOopDesc::from_compiled_offset()), G3_scratch);

  // G5_method:  methodOop
  // O0:         Receiver
  // G3_scratch: entry point
  __ JMP(G3_scratch, 0);
  __ delayed()->nop();

  __ bind(throw_icce);
  Address icce(G3_scratch, StubRoutines::throw_IncompatibleClassChangeError_entry());
  __ jump_to(icce, 0);
  __ delayed()->restore();

  masm->flush();

  guarantee(__ pc() <= s->code_end(), "overflowed buffer");

  s->set_exception_points(npe_addr, ame_addr);
  return s;
}


int VtableStub::pd_code_size_limit(bool is_vtable_stub) {
  if (TraceJumps || DebugVtables || CountCompiledCalls || VerifyOops) return 1000;
  else {
    const int slop = 2*BytesPerInstWord; // sethi;add  (needed for long offsets)
    if (is_vtable_stub) {
      // ld;ld;ld,jmp,nop
      const int basic = 5*BytesPerInstWord +
                        // shift;add for load_klass
                        (UseCompressedOops ? 2*BytesPerInstWord : 0);
      return basic + slop;
    } else {
      // save, ld, ld, sll, and, add, add, ld, cmp, br, add, ld, add, ld, ld, jmp, restore, sethi, jmpl, restore
      const int basic = (20 LP64_ONLY(+ 6)) * BytesPerInstWord +
                        // shift;add for load_klass
                        (UseCompressedOops ? 2*BytesPerInstWord : 0);
      return (basic + slop);
    }
  }
}


int VtableStub::pd_code_alignment() {
  // UltraSPARC cache line size is 8 instructions:
  const unsigned int icache_line_size = 32;
  return icache_line_size;
}
