/*
 * Copyright 2003-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "incls/_vtableStubs_x86_64.cpp.incl"

// machine-dependent part of VtableStubs: create VtableStub of correct size and
// initialize its code

#define __ masm->

#ifndef PRODUCT
extern "C" void bad_compiled_vtable_index(JavaThread* thread,
                                          oop receiver,
                                          int index);
#endif

VtableStub* VtableStubs::create_vtable_stub(int vtable_index) {
  const int amd64_code_length = VtableStub::pd_code_size_limit(true);
  VtableStub* s = new(amd64_code_length) VtableStub(true, vtable_index);
  ResourceMark rm;
  CodeBuffer cb(s->entry_point(), amd64_code_length);
  MacroAssembler* masm = new MacroAssembler(&cb);

#ifndef PRODUCT
  if (CountCompiledCalls) {
    __ incrementl(ExternalAddress((address) SharedRuntime::nof_megamorphic_calls_addr()));
  }
#endif

  // get receiver (need to skip return address on top of stack)
  assert(VtableStub::receiver_location() == j_rarg0->as_VMReg(), "receiver expected in j_rarg0");

  // Free registers (non-args) are rax, rbx

  // get receiver klass
  address npe_addr = __ pc();
  __ load_klass(rax, j_rarg0);

  // compute entry offset (in words)
  int entry_offset =
    instanceKlass::vtable_start_offset() + vtable_index * vtableEntry::size();

#ifndef PRODUCT
  if (DebugVtables) {
    Label L;
    // check offset vs vtable length
    __ cmpl(Address(rax, instanceKlass::vtable_length_offset() * wordSize),
            vtable_index * vtableEntry::size());
    __ jcc(Assembler::greater, L);
    __ movl(rbx, vtable_index);
    __ call_VM(noreg,
               CAST_FROM_FN_PTR(address, bad_compiled_vtable_index), j_rarg0, rbx);
    __ bind(L);
  }
#endif // PRODUCT

  // load methodOop and target address
  const Register method = rbx;

  __ movq(method, Address(rax,
                          entry_offset * wordSize +
                          vtableEntry::method_offset_in_bytes()));
  if (DebugVtables) {
    Label L;
    __ cmpq(method, (int)NULL);
    __ jcc(Assembler::equal, L);
    __ cmpq(Address(method, methodOopDesc::from_compiled_offset()), (int)NULL_WORD);
    __ jcc(Assembler::notZero, L);
    __ stop("Vtable entry is NULL");
    __ bind(L);
  }
  // rax: receiver klass
  // rbx: methodOop
  // rcx: receiver
  address ame_addr = __ pc();
  __ jmp( Address(rbx, methodOopDesc::from_compiled_offset()));

  __ flush();
  s->set_exception_points(npe_addr, ame_addr);
  return s;
}


VtableStub* VtableStubs::create_itable_stub(int vtable_index) {
  // Note well: pd_code_size_limit is the absolute minimum we can get
  // away with.  If you add code here, bump the code stub size
  // returned by pd_code_size_limit!
  const int amd64_code_length = VtableStub::pd_code_size_limit(false);
  VtableStub* s = new(amd64_code_length) VtableStub(false, vtable_index);
  ResourceMark rm;
  CodeBuffer cb(s->entry_point(), amd64_code_length);
  MacroAssembler* masm = new MacroAssembler(&cb);

#ifndef PRODUCT
  if (CountCompiledCalls) {
    __ incrementl(ExternalAddress((address) SharedRuntime::nof_megamorphic_calls_addr()));
  }
#endif

  // Entry arguments:
  //  rax: Interface
  //  j_rarg0: Receiver

  // Free registers (non-args) are rax (interface), rbx

  // get receiver (need to skip return address on top of stack)

  assert(VtableStub::receiver_location() == j_rarg0->as_VMReg(), "receiver expected in j_rarg0");
  // get receiver klass (also an implicit null-check)
  address npe_addr = __ pc();

  __ load_klass(rbx, j_rarg0);

  // If we take a trap while this arg is on the stack we will not
  // be able to walk the stack properly. This is not an issue except
  // when there are mistakes in this assembly code that could generate
  // a spurious fault. Ask me how I know...

  __ pushq(j_rarg1);     // Most registers are in use, so save one

  // compute itable entry offset (in words)
  const int base = instanceKlass::vtable_start_offset() * wordSize;
  assert(vtableEntry::size() * wordSize == 8,
         "adjust the scaling in the code below");
  // Get length of vtable
  __ movl(j_rarg1,
          Address(rbx, instanceKlass::vtable_length_offset() * wordSize));
  __ leaq(rbx, Address(rbx, j_rarg1, Address::times_8, base));

  if (HeapWordsPerLong > 1) {
    // Round up to align_object_offset boundary
    __ round_to_q(rbx, BytesPerLong);
  }
  Label hit, next, entry, throw_icce;

  __ jmpb(entry);

  __ bind(next);
  __ addq(rbx, itableOffsetEntry::size() * wordSize);

  __ bind(entry);

  // If the entry is NULL then we've reached the end of the table
  // without finding the expected interface, so throw an exception
  __ movq(j_rarg1, Address(rbx, itableOffsetEntry::interface_offset_in_bytes()));
  __ testq(j_rarg1, j_rarg1);
  __ jcc(Assembler::zero, throw_icce);
  __ cmpq(rax, j_rarg1);
  __ jccb(Assembler::notEqual, next);

  // We found a hit, move offset into j_rarg1
  __ movl(j_rarg1, Address(rbx, itableOffsetEntry::offset_offset_in_bytes()));

  // Compute itableMethodEntry
  const int method_offset =
    (itableMethodEntry::size() * wordSize * vtable_index) +
    itableMethodEntry::method_offset_in_bytes();

  // Get methodOop and entrypoint for compiler

  // Get klass pointer again
  __ load_klass(rax, j_rarg0);

  const Register method = rbx;
  __ movq(method, Address(rax, j_rarg1, Address::times_1, method_offset));

  // Restore saved register, before possible trap.
  __ popq(j_rarg1);

  // method (rbx): methodOop
  // j_rarg0: receiver


#ifdef ASSERT
  if (DebugVtables) {
    Label L2;
    __ cmpq(method, (int)NULL);
    __ jcc(Assembler::equal, L2);
    __ cmpq(Address(method, methodOopDesc::from_compiled_offset()), (int)NULL_WORD);
    __ jcc(Assembler::notZero, L2);
    __ stop("compiler entrypoint is null");
    __ bind(L2);
  }
#endif // ASSERT

  // rbx: methodOop
  // j_rarg0: receiver
  address ame_addr = __ pc();
  __ jmp(Address(method, methodOopDesc::from_compiled_offset()));

  __ bind(throw_icce);
  // Restore saved register
  __ popq(j_rarg1);
  __ jump(RuntimeAddress(StubRoutines::throw_IncompatibleClassChangeError_entry()));

  __ flush();

  guarantee(__ pc() <= s->code_end(), "overflowed buffer");

  s->set_exception_points(npe_addr, ame_addr);
  return s;
}

int VtableStub::pd_code_size_limit(bool is_vtable_stub) {
  if (is_vtable_stub) {
    // Vtable stub size
    return (DebugVtables ? 512 : 24) + (CountCompiledCalls ? 13 : 0) +
           (UseCompressedOops ? 16 : 0);  // 1 leaq can be 3 bytes + 1 long
  } else {
    // Itable stub size
    return (DebugVtables ? 636 : 72) + (CountCompiledCalls ? 13 : 0) +
           (UseCompressedOops ? 32 : 0);  // 2 leaqs
  }
}

int VtableStub::pd_code_alignment() {
  return wordSize;
}
