/*
 * Copyright 1997-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "incls/_vtableStubs_x86_32.cpp.incl"

// machine-dependent part of VtableStubs: create VtableStub of correct size and
// initialize its code

#define __ masm->

#ifndef PRODUCT
extern "C" void bad_compiled_vtable_index(JavaThread* thread, oop receiver, int index);
#endif

// used by compiler only; may use only caller saved registers rax, rbx, rcx.
// rdx holds first int arg, rsi, rdi, rbp are callee-save & must be preserved.
// Leave receiver in rcx; required behavior when +OptoArgsInRegisters
// is modifed to put first oop in rcx.
//
VtableStub* VtableStubs::create_vtable_stub(int vtable_index) {
  const int i486_code_length = VtableStub::pd_code_size_limit(true);
  VtableStub* s = new(i486_code_length) VtableStub(true, vtable_index);
  ResourceMark rm;
  CodeBuffer cb(s->entry_point(), i486_code_length);
  MacroAssembler* masm = new MacroAssembler(&cb);

#ifndef PRODUCT

  if (CountCompiledCalls) {
    __ incrementl(ExternalAddress((address) SharedRuntime::nof_megamorphic_calls_addr()));
  }
#endif /* PRODUCT */

  // get receiver (need to skip return address on top of stack)
  assert(VtableStub::receiver_location() == rcx->as_VMReg(), "receiver expected in rcx");

  // get receiver klass
  address npe_addr = __ pc();
  __ movptr(rax, Address(rcx, oopDesc::klass_offset_in_bytes()));
  // compute entry offset (in words)
  int entry_offset = instanceKlass::vtable_start_offset() + vtable_index*vtableEntry::size();
#ifndef PRODUCT
  if (DebugVtables) {
    Label L;
    // check offset vs vtable length
    __ cmpl(Address(rax, instanceKlass::vtable_length_offset()*wordSize), vtable_index*vtableEntry::size());
    __ jcc(Assembler::greater, L);
    __ movl(rbx, vtable_index);
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, bad_compiled_vtable_index), rcx, rbx);
    __ bind(L);
  }
#endif // PRODUCT

  const Register method = rbx;

  // load methodOop and target address
  __ movptr(method, Address(rax, entry_offset*wordSize + vtableEntry::method_offset_in_bytes()));
  if (DebugVtables) {
    Label L;
    __ cmpptr(method, (int32_t)NULL_WORD);
    __ jcc(Assembler::equal, L);
    __ cmpptr(Address(method, methodOopDesc::from_compiled_offset()), (int32_t)NULL_WORD);
    __ jcc(Assembler::notZero, L);
    __ stop("Vtable entry is NULL");
    __ bind(L);
  }

  // rax,: receiver klass
  // method (rbx): methodOop
  // rcx: receiver
  address ame_addr = __ pc();
  __ jmp( Address(method, methodOopDesc::from_compiled_offset()));

  masm->flush();
  s->set_exception_points(npe_addr, ame_addr);
  return s;
}


VtableStub* VtableStubs::create_itable_stub(int vtable_index) {
  // Note well: pd_code_size_limit is the absolute minimum we can get away with.  If you
  //            add code here, bump the code stub size returned by pd_code_size_limit!
  const int i486_code_length = VtableStub::pd_code_size_limit(false);
  VtableStub* s = new(i486_code_length) VtableStub(false, vtable_index);
  ResourceMark rm;
  CodeBuffer cb(s->entry_point(), i486_code_length);
  MacroAssembler* masm = new MacroAssembler(&cb);

  // Entry arguments:
  //  rax,: Interface
  //  rcx: Receiver

#ifndef PRODUCT
  if (CountCompiledCalls) {
    __ incrementl(ExternalAddress((address) SharedRuntime::nof_megamorphic_calls_addr()));
  }
#endif /* PRODUCT */
  // get receiver (need to skip return address on top of stack)

  assert(VtableStub::receiver_location() == rcx->as_VMReg(), "receiver expected in rcx");

  // get receiver klass (also an implicit null-check)
  address npe_addr = __ pc();
  __ movptr(rbx, Address(rcx, oopDesc::klass_offset_in_bytes()));

  __ mov(rsi, rbx);   // Save klass in free register
  // Most registers are in use, so save a few
  __ push(rdx);
  // compute itable entry offset (in words)
  const int base = instanceKlass::vtable_start_offset() * wordSize;
  assert(vtableEntry::size() * wordSize == 4, "adjust the scaling in the code below");
  __ movl(rdx, Address(rbx, instanceKlass::vtable_length_offset() * wordSize)); // Get length of vtable
  __ lea(rbx, Address(rbx, rdx, Address::times_ptr, base));
  if (HeapWordsPerLong > 1) {
    // Round up to align_object_offset boundary
    __ round_to(rbx, BytesPerLong);
  }

  Label hit, next, entry, throw_icce;

  __ jmpb(entry);

  __ bind(next);
  __ addptr(rbx, itableOffsetEntry::size() * wordSize);

  __ bind(entry);

  // If the entry is NULL then we've reached the end of the table
  // without finding the expected interface, so throw an exception
  __ movptr(rdx, Address(rbx, itableOffsetEntry::interface_offset_in_bytes()));
  __ testptr(rdx, rdx);
  __ jcc(Assembler::zero, throw_icce);
  __ cmpptr(rax, rdx);
  __ jcc(Assembler::notEqual, next);

  // We found a hit, move offset into rbx,
  __ movl(rdx, Address(rbx, itableOffsetEntry::offset_offset_in_bytes()));

  // Compute itableMethodEntry.
  const int method_offset = (itableMethodEntry::size() * wordSize * vtable_index) + itableMethodEntry::method_offset_in_bytes();

  // Get methodOop and entrypoint for compiler
  const Register method = rbx;
  __ movptr(method, Address(rsi, rdx, Address::times_1, method_offset));

  // Restore saved register, before possible trap.
  __ pop(rdx);

  // method (rbx): methodOop
  // rcx: receiver

#ifdef ASSERT
  if (DebugVtables) {
      Label L1;
      __ cmpptr(method, (int32_t)NULL_WORD);
      __ jcc(Assembler::equal, L1);
      __ cmpptr(Address(method, methodOopDesc::from_compiled_offset()), (int32_t)NULL_WORD);
      __ jcc(Assembler::notZero, L1);
      __ stop("methodOop is null");
      __ bind(L1);
    }
#endif // ASSERT

  address ame_addr = __ pc();
  __ jmp(Address(method, methodOopDesc::from_compiled_offset()));

  __ bind(throw_icce);
  // Restore saved register
  __ pop(rdx);
  __ jump(RuntimeAddress(StubRoutines::throw_IncompatibleClassChangeError_entry()));

  masm->flush();

  guarantee(__ pc() <= s->code_end(), "overflowed buffer");

  s->set_exception_points(npe_addr, ame_addr);
  return s;
}



int VtableStub::pd_code_size_limit(bool is_vtable_stub) {
  if (is_vtable_stub) {
    // Vtable stub size
    return (DebugVtables ? 210 : 16) + (CountCompiledCalls ? 6 : 0);
  } else {
    // Itable stub size
    return (DebugVtables ? 144 : 64) + (CountCompiledCalls ? 6 : 0);
  }
}

int VtableStub::pd_code_alignment() {
  return wordSize;
}
