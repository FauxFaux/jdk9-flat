/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "interp_masm_x86.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/interpreterRuntime.hpp"
#include "oops/arrayOop.hpp"
#include "oops/markOop.hpp"
#include "oops/methodData.hpp"
#include "oops/method.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/jvmtiRedefineClassesTrace.hpp"
#include "prims/jvmtiThreadState.hpp"
#include "runtime/basicLock.hpp"
#include "runtime/biasedLocking.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/thread.inline.hpp"

// Implementation of InterpreterMacroAssembler

#ifndef CC_INTERP
void InterpreterMacroAssembler::profile_obj_type(Register obj, const Address& mdo_addr) {
  Label update, next, none;

  verify_oop(obj);

  testptr(obj, obj);
  jccb(Assembler::notZero, update);
  orptr(mdo_addr, TypeEntries::null_seen);
  jmpb(next);

  bind(update);
  load_klass(obj, obj);

  xorptr(obj, mdo_addr);
  testptr(obj, TypeEntries::type_klass_mask);
  jccb(Assembler::zero, next); // klass seen before, nothing to
                               // do. The unknown bit may have been
                               // set already but no need to check.

  testptr(obj, TypeEntries::type_unknown);
  jccb(Assembler::notZero, next); // already unknown. Nothing to do anymore.

  cmpptr(mdo_addr, 0);
  jccb(Assembler::equal, none);
  cmpptr(mdo_addr, TypeEntries::null_seen);
  jccb(Assembler::equal, none);
  // There is a chance that the checks above (re-reading profiling
  // data from memory) fail if another thread has just set the
  // profiling to this obj's klass
  xorptr(obj, mdo_addr);
  testptr(obj, TypeEntries::type_klass_mask);
  jccb(Assembler::zero, next);

  // different than before. Cannot keep accurate profile.
  orptr(mdo_addr, TypeEntries::type_unknown);
  jmpb(next);

  bind(none);
  // first time here. Set profile type.
  movptr(mdo_addr, obj);

  bind(next);
}

void InterpreterMacroAssembler::profile_arguments_type(Register mdp, Register callee, Register tmp, bool is_virtual) {
  if (!ProfileInterpreter) {
    return;
  }

  if (MethodData::profile_arguments() || MethodData::profile_return()) {
    Label profile_continue;

    test_method_data_pointer(mdp, profile_continue);

    int off_to_start = is_virtual ? in_bytes(VirtualCallData::virtual_call_data_size()) : in_bytes(CounterData::counter_data_size());

    cmpb(Address(mdp, in_bytes(DataLayout::tag_offset()) - off_to_start), is_virtual ? DataLayout::virtual_call_type_data_tag : DataLayout::call_type_data_tag);
    jcc(Assembler::notEqual, profile_continue);

    if (MethodData::profile_arguments()) {
      Label done;
      int off_to_args = in_bytes(TypeEntriesAtCall::args_data_offset());
      addptr(mdp, off_to_args);

      for (int i = 0; i < TypeProfileArgsLimit; i++) {
        if (i > 0 || MethodData::profile_return()) {
          // If return value type is profiled we may have no argument to profile
          movptr(tmp, Address(mdp, in_bytes(TypeEntriesAtCall::cell_count_offset())-off_to_args));
          subl(tmp, i*TypeStackSlotEntries::per_arg_count());
          cmpl(tmp, TypeStackSlotEntries::per_arg_count());
          jcc(Assembler::less, done);
        }
        movptr(tmp, Address(callee, Method::const_offset()));
        load_unsigned_short(tmp, Address(tmp, ConstMethod::size_of_parameters_offset()));
        // stack offset o (zero based) from the start of the argument
        // list, for n arguments translates into offset n - o - 1 from
        // the end of the argument list
        subptr(tmp, Address(mdp, in_bytes(TypeEntriesAtCall::stack_slot_offset(i))-off_to_args));
        subl(tmp, 1);
        Address arg_addr = argument_address(tmp);
        movptr(tmp, arg_addr);

        Address mdo_arg_addr(mdp, in_bytes(TypeEntriesAtCall::argument_type_offset(i))-off_to_args);
        profile_obj_type(tmp, mdo_arg_addr);

        int to_add = in_bytes(TypeStackSlotEntries::per_arg_size());
        addptr(mdp, to_add);
        off_to_args += to_add;
      }

      if (MethodData::profile_return()) {
        movptr(tmp, Address(mdp, in_bytes(TypeEntriesAtCall::cell_count_offset())-off_to_args));
        subl(tmp, TypeProfileArgsLimit*TypeStackSlotEntries::per_arg_count());
      }

      bind(done);

      if (MethodData::profile_return()) {
        // We're right after the type profile for the last
        // argument. tmp is the number of cells left in the
        // CallTypeData/VirtualCallTypeData to reach its end. Non null
        // if there's a return to profile.
        assert(ReturnTypeEntry::static_cell_count() < TypeStackSlotEntries::per_arg_count(), "can't move past ret type");
        shll(tmp, exact_log2(DataLayout::cell_size));
        addptr(mdp, tmp);
      }
      movptr(Address(rbp, frame::interpreter_frame_mdp_offset * wordSize), mdp);
    } else {
      assert(MethodData::profile_return(), "either profile call args or call ret");
      update_mdp_by_constant(mdp, in_bytes(TypeEntriesAtCall::return_only_size()));
    }

    // mdp points right after the end of the
    // CallTypeData/VirtualCallTypeData, right after the cells for the
    // return value type if there's one

    bind(profile_continue);
  }
}

void InterpreterMacroAssembler::profile_return_type(Register mdp, Register ret, Register tmp) {
  assert_different_registers(mdp, ret, tmp, _bcp_register);
  if (ProfileInterpreter && MethodData::profile_return()) {
    Label profile_continue, done;

    test_method_data_pointer(mdp, profile_continue);

    if (MethodData::profile_return_jsr292_only()) {
      assert(Method::intrinsic_id_size_in_bytes() == 2, "assuming Method::_intrinsic_id is u2");

      // If we don't profile all invoke bytecodes we must make sure
      // it's a bytecode we indeed profile. We can't go back to the
      // begining of the ProfileData we intend to update to check its
      // type because we're right after it and we don't known its
      // length
      Label do_profile;
      cmpb(Address(_bcp_register, 0), Bytecodes::_invokedynamic);
      jcc(Assembler::equal, do_profile);
      cmpb(Address(_bcp_register, 0), Bytecodes::_invokehandle);
      jcc(Assembler::equal, do_profile);
      get_method(tmp);
      cmpw(Address(tmp, Method::intrinsic_id_offset_in_bytes()), vmIntrinsics::_compiledLambdaForm);
      jcc(Assembler::notEqual, profile_continue);

      bind(do_profile);
    }

    Address mdo_ret_addr(mdp, -in_bytes(ReturnTypeEntry::size()));
    mov(tmp, ret);
    profile_obj_type(tmp, mdo_ret_addr);

    bind(profile_continue);
  }
}

void InterpreterMacroAssembler::profile_parameters_type(Register mdp, Register tmp1, Register tmp2) {
  if (ProfileInterpreter && MethodData::profile_parameters()) {
    Label profile_continue, done;

    test_method_data_pointer(mdp, profile_continue);

    // Load the offset of the area within the MDO used for
    // parameters. If it's negative we're not profiling any parameters
    movl(tmp1, Address(mdp, in_bytes(MethodData::parameters_type_data_di_offset()) - in_bytes(MethodData::data_offset())));
    testl(tmp1, tmp1);
    jcc(Assembler::negative, profile_continue);

    // Compute a pointer to the area for parameters from the offset
    // and move the pointer to the slot for the last
    // parameters. Collect profiling from last parameter down.
    // mdo start + parameters offset + array length - 1
    addptr(mdp, tmp1);
    movptr(tmp1, Address(mdp, ArrayData::array_len_offset()));
    decrement(tmp1, TypeStackSlotEntries::per_arg_count());

    Label loop;
    bind(loop);

    int off_base = in_bytes(ParametersTypeData::stack_slot_offset(0));
    int type_base = in_bytes(ParametersTypeData::type_offset(0));
    Address::ScaleFactor per_arg_scale = Address::times(DataLayout::cell_size);
    Address arg_off(mdp, tmp1, per_arg_scale, off_base);
    Address arg_type(mdp, tmp1, per_arg_scale, type_base);

    // load offset on the stack from the slot for this parameter
    movptr(tmp2, arg_off);
    negptr(tmp2);
    // read the parameter from the local area
    movptr(tmp2, Address(_locals_register, tmp2, Interpreter::stackElementScale()));

    // profile the parameter
    profile_obj_type(tmp2, arg_type);

    // go to next parameter
    decrement(tmp1, TypeStackSlotEntries::per_arg_count());
    jcc(Assembler::positive, loop);

    bind(profile_continue);
  }
}
#endif

#ifdef CC_INTERP
void InterpreterMacroAssembler::get_method(Register reg) {
  movptr(reg, Address(rbp, -(sizeof(BytecodeInterpreter) + 2 * wordSize)));
  movptr(reg, Address(reg, byte_offset_of(BytecodeInterpreter, _method)));
}
#endif // CC_INTERP

#ifndef CC_INTERP
void InterpreterMacroAssembler::call_VM_leaf_base(address entry_point,
                                                  int number_of_arguments) {
  // interpreter specific
  //
  // Note: No need to save/restore bcp & locals registers
  //       since these are callee saved registers and no blocking/
  //       GC can happen in leaf calls.
  // Further Note: DO NOT save/restore bcp/locals. If a caller has
  // already saved them so that it can use rsi/rdi as temporaries
  // then a save/restore here will DESTROY the copy the caller
  // saved! There used to be a save_bcp() that only happened in
  // the ASSERT path (no restore_bcp). Which caused bizarre failures
  // when jvm built with ASSERTs.
#ifdef ASSERT
  {
    Label L;
    cmpptr(Address(rbp, frame::interpreter_frame_last_sp_offset * wordSize), (int32_t)NULL_WORD);
    jcc(Assembler::equal, L);
    stop("InterpreterMacroAssembler::call_VM_leaf_base:"
         " last_sp != NULL");
    bind(L);
  }
#endif
  // super call
  MacroAssembler::call_VM_leaf_base(entry_point, number_of_arguments);
  // interpreter specific
  // LP64: Used to ASSERT that r13/r14 were equal to frame's bcp/locals
  // but since they may not have been saved (and we don't want to
  // save them here (see note above) the assert is invalid.
}

void InterpreterMacroAssembler::call_VM_base(Register oop_result,
                                             Register java_thread,
                                             Register last_java_sp,
                                             address  entry_point,
                                             int      number_of_arguments,
                                             bool     check_exceptions) {
  // interpreter specific
  //
  // Note: Could avoid restoring locals ptr (callee saved) - however doesn't
  //       really make a difference for these runtime calls, since they are
  //       slow anyway. Btw., bcp must be saved/restored since it may change
  //       due to GC.
  NOT_LP64(assert(java_thread == noreg , "not expecting a precomputed java thread");)
  save_bcp();
#ifdef ASSERT
  {
    Label L;
    cmpptr(Address(rbp, frame::interpreter_frame_last_sp_offset * wordSize), (int32_t)NULL_WORD);
    jcc(Assembler::equal, L);
    stop("InterpreterMacroAssembler::call_VM_leaf_base:"
         " last_sp != NULL");
    bind(L);
  }
#endif /* ASSERT */
  // super call
  MacroAssembler::call_VM_base(oop_result, noreg, last_java_sp,
                               entry_point, number_of_arguments,
                               check_exceptions);
  // interpreter specific
  restore_bcp();
  restore_locals();
}

void InterpreterMacroAssembler::check_and_handle_popframe(Register java_thread) {
  if (JvmtiExport::can_pop_frame()) {
    Label L;
    // Initiate popframe handling only if it is not already being
    // processed.  If the flag has the popframe_processing bit set, it
    // means that this code is called *during* popframe handling - we
    // don't want to reenter.
    // This method is only called just after the call into the vm in
    // call_VM_base, so the arg registers are available.
    Register pop_cond = NOT_LP64(java_thread) // Not clear if any other register is available on 32 bit
                        LP64_ONLY(c_rarg0);
    movl(pop_cond, Address(java_thread, JavaThread::popframe_condition_offset()));
    testl(pop_cond, JavaThread::popframe_pending_bit);
    jcc(Assembler::zero, L);
    testl(pop_cond, JavaThread::popframe_processing_bit);
    jcc(Assembler::notZero, L);
    // Call Interpreter::remove_activation_preserving_args_entry() to get the
    // address of the same-named entrypoint in the generated interpreter code.
    call_VM_leaf(CAST_FROM_FN_PTR(address, Interpreter::remove_activation_preserving_args_entry));
    jmp(rax);
    bind(L);
    NOT_LP64(get_thread(java_thread);)
  }
}

void InterpreterMacroAssembler::load_earlyret_value(TosState state) {
  Register thread = LP64_ONLY(r15_thread) NOT_LP64(rcx);
  NOT_LP64(get_thread(thread);)
  movptr(rcx, Address(thread, JavaThread::jvmti_thread_state_offset()));
  const Address tos_addr(rcx, JvmtiThreadState::earlyret_tos_offset());
  const Address oop_addr(rcx, JvmtiThreadState::earlyret_oop_offset());
  const Address val_addr(rcx, JvmtiThreadState::earlyret_value_offset());
#ifdef _LP64
  switch (state) {
    case atos: movptr(rax, oop_addr);
               movptr(oop_addr, (int32_t)NULL_WORD);
               verify_oop(rax, state);              break;
    case ltos: movptr(rax, val_addr);                 break;
    case btos:                                   // fall through
    case ctos:                                   // fall through
    case stos:                                   // fall through
    case itos: movl(rax, val_addr);                 break;
    case ftos: load_float(val_addr);                break;
    case dtos: load_double(val_addr);               break;
    case vtos: /* nothing to do */                  break;
    default  : ShouldNotReachHere();
  }
  // Clean up tos value in the thread object
  movl(tos_addr,  (int) ilgl);
  movl(val_addr,  (int32_t) NULL_WORD);
#else
  const Address val_addr1(rcx, JvmtiThreadState::earlyret_value_offset()
                             + in_ByteSize(wordSize));
  switch (state) {
    case atos: movptr(rax, oop_addr);
               movptr(oop_addr, NULL_WORD);
               verify_oop(rax, state);                break;
    case ltos:
               movl(rdx, val_addr1);               // fall through
    case btos:                                     // fall through
    case ctos:                                     // fall through
    case stos:                                     // fall through
    case itos: movl(rax, val_addr);                   break;
    case ftos: load_float(val_addr);                  break;
    case dtos: load_double(val_addr);                 break;
    case vtos: /* nothing to do */                    break;
    default  : ShouldNotReachHere();
  }
#endif // _LP64
  // Clean up tos value in the thread object
  movl(tos_addr,  (int32_t) ilgl);
  movptr(val_addr,  NULL_WORD);
  NOT_LP64(movptr(val_addr1, NULL_WORD);)
}


void InterpreterMacroAssembler::check_and_handle_earlyret(Register java_thread) {
  if (JvmtiExport::can_force_early_return()) {
    Label L;
    Register tmp = LP64_ONLY(c_rarg0) NOT_LP64(java_thread);
    Register rthread = LP64_ONLY(r15_thread) NOT_LP64(java_thread);

    movptr(tmp, Address(rthread, JavaThread::jvmti_thread_state_offset()));
    testptr(tmp, tmp);
    jcc(Assembler::zero, L); // if (thread->jvmti_thread_state() == NULL) exit;

    // Initiate earlyret handling only if it is not already being processed.
    // If the flag has the earlyret_processing bit set, it means that this code
    // is called *during* earlyret handling - we don't want to reenter.
    movl(tmp, Address(tmp, JvmtiThreadState::earlyret_state_offset()));
    cmpl(tmp, JvmtiThreadState::earlyret_pending);
    jcc(Assembler::notEqual, L);

    // Call Interpreter::remove_activation_early_entry() to get the address of the
    // same-named entrypoint in the generated interpreter code.
    NOT_LP64(get_thread(java_thread);)
    movptr(tmp, Address(rthread, JavaThread::jvmti_thread_state_offset()));
#ifdef _LP64
    movl(tmp, Address(tmp, JvmtiThreadState::earlyret_tos_offset()));
    call_VM_leaf(CAST_FROM_FN_PTR(address, Interpreter::remove_activation_early_entry), tmp);
#else
    pushl(Address(tmp, JvmtiThreadState::earlyret_tos_offset()));
    call_VM_leaf(CAST_FROM_FN_PTR(address, Interpreter::remove_activation_early_entry), 1);
#endif // _LP64
    jmp(rax);
    bind(L);
    NOT_LP64(get_thread(java_thread);)
  }
}

void InterpreterMacroAssembler::get_unsigned_2_byte_index_at_bcp(Register reg, int bcp_offset) {
  assert(bcp_offset >= 0, "bcp is still pointing to start of bytecode");
  load_unsigned_short(reg, Address(_bcp_register, bcp_offset));
  bswapl(reg);
  shrl(reg, 16);
}

void InterpreterMacroAssembler::get_cache_index_at_bcp(Register index,
                                                       int bcp_offset,
                                                       size_t index_size) {
  assert(bcp_offset > 0, "bcp is still pointing to start of bytecode");
  if (index_size == sizeof(u2)) {
    load_unsigned_short(index, Address(_bcp_register, bcp_offset));
  } else if (index_size == sizeof(u4)) {
    movl(index, Address(_bcp_register, bcp_offset));
    // Check if the secondary index definition is still ~x, otherwise
    // we have to change the following assembler code to calculate the
    // plain index.
    assert(ConstantPool::decode_invokedynamic_index(~123) == 123, "else change next line");
    notl(index);  // convert to plain index
  } else if (index_size == sizeof(u1)) {
    load_unsigned_byte(index, Address(_bcp_register, bcp_offset));
  } else {
    ShouldNotReachHere();
  }
}

void InterpreterMacroAssembler::get_cache_and_index_at_bcp(Register cache,
                                                           Register index,
                                                           int bcp_offset,
                                                           size_t index_size) {
  assert_different_registers(cache, index);
  get_cache_index_at_bcp(index, bcp_offset, index_size);
  movptr(cache, Address(rbp, frame::interpreter_frame_cache_offset * wordSize));
  assert(sizeof(ConstantPoolCacheEntry) == 4 * wordSize, "adjust code below");
  // convert from field index to ConstantPoolCacheEntry index
  assert(exact_log2(in_words(ConstantPoolCacheEntry::size())) == 2, "else change next line");
  shll(index, 2);
}

void InterpreterMacroAssembler::get_cache_and_index_and_bytecode_at_bcp(Register cache,
                                                                        Register index,
                                                                        Register bytecode,
                                                                        int byte_no,
                                                                        int bcp_offset,
                                                                        size_t index_size) {
  get_cache_and_index_at_bcp(cache, index, bcp_offset, index_size);
  // We use a 32-bit load here since the layout of 64-bit words on
  // little-endian machines allow us that.
  movl(bytecode, Address(cache, index, Address::times_ptr, ConstantPoolCache::base_offset() + ConstantPoolCacheEntry::indices_offset()));
  const int shift_count = (1 + byte_no) * BitsPerByte;
  assert((byte_no == TemplateTable::f1_byte && shift_count == ConstantPoolCacheEntry::bytecode_1_shift) ||
         (byte_no == TemplateTable::f2_byte && shift_count == ConstantPoolCacheEntry::bytecode_2_shift),
         "correct shift count");
  shrl(bytecode, shift_count);
  assert(ConstantPoolCacheEntry::bytecode_1_mask == ConstantPoolCacheEntry::bytecode_2_mask, "common mask");
  andl(bytecode, ConstantPoolCacheEntry::bytecode_1_mask);
}

void InterpreterMacroAssembler::get_cache_entry_pointer_at_bcp(Register cache,
                                                               Register tmp,
                                                               int bcp_offset,
                                                               size_t index_size) {
  assert(cache != tmp, "must use different register");
  get_cache_index_at_bcp(tmp, bcp_offset, index_size);
  assert(sizeof(ConstantPoolCacheEntry) == 4 * wordSize, "adjust code below");
  // convert from field index to ConstantPoolCacheEntry index
  // and from word offset to byte offset
  assert(exact_log2(in_bytes(ConstantPoolCacheEntry::size_in_bytes())) == 2 + LogBytesPerWord, "else change next line");
  shll(tmp, 2 + LogBytesPerWord);
  movptr(cache, Address(rbp, frame::interpreter_frame_cache_offset * wordSize));
  // skip past the header
  addptr(cache, in_bytes(ConstantPoolCache::base_offset()));
  addptr(cache, tmp);  // construct pointer to cache entry
}

// Load object from cpool->resolved_references(index)
void InterpreterMacroAssembler::load_resolved_reference_at_index(
                                           Register result, Register index) {
  assert_different_registers(result, index);
  // convert from field index to resolved_references() index and from
  // word index to byte offset. Since this is a java object, it can be compressed
  Register tmp = index;  // reuse
  shll(tmp, LogBytesPerHeapOop);

  get_constant_pool(result);
  // load pointer for resolved_references[] objArray
  movptr(result, Address(result, ConstantPool::resolved_references_offset_in_bytes()));
  // JNIHandles::resolve(obj);
  movptr(result, Address(result, 0));
  // Add in the index
  addptr(result, tmp);
  load_heap_oop(result, Address(result, arrayOopDesc::base_offset_in_bytes(T_OBJECT)));
}


// Generate a subtype check: branch to ok_is_subtype if sub_klass is a
// subtype of super_klass.
//
// Args:
//      rax: superklass
//      Rsub_klass: subklass
//
// Kills:
//      rcx, rdi
void InterpreterMacroAssembler::gen_subtype_check(Register Rsub_klass,
                                                  Label& ok_is_subtype) {
  assert(Rsub_klass != rax, "rax holds superklass");
  LP64_ONLY(assert(Rsub_klass != r14, "r14 holds locals");)
  LP64_ONLY(assert(Rsub_klass != r13, "r13 holds bcp");)
  assert(Rsub_klass != rcx, "rcx holds 2ndary super array length");
  assert(Rsub_klass != rdi, "rdi holds 2ndary super array scan ptr");

  // Profile the not-null value's klass.
  profile_typecheck(rcx, Rsub_klass, rdi); // blows rcx, reloads rdi

  // Do the check.
  check_klass_subtype(Rsub_klass, rax, rcx, ok_is_subtype); // blows rcx

  // Profile the failure of the check.
  profile_typecheck_failed(rcx); // blows rcx
}


#ifndef _LP64
void InterpreterMacroAssembler::f2ieee() {
  if (IEEEPrecision) {
    fstp_s(Address(rsp, 0));
    fld_s(Address(rsp, 0));
  }
}


void InterpreterMacroAssembler::d2ieee() {
  if (IEEEPrecision) {
    fstp_d(Address(rsp, 0));
    fld_d(Address(rsp, 0));
  }
}
#endif // _LP64

// Java Expression Stack

void InterpreterMacroAssembler::pop_ptr(Register r) {
  pop(r);
}

void InterpreterMacroAssembler::push_ptr(Register r) {
  push(r);
}

void InterpreterMacroAssembler::push_i(Register r) {
  push(r);
}

void InterpreterMacroAssembler::push_f(XMMRegister r) {
  subptr(rsp, wordSize);
  movflt(Address(rsp, 0), r);
}

void InterpreterMacroAssembler::pop_f(XMMRegister r) {
  movflt(r, Address(rsp, 0));
  addptr(rsp, wordSize);
}

void InterpreterMacroAssembler::push_d(XMMRegister r) {
  subptr(rsp, 2 * wordSize);
  movdbl(Address(rsp, 0), r);
}

void InterpreterMacroAssembler::pop_d(XMMRegister r) {
  movdbl(r, Address(rsp, 0));
  addptr(rsp, 2 * Interpreter::stackElementSize);
}

#ifdef _LP64
void InterpreterMacroAssembler::pop_i(Register r) {
  // XXX can't use pop currently, upper half non clean
  movl(r, Address(rsp, 0));
  addptr(rsp, wordSize);
}

void InterpreterMacroAssembler::pop_l(Register r) {
  movq(r, Address(rsp, 0));
  addptr(rsp, 2 * Interpreter::stackElementSize);
}

void InterpreterMacroAssembler::push_l(Register r) {
  subptr(rsp, 2 * wordSize);
  movq(Address(rsp, 0), r);
}

void InterpreterMacroAssembler::pop(TosState state) {
  switch (state) {
  case atos: pop_ptr();                 break;
  case btos:
  case ctos:
  case stos:
  case itos: pop_i();                   break;
  case ltos: pop_l();                   break;
  case ftos: pop_f(xmm0);               break;
  case dtos: pop_d(xmm0);               break;
  case vtos: /* nothing to do */        break;
  default:   ShouldNotReachHere();
  }
  verify_oop(rax, state);
}

void InterpreterMacroAssembler::push(TosState state) {
  verify_oop(rax, state);
  switch (state) {
  case atos: push_ptr();                break;
  case btos:
  case ctos:
  case stos:
  case itos: push_i();                  break;
  case ltos: push_l();                  break;
  case ftos: push_f(xmm0);              break;
  case dtos: push_d(xmm0);              break;
  case vtos: /* nothing to do */        break;
  default  : ShouldNotReachHere();
  }
}
#else
void InterpreterMacroAssembler::pop_i(Register r) {
  pop(r);
}

void InterpreterMacroAssembler::pop_l(Register lo, Register hi) {
  pop(lo);
  pop(hi);
}

void InterpreterMacroAssembler::pop_f() {
  fld_s(Address(rsp, 0));
  addptr(rsp, 1 * wordSize);
}

void InterpreterMacroAssembler::pop_d() {
  fld_d(Address(rsp, 0));
  addptr(rsp, 2 * wordSize);
}


void InterpreterMacroAssembler::pop(TosState state) {
  switch (state) {
    case atos: pop_ptr(rax);                                 break;
    case btos:                                               // fall through
    case ctos:                                               // fall through
    case stos:                                               // fall through
    case itos: pop_i(rax);                                   break;
    case ltos: pop_l(rax, rdx);                              break;
    case ftos:
      if (UseSSE >= 1) {
        pop_f(xmm0);
      } else {
        pop_f();
      }
      break;
    case dtos:
      if (UseSSE >= 2) {
        pop_d(xmm0);
      } else {
        pop_d();
      }
      break;
    case vtos: /* nothing to do */                           break;
    default  : ShouldNotReachHere();
  }
  verify_oop(rax, state);
}


void InterpreterMacroAssembler::push_l(Register lo, Register hi) {
  push(hi);
  push(lo);
}

void InterpreterMacroAssembler::push_f() {
  // Do not schedule for no AGI! Never write beyond rsp!
  subptr(rsp, 1 * wordSize);
  fstp_s(Address(rsp, 0));
}

void InterpreterMacroAssembler::push_d() {
  // Do not schedule for no AGI! Never write beyond rsp!
  subptr(rsp, 2 * wordSize);
  fstp_d(Address(rsp, 0));
}


void InterpreterMacroAssembler::push(TosState state) {
  verify_oop(rax, state);
  switch (state) {
    case atos: push_ptr(rax); break;
    case btos:                                               // fall through
    case ctos:                                               // fall through
    case stos:                                               // fall through
    case itos: push_i(rax);                                    break;
    case ltos: push_l(rax, rdx);                               break;
    case ftos:
      if (UseSSE >= 1) {
        push_f(xmm0);
      } else {
        push_f();
      }
      break;
    case dtos:
      if (UseSSE >= 2) {
        push_d(xmm0);
      } else {
        push_d();
      }
      break;
    case vtos: /* nothing to do */                             break;
    default  : ShouldNotReachHere();
  }
}
#endif // _LP64


// Helpers for swap and dup
void InterpreterMacroAssembler::load_ptr(int n, Register val) {
  movptr(val, Address(rsp, Interpreter::expr_offset_in_bytes(n)));
}

void InterpreterMacroAssembler::store_ptr(int n, Register val) {
  movptr(Address(rsp, Interpreter::expr_offset_in_bytes(n)), val);
}


void InterpreterMacroAssembler::prepare_to_jump_from_interpreted() {
  // set sender sp
  lea(_bcp_register, Address(rsp, wordSize));
  // record last_sp
  movptr(Address(rbp, frame::interpreter_frame_last_sp_offset * wordSize), _bcp_register);
}


// Jump to from_interpreted entry of a call unless single stepping is possible
// in this thread in which case we must call the i2i entry
void InterpreterMacroAssembler::jump_from_interpreted(Register method, Register temp) {
  prepare_to_jump_from_interpreted();

  if (JvmtiExport::can_post_interpreter_events()) {
    Label run_compiled_code;
    // JVMTI events, such as single-stepping, are implemented partly by avoiding running
    // compiled code in threads for which the event is enabled.  Check here for
    // interp_only_mode if these events CAN be enabled.
    // interp_only is an int, on little endian it is sufficient to test the byte only
    // Is a cmpl faster?
    LP64_ONLY(temp = r15_thread;)
    NOT_LP64(get_thread(temp);)
    cmpb(Address(temp, JavaThread::interp_only_mode_offset()), 0);
    jccb(Assembler::zero, run_compiled_code);
    jmp(Address(method, Method::interpreter_entry_offset()));
    bind(run_compiled_code);
  }

  jmp(Address(method, Method::from_interpreted_offset()));
}

// The following two routines provide a hook so that an implementation
// can schedule the dispatch in two parts.  x86 does not do this.
void InterpreterMacroAssembler::dispatch_prolog(TosState state, int step) {
  // Nothing x86 specific to be done here
}

void InterpreterMacroAssembler::dispatch_epilog(TosState state, int step) {
  dispatch_next(state, step);
}

void InterpreterMacroAssembler::dispatch_base(TosState state,
                                              address* table,
                                              bool verifyoop) {
  verify_FPU(1, state);
  if (VerifyActivationFrameSize) {
    Label L;
    mov(rcx, rbp);
    subptr(rcx, rsp);
    int32_t min_frame_size =
      (frame::link_offset - frame::interpreter_frame_initial_sp_offset) *
      wordSize;
    cmpptr(rcx, (int32_t)min_frame_size);
    jcc(Assembler::greaterEqual, L);
    stop("broken stack frame");
    bind(L);
  }
  if (verifyoop) {
    verify_oop(rax, state);
  }
#ifdef _LP64
  lea(rscratch1, ExternalAddress((address)table));
  jmp(Address(rscratch1, rbx, Address::times_8));
#else
  Address index(noreg, rbx, Address::times_ptr);
  ExternalAddress tbl((address)table);
  ArrayAddress dispatch(tbl, index);
  jump(dispatch);
#endif // _LP64
}

void InterpreterMacroAssembler::dispatch_only(TosState state) {
  dispatch_base(state, Interpreter::dispatch_table(state));
}

void InterpreterMacroAssembler::dispatch_only_normal(TosState state) {
  dispatch_base(state, Interpreter::normal_table(state));
}

void InterpreterMacroAssembler::dispatch_only_noverify(TosState state) {
  dispatch_base(state, Interpreter::normal_table(state), false);
}


void InterpreterMacroAssembler::dispatch_next(TosState state, int step) {
  // load next bytecode (load before advancing _bcp_register to prevent AGI)
  load_unsigned_byte(rbx, Address(_bcp_register, step));
  // advance _bcp_register
  increment(_bcp_register, step);
  dispatch_base(state, Interpreter::dispatch_table(state));
}

void InterpreterMacroAssembler::dispatch_via(TosState state, address* table) {
  // load current bytecode
  load_unsigned_byte(rbx, Address(_bcp_register, 0));
  dispatch_base(state, table);
}

// remove activation
//
// Unlock the receiver if this is a synchronized method.
// Unlock any Java monitors from syncronized blocks.
// Remove the activation from the stack.
//
// If there are locked Java monitors
//    If throw_monitor_exception
//       throws IllegalMonitorStateException
//    Else if install_monitor_exception
//       installs IllegalMonitorStateException
//    Else
//       no error processing
void InterpreterMacroAssembler::remove_activation(
        TosState state,
        Register ret_addr,
        bool throw_monitor_exception,
        bool install_monitor_exception,
        bool notify_jvmdi) {
  // Note: Registers rdx xmm0 may be in use for the
  // result check if synchronized method
  Label unlocked, unlock, no_unlock;

  const Register rthread = LP64_ONLY(r15_thread) NOT_LP64(rcx);
  const Register robj    = LP64_ONLY(c_rarg1) NOT_LP64(rdx);
  const Register rmon    = LP64_ONLY(c_rarg1) NOT_LP64(rcx);
                              // monitor pointers need different register
                              // because rdx may have the result in it
  NOT_LP64(get_thread(rcx);)

  // get the value of _do_not_unlock_if_synchronized into rdx
  const Address do_not_unlock_if_synchronized(rthread,
    in_bytes(JavaThread::do_not_unlock_if_synchronized_offset()));
  movbool(rbx, do_not_unlock_if_synchronized);
  movbool(do_not_unlock_if_synchronized, false); // reset the flag

 // get method access flags
  movptr(rcx, Address(rbp, frame::interpreter_frame_method_offset * wordSize));
  movl(rcx, Address(rcx, Method::access_flags_offset()));
  testl(rcx, JVM_ACC_SYNCHRONIZED);
  jcc(Assembler::zero, unlocked);

  // Don't unlock anything if the _do_not_unlock_if_synchronized flag
  // is set.
  testbool(rbx);
  jcc(Assembler::notZero, no_unlock);

  // unlock monitor
  push(state); // save result

  // BasicObjectLock will be first in list, since this is a
  // synchronized method. However, need to check that the object has
  // not been unlocked by an explicit monitorexit bytecode.
  const Address monitor(rbp, frame::interpreter_frame_initial_sp_offset *
                        wordSize - (int) sizeof(BasicObjectLock));
  // We use c_rarg1/rdx so that if we go slow path it will be the correct
  // register for unlock_object to pass to VM directly
  lea(robj, monitor); // address of first monitor

  movptr(rax, Address(robj, BasicObjectLock::obj_offset_in_bytes()));
  testptr(rax, rax);
  jcc(Assembler::notZero, unlock);

  pop(state);
  if (throw_monitor_exception) {
    // Entry already unlocked, need to throw exception
    NOT_LP64(empty_FPU_stack();)  // remove possible return value from FPU-stack, otherwise stack could overflow
    call_VM(noreg, CAST_FROM_FN_PTR(address,
                   InterpreterRuntime::throw_illegal_monitor_state_exception));
    should_not_reach_here();
  } else {
    // Monitor already unlocked during a stack unroll. If requested,
    // install an illegal_monitor_state_exception.  Continue with
    // stack unrolling.
    if (install_monitor_exception) {
      NOT_LP64(empty_FPU_stack();)
      call_VM(noreg, CAST_FROM_FN_PTR(address,
                     InterpreterRuntime::new_illegal_monitor_state_exception));
    }
    jmp(unlocked);
  }

  bind(unlock);
  unlock_object(robj);
  pop(state);

  // Check that for block-structured locking (i.e., that all locked
  // objects has been unlocked)
  bind(unlocked);

  // rax, rdx: Might contain return value

  // Check that all monitors are unlocked
  {
    Label loop, exception, entry, restart;
    const int entry_size = frame::interpreter_frame_monitor_size() * wordSize;
    const Address monitor_block_top(
        rbp, frame::interpreter_frame_monitor_block_top_offset * wordSize);
    const Address monitor_block_bot(
        rbp, frame::interpreter_frame_initial_sp_offset * wordSize);

    bind(restart);
    // We use c_rarg1 so that if we go slow path it will be the correct
    // register for unlock_object to pass to VM directly
    movptr(rmon, monitor_block_top); // points to current entry, starting
                                  // with top-most entry
    lea(rbx, monitor_block_bot);  // points to word before bottom of
                                  // monitor block
    jmp(entry);

    // Entry already locked, need to throw exception
    bind(exception);

    if (throw_monitor_exception) {
      // Throw exception
      NOT_LP64(empty_FPU_stack();)
      MacroAssembler::call_VM(noreg,
                              CAST_FROM_FN_PTR(address, InterpreterRuntime::
                                   throw_illegal_monitor_state_exception));
      should_not_reach_here();
    } else {
      // Stack unrolling. Unlock object and install illegal_monitor_exception.
      // Unlock does not block, so don't have to worry about the frame.
      // We don't have to preserve c_rarg1 since we are going to throw an exception.

      push(state);
      mov(robj, rmon);   // nop if robj and rmon are the same
      unlock_object(robj);
      pop(state);

      if (install_monitor_exception) {
        NOT_LP64(empty_FPU_stack();)
        call_VM(noreg, CAST_FROM_FN_PTR(address,
                                        InterpreterRuntime::
                                        new_illegal_monitor_state_exception));
      }

      jmp(restart);
    }

    bind(loop);
    // check if current entry is used
    cmpptr(Address(rmon, BasicObjectLock::obj_offset_in_bytes()), (int32_t) NULL);
    jcc(Assembler::notEqual, exception);

    addptr(rmon, entry_size); // otherwise advance to next entry
    bind(entry);
    cmpptr(rmon, rbx); // check if bottom reached
    jcc(Assembler::notEqual, loop); // if not at bottom then check this entry
  }

  bind(no_unlock);

  // jvmti support
  if (notify_jvmdi) {
    notify_method_exit(state, NotifyJVMTI);    // preserve TOSCA
  } else {
    notify_method_exit(state, SkipNotifyJVMTI); // preserve TOSCA
  }

  // remove activation
  // get sender sp
  movptr(rbx,
         Address(rbp, frame::interpreter_frame_sender_sp_offset * wordSize));
  leave();                           // remove frame anchor
  pop(ret_addr);                     // get return address
  mov(rsp, rbx);                     // set sp to sender sp
}
#endif // !CC_INTERP

void InterpreterMacroAssembler::get_method_counters(Register method,
                                                    Register mcs, Label& skip) {
  Label has_counters;
  movptr(mcs, Address(method, Method::method_counters_offset()));
  testptr(mcs, mcs);
  jcc(Assembler::notZero, has_counters);
  call_VM(noreg, CAST_FROM_FN_PTR(address,
          InterpreterRuntime::build_method_counters), method);
  movptr(mcs, Address(method,Method::method_counters_offset()));
  testptr(mcs, mcs);
  jcc(Assembler::zero, skip); // No MethodCounters allocated, OutOfMemory
  bind(has_counters);
}


// Lock object
//
// Args:
//      rdx, c_rarg1: BasicObjectLock to be used for locking
//
// Kills:
//      rax, rbx
void InterpreterMacroAssembler::lock_object(Register lock_reg) {
  assert(lock_reg == LP64_ONLY(c_rarg1) NOT_LP64(rdx),
         "The argument is only for looks. It must be c_rarg1");

  if (UseHeavyMonitors) {
    call_VM(noreg,
            CAST_FROM_FN_PTR(address, InterpreterRuntime::monitorenter),
            lock_reg);
  } else {
    Label done;

    const Register swap_reg = rax; // Must use rax for cmpxchg instruction
    const Register tmp_reg = rbx; // Will be passed to biased_locking_enter to avoid a
                                  // problematic case where tmp_reg = no_reg.
    const Register obj_reg = LP64_ONLY(c_rarg3) NOT_LP64(rcx); // Will contain the oop

    const int obj_offset = BasicObjectLock::obj_offset_in_bytes();
    const int lock_offset = BasicObjectLock::lock_offset_in_bytes ();
    const int mark_offset = lock_offset +
                            BasicLock::displaced_header_offset_in_bytes();

    Label slow_case;

    // Load object pointer into obj_reg
    movptr(obj_reg, Address(lock_reg, obj_offset));

    if (UseBiasedLocking) {
      biased_locking_enter(lock_reg, obj_reg, swap_reg, tmp_reg, false, done, &slow_case);
    }

    // Load immediate 1 into swap_reg %rax
    movl(swap_reg, (int32_t)1);

    // Load (object->mark() | 1) into swap_reg %rax
    orptr(swap_reg, Address(obj_reg, 0));

    // Save (object->mark() | 1) into BasicLock's displaced header
    movptr(Address(lock_reg, mark_offset), swap_reg);

    assert(lock_offset == 0,
           "displached header must be first word in BasicObjectLock");

    if (os::is_MP()) lock();
    cmpxchgptr(lock_reg, Address(obj_reg, 0));
    if (PrintBiasedLockingStatistics) {
      cond_inc32(Assembler::zero,
                 ExternalAddress((address) BiasedLocking::fast_path_entry_count_addr()));
    }
    jcc(Assembler::zero, done);

    const int zero_bits = LP64_ONLY(7) NOT_LP64(3);

    // Test if the oopMark is an obvious stack pointer, i.e.,
    //  1) (mark & zero_bits) == 0, and
    //  2) rsp <= mark < mark + os::pagesize()
    //
    // These 3 tests can be done by evaluating the following
    // expression: ((mark - rsp) & (zero_bits - os::vm_page_size())),
    // assuming both stack pointer and pagesize have their
    // least significant bits clear.
    // NOTE: the oopMark is in swap_reg %rax as the result of cmpxchg
    subptr(swap_reg, rsp);
    andptr(swap_reg, zero_bits - os::vm_page_size());

    // Save the test result, for recursive case, the result is zero
    movptr(Address(lock_reg, mark_offset), swap_reg);

    if (PrintBiasedLockingStatistics) {
      cond_inc32(Assembler::zero,
                 ExternalAddress((address) BiasedLocking::fast_path_entry_count_addr()));
    }
    jcc(Assembler::zero, done);

    bind(slow_case);

    // Call the runtime routine for slow case
    call_VM(noreg,
            CAST_FROM_FN_PTR(address, InterpreterRuntime::monitorenter),
            lock_reg);

    bind(done);
  }
}


// Unlocks an object. Used in monitorexit bytecode and
// remove_activation.  Throws an IllegalMonitorException if object is
// not locked by current thread.
//
// Args:
//      rdx, c_rarg1: BasicObjectLock for lock
//
// Kills:
//      rax
//      c_rarg0, c_rarg1, c_rarg2, c_rarg3, ... (param regs)
//      rscratch1, rscratch2 (scratch regs)
// rax, rbx, rcx, rdx
void InterpreterMacroAssembler::unlock_object(Register lock_reg) {
  assert(lock_reg == LP64_ONLY(c_rarg1) NOT_LP64(rdx),
         "The argument is only for looks. It must be c_rarg1");

  if (UseHeavyMonitors) {
    call_VM(noreg,
            CAST_FROM_FN_PTR(address, InterpreterRuntime::monitorexit),
            lock_reg);
  } else {
    Label done;

    const Register swap_reg   = rax;  // Must use rax for cmpxchg instruction
    const Register header_reg = LP64_ONLY(c_rarg2) NOT_LP64(rbx);  // Will contain the old oopMark
    const Register obj_reg    = LP64_ONLY(c_rarg3) NOT_LP64(rcx);  // Will contain the oop

    save_bcp(); // Save in case of exception

    // Convert from BasicObjectLock structure to object and BasicLock
    // structure Store the BasicLock address into %rax
    lea(swap_reg, Address(lock_reg, BasicObjectLock::lock_offset_in_bytes()));

    // Load oop into obj_reg(%c_rarg3)
    movptr(obj_reg, Address(lock_reg, BasicObjectLock::obj_offset_in_bytes()));

    // Free entry
    movptr(Address(lock_reg, BasicObjectLock::obj_offset_in_bytes()), (int32_t)NULL_WORD);

    if (UseBiasedLocking) {
      biased_locking_exit(obj_reg, header_reg, done);
    }

    // Load the old header from BasicLock structure
    movptr(header_reg, Address(swap_reg,
                               BasicLock::displaced_header_offset_in_bytes()));

    // Test for recursion
    testptr(header_reg, header_reg);

    // zero for recursive case
    jcc(Assembler::zero, done);

    // Atomic swap back the old header
    if (os::is_MP()) lock();
    cmpxchgptr(header_reg, Address(obj_reg, 0));

    // zero for recursive case
    jcc(Assembler::zero, done);

    // Call the runtime routine for slow case.
    movptr(Address(lock_reg, BasicObjectLock::obj_offset_in_bytes()),
         obj_reg); // restore obj
    call_VM(noreg,
            CAST_FROM_FN_PTR(address, InterpreterRuntime::monitorexit),
            lock_reg);

    bind(done);

    restore_bcp();
  }
}
#ifndef CC_INTERP
void InterpreterMacroAssembler::test_method_data_pointer(Register mdp,
                                                         Label& zero_continue) {
  assert(ProfileInterpreter, "must be profiling interpreter");
  movptr(mdp, Address(rbp, frame::interpreter_frame_mdp_offset * wordSize));
  testptr(mdp, mdp);
  jcc(Assembler::zero, zero_continue);
}


// Set the method data pointer for the current bcp.
void InterpreterMacroAssembler::set_method_data_pointer_for_bcp() {
  assert(ProfileInterpreter, "must be profiling interpreter");
  Label set_mdp;
  push(rax);
  push(rbx);

  get_method(rbx);
  // Test MDO to avoid the call if it is NULL.
  movptr(rax, Address(rbx, in_bytes(Method::method_data_offset())));
  testptr(rax, rax);
  jcc(Assembler::zero, set_mdp);
  // rbx: method
  // _bcp_register: bcp
  call_VM_leaf(CAST_FROM_FN_PTR(address, InterpreterRuntime::bcp_to_di), rbx, _bcp_register);
  // rax: mdi
  // mdo is guaranteed to be non-zero here, we checked for it before the call.
  movptr(rbx, Address(rbx, in_bytes(Method::method_data_offset())));
  addptr(rbx, in_bytes(MethodData::data_offset()));
  addptr(rax, rbx);
  bind(set_mdp);
  movptr(Address(rbp, frame::interpreter_frame_mdp_offset * wordSize), rax);
  pop(rbx);
  pop(rax);
}

void InterpreterMacroAssembler::verify_method_data_pointer() {
  assert(ProfileInterpreter, "must be profiling interpreter");
#ifdef ASSERT
  Label verify_continue;
  push(rax);
  push(rbx);
  Register arg3_reg = LP64_ONLY(c_rarg3) NOT_LP64(rcx);
  Register arg2_reg = LP64_ONLY(c_rarg2) NOT_LP64(rdx);
  push(arg3_reg);
  push(arg2_reg);
  test_method_data_pointer(arg3_reg, verify_continue); // If mdp is zero, continue
  get_method(rbx);

  // If the mdp is valid, it will point to a DataLayout header which is
  // consistent with the bcp.  The converse is highly probable also.
  load_unsigned_short(arg2_reg,
                      Address(arg3_reg, in_bytes(DataLayout::bci_offset())));
  addptr(arg2_reg, Address(rbx, Method::const_offset()));
  lea(arg2_reg, Address(arg2_reg, ConstMethod::codes_offset()));
  cmpptr(arg2_reg, _bcp_register);
  jcc(Assembler::equal, verify_continue);
  // rbx: method
  // _bcp_register: bcp
  // c_rarg3: mdp
  call_VM_leaf(CAST_FROM_FN_PTR(address, InterpreterRuntime::verify_mdp),
               rbx, _bcp_register, arg3_reg);
  bind(verify_continue);
  pop(arg2_reg);
  pop(arg3_reg);
  pop(rbx);
  pop(rax);
#endif // ASSERT
}


void InterpreterMacroAssembler::set_mdp_data_at(Register mdp_in,
                                                int constant,
                                                Register value) {
  assert(ProfileInterpreter, "must be profiling interpreter");
  Address data(mdp_in, constant);
  movptr(data, value);
}


void InterpreterMacroAssembler::increment_mdp_data_at(Register mdp_in,
                                                      int constant,
                                                      bool decrement) {
  // Counter address
  Address data(mdp_in, constant);

  increment_mdp_data_at(data, decrement);
}

void InterpreterMacroAssembler::increment_mdp_data_at(Address data,
                                                      bool decrement) {
  assert(ProfileInterpreter, "must be profiling interpreter");
  // %%% this does 64bit counters at best it is wasting space
  // at worst it is a rare bug when counters overflow

  if (decrement) {
    // Decrement the register.  Set condition codes.
    addptr(data, (int32_t) -DataLayout::counter_increment);
    // If the decrement causes the counter to overflow, stay negative
    Label L;
    jcc(Assembler::negative, L);
    addptr(data, (int32_t) DataLayout::counter_increment);
    bind(L);
  } else {
    assert(DataLayout::counter_increment == 1,
           "flow-free idiom only works with 1");
    // Increment the register.  Set carry flag.
    addptr(data, DataLayout::counter_increment);
    // If the increment causes the counter to overflow, pull back by 1.
    sbbptr(data, (int32_t)0);
  }
}


void InterpreterMacroAssembler::increment_mdp_data_at(Register mdp_in,
                                                      Register reg,
                                                      int constant,
                                                      bool decrement) {
  Address data(mdp_in, reg, Address::times_1, constant);

  increment_mdp_data_at(data, decrement);
}

void InterpreterMacroAssembler::set_mdp_flag_at(Register mdp_in,
                                                int flag_byte_constant) {
  assert(ProfileInterpreter, "must be profiling interpreter");
  int header_offset = in_bytes(DataLayout::header_offset());
  int header_bits = DataLayout::flag_mask_to_header_mask(flag_byte_constant);
  // Set the flag
  orl(Address(mdp_in, header_offset), header_bits);
}



void InterpreterMacroAssembler::test_mdp_data_at(Register mdp_in,
                                                 int offset,
                                                 Register value,
                                                 Register test_value_out,
                                                 Label& not_equal_continue) {
  assert(ProfileInterpreter, "must be profiling interpreter");
  if (test_value_out == noreg) {
    cmpptr(value, Address(mdp_in, offset));
  } else {
    // Put the test value into a register, so caller can use it:
    movptr(test_value_out, Address(mdp_in, offset));
    cmpptr(test_value_out, value);
  }
  jcc(Assembler::notEqual, not_equal_continue);
}


void InterpreterMacroAssembler::update_mdp_by_offset(Register mdp_in,
                                                     int offset_of_disp) {
  assert(ProfileInterpreter, "must be profiling interpreter");
  Address disp_address(mdp_in, offset_of_disp);
  addptr(mdp_in, disp_address);
  movptr(Address(rbp, frame::interpreter_frame_mdp_offset * wordSize), mdp_in);
}


void InterpreterMacroAssembler::update_mdp_by_offset(Register mdp_in,
                                                     Register reg,
                                                     int offset_of_disp) {
  assert(ProfileInterpreter, "must be profiling interpreter");
  Address disp_address(mdp_in, reg, Address::times_1, offset_of_disp);
  addptr(mdp_in, disp_address);
  movptr(Address(rbp, frame::interpreter_frame_mdp_offset * wordSize), mdp_in);
}


void InterpreterMacroAssembler::update_mdp_by_constant(Register mdp_in,
                                                       int constant) {
  assert(ProfileInterpreter, "must be profiling interpreter");
  addptr(mdp_in, constant);
  movptr(Address(rbp, frame::interpreter_frame_mdp_offset * wordSize), mdp_in);
}


void InterpreterMacroAssembler::update_mdp_for_ret(Register return_bci) {
  assert(ProfileInterpreter, "must be profiling interpreter");
  push(return_bci); // save/restore across call_VM
  call_VM(noreg,
          CAST_FROM_FN_PTR(address, InterpreterRuntime::update_mdp_for_ret),
          return_bci);
  pop(return_bci);
}


void InterpreterMacroAssembler::profile_taken_branch(Register mdp,
                                                     Register bumped_count) {
  if (ProfileInterpreter) {
    Label profile_continue;

    // If no method data exists, go to profile_continue.
    // Otherwise, assign to mdp
    test_method_data_pointer(mdp, profile_continue);

    // We are taking a branch.  Increment the taken count.
    // We inline increment_mdp_data_at to return bumped_count in a register
    //increment_mdp_data_at(mdp, in_bytes(JumpData::taken_offset()));
    Address data(mdp, in_bytes(JumpData::taken_offset()));
    movptr(bumped_count, data);
    assert(DataLayout::counter_increment == 1,
            "flow-free idiom only works with 1");
    addptr(bumped_count, DataLayout::counter_increment);
    sbbptr(bumped_count, 0);
    movptr(data, bumped_count); // Store back out

    // The method data pointer needs to be updated to reflect the new target.
    update_mdp_by_offset(mdp, in_bytes(JumpData::displacement_offset()));
    bind(profile_continue);
  }
}


void InterpreterMacroAssembler::profile_not_taken_branch(Register mdp) {
  if (ProfileInterpreter) {
    Label profile_continue;

    // If no method data exists, go to profile_continue.
    test_method_data_pointer(mdp, profile_continue);

    // We are taking a branch.  Increment the not taken count.
    increment_mdp_data_at(mdp, in_bytes(BranchData::not_taken_offset()));

    // The method data pointer needs to be updated to correspond to
    // the next bytecode
    update_mdp_by_constant(mdp, in_bytes(BranchData::branch_data_size()));
    bind(profile_continue);
  }
}

void InterpreterMacroAssembler::profile_call(Register mdp) {
  if (ProfileInterpreter) {
    Label profile_continue;

    // If no method data exists, go to profile_continue.
    test_method_data_pointer(mdp, profile_continue);

    // We are making a call.  Increment the count.
    increment_mdp_data_at(mdp, in_bytes(CounterData::count_offset()));

    // The method data pointer needs to be updated to reflect the new target.
    update_mdp_by_constant(mdp, in_bytes(CounterData::counter_data_size()));
    bind(profile_continue);
  }
}


void InterpreterMacroAssembler::profile_final_call(Register mdp) {
  if (ProfileInterpreter) {
    Label profile_continue;

    // If no method data exists, go to profile_continue.
    test_method_data_pointer(mdp, profile_continue);

    // We are making a call.  Increment the count.
    increment_mdp_data_at(mdp, in_bytes(CounterData::count_offset()));

    // The method data pointer needs to be updated to reflect the new target.
    update_mdp_by_constant(mdp,
                           in_bytes(VirtualCallData::
                                    virtual_call_data_size()));
    bind(profile_continue);
  }
}


void InterpreterMacroAssembler::profile_virtual_call(Register receiver,
                                                     Register mdp,
                                                     Register reg2,
                                                     bool receiver_can_be_null) {
  if (ProfileInterpreter) {
    Label profile_continue;

    // If no method data exists, go to profile_continue.
    test_method_data_pointer(mdp, profile_continue);

    Label skip_receiver_profile;
    if (receiver_can_be_null) {
      Label not_null;
      testptr(receiver, receiver);
      jccb(Assembler::notZero, not_null);
      // We are making a call.  Increment the count for null receiver.
      increment_mdp_data_at(mdp, in_bytes(CounterData::count_offset()));
      jmp(skip_receiver_profile);
      bind(not_null);
    }

    // Record the receiver type.
    record_klass_in_profile(receiver, mdp, reg2, true);
    bind(skip_receiver_profile);

    // The method data pointer needs to be updated to reflect the new target.
    update_mdp_by_constant(mdp,
                           in_bytes(VirtualCallData::
                                    virtual_call_data_size()));
    bind(profile_continue);
  }
}

// This routine creates a state machine for updating the multi-row
// type profile at a virtual call site (or other type-sensitive bytecode).
// The machine visits each row (of receiver/count) until the receiver type
// is found, or until it runs out of rows.  At the same time, it remembers
// the location of the first empty row.  (An empty row records null for its
// receiver, and can be allocated for a newly-observed receiver type.)
// Because there are two degrees of freedom in the state, a simple linear
// search will not work; it must be a decision tree.  Hence this helper
// function is recursive, to generate the required tree structured code.
// It's the interpreter, so we are trading off code space for speed.
// See below for example code.
void InterpreterMacroAssembler::record_klass_in_profile_helper(
                                        Register receiver, Register mdp,
                                        Register reg2, int start_row,
                                        Label& done, bool is_virtual_call) {
  if (TypeProfileWidth == 0) {
    if (is_virtual_call) {
      increment_mdp_data_at(mdp, in_bytes(CounterData::count_offset()));
    }
    return;
  }

  int last_row = VirtualCallData::row_limit() - 1;
  assert(start_row <= last_row, "must be work left to do");
  // Test this row for both the receiver and for null.
  // Take any of three different outcomes:
  //   1. found receiver => increment count and goto done
  //   2. found null => keep looking for case 1, maybe allocate this cell
  //   3. found something else => keep looking for cases 1 and 2
  // Case 3 is handled by a recursive call.
  for (int row = start_row; row <= last_row; row++) {
    Label next_test;
    bool test_for_null_also = (row == start_row);

    // See if the receiver is receiver[n].
    int recvr_offset = in_bytes(VirtualCallData::receiver_offset(row));
    test_mdp_data_at(mdp, recvr_offset, receiver,
                     (test_for_null_also ? reg2 : noreg),
                     next_test);
    // (Reg2 now contains the receiver from the CallData.)

    // The receiver is receiver[n].  Increment count[n].
    int count_offset = in_bytes(VirtualCallData::receiver_count_offset(row));
    increment_mdp_data_at(mdp, count_offset);
    jmp(done);
    bind(next_test);

    if (test_for_null_also) {
      Label found_null;
      // Failed the equality check on receiver[n]...  Test for null.
      testptr(reg2, reg2);
      if (start_row == last_row) {
        // The only thing left to do is handle the null case.
        if (is_virtual_call) {
          jccb(Assembler::zero, found_null);
          // Receiver did not match any saved receiver and there is no empty row for it.
          // Increment total counter to indicate polymorphic case.
          increment_mdp_data_at(mdp, in_bytes(CounterData::count_offset()));
          jmp(done);
          bind(found_null);
        } else {
          jcc(Assembler::notZero, done);
        }
        break;
      }
      // Since null is rare, make it be the branch-taken case.
      jcc(Assembler::zero, found_null);

      // Put all the "Case 3" tests here.
      record_klass_in_profile_helper(receiver, mdp, reg2, start_row + 1, done, is_virtual_call);

      // Found a null.  Keep searching for a matching receiver,
      // but remember that this is an empty (unused) slot.
      bind(found_null);
    }
  }

  // In the fall-through case, we found no matching receiver, but we
  // observed the receiver[start_row] is NULL.

  // Fill in the receiver field and increment the count.
  int recvr_offset = in_bytes(VirtualCallData::receiver_offset(start_row));
  set_mdp_data_at(mdp, recvr_offset, receiver);
  int count_offset = in_bytes(VirtualCallData::receiver_count_offset(start_row));
  movl(reg2, DataLayout::counter_increment);
  set_mdp_data_at(mdp, count_offset, reg2);
  if (start_row > 0) {
    jmp(done);
  }
}

// Example state machine code for three profile rows:
//   // main copy of decision tree, rooted at row[1]
//   if (row[0].rec == rec) { row[0].incr(); goto done; }
//   if (row[0].rec != NULL) {
//     // inner copy of decision tree, rooted at row[1]
//     if (row[1].rec == rec) { row[1].incr(); goto done; }
//     if (row[1].rec != NULL) {
//       // degenerate decision tree, rooted at row[2]
//       if (row[2].rec == rec) { row[2].incr(); goto done; }
//       if (row[2].rec != NULL) { count.incr(); goto done; } // overflow
//       row[2].init(rec); goto done;
//     } else {
//       // remember row[1] is empty
//       if (row[2].rec == rec) { row[2].incr(); goto done; }
//       row[1].init(rec); goto done;
//     }
//   } else {
//     // remember row[0] is empty
//     if (row[1].rec == rec) { row[1].incr(); goto done; }
//     if (row[2].rec == rec) { row[2].incr(); goto done; }
//     row[0].init(rec); goto done;
//   }
//   done:

void InterpreterMacroAssembler::record_klass_in_profile(Register receiver,
                                                        Register mdp, Register reg2,
                                                        bool is_virtual_call) {
  assert(ProfileInterpreter, "must be profiling");
  Label done;

  record_klass_in_profile_helper(receiver, mdp, reg2, 0, done, is_virtual_call);

  bind (done);
}

void InterpreterMacroAssembler::profile_ret(Register return_bci,
                                            Register mdp) {
  if (ProfileInterpreter) {
    Label profile_continue;
    uint row;

    // If no method data exists, go to profile_continue.
    test_method_data_pointer(mdp, profile_continue);

    // Update the total ret count.
    increment_mdp_data_at(mdp, in_bytes(CounterData::count_offset()));

    for (row = 0; row < RetData::row_limit(); row++) {
      Label next_test;

      // See if return_bci is equal to bci[n]:
      test_mdp_data_at(mdp,
                       in_bytes(RetData::bci_offset(row)),
                       return_bci, noreg,
                       next_test);

      // return_bci is equal to bci[n].  Increment the count.
      increment_mdp_data_at(mdp, in_bytes(RetData::bci_count_offset(row)));

      // The method data pointer needs to be updated to reflect the new target.
      update_mdp_by_offset(mdp,
                           in_bytes(RetData::bci_displacement_offset(row)));
      jmp(profile_continue);
      bind(next_test);
    }

    update_mdp_for_ret(return_bci);

    bind(profile_continue);
  }
}


void InterpreterMacroAssembler::profile_null_seen(Register mdp) {
  if (ProfileInterpreter) {
    Label profile_continue;

    // If no method data exists, go to profile_continue.
    test_method_data_pointer(mdp, profile_continue);

    set_mdp_flag_at(mdp, BitData::null_seen_byte_constant());

    // The method data pointer needs to be updated.
    int mdp_delta = in_bytes(BitData::bit_data_size());
    if (TypeProfileCasts) {
      mdp_delta = in_bytes(VirtualCallData::virtual_call_data_size());
    }
    update_mdp_by_constant(mdp, mdp_delta);

    bind(profile_continue);
  }
}


void InterpreterMacroAssembler::profile_typecheck_failed(Register mdp) {
  if (ProfileInterpreter && TypeProfileCasts) {
    Label profile_continue;

    // If no method data exists, go to profile_continue.
    test_method_data_pointer(mdp, profile_continue);

    int count_offset = in_bytes(CounterData::count_offset());
    // Back up the address, since we have already bumped the mdp.
    count_offset -= in_bytes(VirtualCallData::virtual_call_data_size());

    // *Decrement* the counter.  We expect to see zero or small negatives.
    increment_mdp_data_at(mdp, count_offset, true);

    bind (profile_continue);
  }
}


void InterpreterMacroAssembler::profile_typecheck(Register mdp, Register klass, Register reg2) {
  if (ProfileInterpreter) {
    Label profile_continue;

    // If no method data exists, go to profile_continue.
    test_method_data_pointer(mdp, profile_continue);

    // The method data pointer needs to be updated.
    int mdp_delta = in_bytes(BitData::bit_data_size());
    if (TypeProfileCasts) {
      mdp_delta = in_bytes(VirtualCallData::virtual_call_data_size());

      // Record the object type.
      record_klass_in_profile(klass, mdp, reg2, false);
      NOT_LP64(assert(reg2 == rdi, "we know how to fix this blown reg");)
      NOT_LP64(restore_locals();)         // Restore EDI
    }
    update_mdp_by_constant(mdp, mdp_delta);

    bind(profile_continue);
  }
}


void InterpreterMacroAssembler::profile_switch_default(Register mdp) {
  if (ProfileInterpreter) {
    Label profile_continue;

    // If no method data exists, go to profile_continue.
    test_method_data_pointer(mdp, profile_continue);

    // Update the default case count
    increment_mdp_data_at(mdp,
                          in_bytes(MultiBranchData::default_count_offset()));

    // The method data pointer needs to be updated.
    update_mdp_by_offset(mdp,
                         in_bytes(MultiBranchData::
                                  default_displacement_offset()));

    bind(profile_continue);
  }
}


void InterpreterMacroAssembler::profile_switch_case(Register index,
                                                    Register mdp,
                                                    Register reg2) {
  if (ProfileInterpreter) {
    Label profile_continue;

    // If no method data exists, go to profile_continue.
    test_method_data_pointer(mdp, profile_continue);

    // Build the base (index * per_case_size_in_bytes()) +
    // case_array_offset_in_bytes()
    movl(reg2, in_bytes(MultiBranchData::per_case_size()));
    imulptr(index, reg2); // XXX l ?
    addptr(index, in_bytes(MultiBranchData::case_array_offset())); // XXX l ?

    // Update the case count
    increment_mdp_data_at(mdp,
                          index,
                          in_bytes(MultiBranchData::relative_count_offset()));

    // The method data pointer needs to be updated.
    update_mdp_by_offset(mdp,
                         index,
                         in_bytes(MultiBranchData::
                                  relative_displacement_offset()));

    bind(profile_continue);
  }
}



void InterpreterMacroAssembler::verify_oop(Register reg, TosState state) {
  if (state == atos) {
    MacroAssembler::verify_oop(reg);
  }
}

void InterpreterMacroAssembler::verify_FPU(int stack_depth, TosState state) {
#ifndef _LP64
  if ((state == ftos && UseSSE < 1) ||
      (state == dtos && UseSSE < 2)) {
    MacroAssembler::verify_FPU(stack_depth);
  }
#endif
}

// Jump if ((*counter_addr += increment) & mask) satisfies the condition.
void InterpreterMacroAssembler::increment_mask_and_jump(Address counter_addr,
                                                        int increment, Address mask,
                                                        Register scratch, bool preloaded,
                                                        Condition cond, Label* where) {
  if (!preloaded) {
    movl(scratch, counter_addr);
  }
  incrementl(scratch, increment);
  movl(counter_addr, scratch);
  andl(scratch, mask);
  jcc(cond, *where);
}
#endif // CC_INTERP

void InterpreterMacroAssembler::notify_method_entry() {
  // Whenever JVMTI is interp_only_mode, method entry/exit events are sent to
  // track stack depth.  If it is possible to enter interp_only_mode we add
  // the code to check if the event should be sent.
  Register rthread = LP64_ONLY(r15_thread) NOT_LP64(rcx);
  Register rarg = LP64_ONLY(c_rarg1) NOT_LP64(rbx);
  if (JvmtiExport::can_post_interpreter_events()) {
    Label L;
    NOT_LP64(get_thread(rthread);)
    movl(rdx, Address(rthread, JavaThread::interp_only_mode_offset()));
    testl(rdx, rdx);
    jcc(Assembler::zero, L);
    call_VM(noreg, CAST_FROM_FN_PTR(address,
                                    InterpreterRuntime::post_method_entry));
    bind(L);
  }

  {
    SkipIfEqual skip(this, &DTraceMethodProbes, false);
    NOT_LP64(get_thread(rthread);)
    get_method(rarg);
    call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::dtrace_method_entry),
                 rthread, rarg);
  }

  // RedefineClasses() tracing support for obsolete method entry
  if (RC_TRACE_IN_RANGE(0x00001000, 0x00002000)) {
    NOT_LP64(get_thread(rthread);)
    get_method(rarg);
    call_VM_leaf(
      CAST_FROM_FN_PTR(address, SharedRuntime::rc_trace_method_entry),
      rthread, rarg);
  }
}


void InterpreterMacroAssembler::notify_method_exit(
    TosState state, NotifyMethodExitMode mode) {
  // Whenever JVMTI is interp_only_mode, method entry/exit events are sent to
  // track stack depth.  If it is possible to enter interp_only_mode we add
  // the code to check if the event should be sent.
  Register rthread = LP64_ONLY(r15_thread) NOT_LP64(rcx);
  Register rarg = LP64_ONLY(c_rarg1) NOT_LP64(rbx);
  if (mode == NotifyJVMTI && JvmtiExport::can_post_interpreter_events()) {
    Label L;
    // Note: frame::interpreter_frame_result has a dependency on how the
    // method result is saved across the call to post_method_exit. If this
    // is changed then the interpreter_frame_result implementation will
    // need to be updated too.

    // For c++ interpreter the result is always stored at a known location in the frame
    // template interpreter will leave it on the top of the stack.
    NOT_CC_INTERP(push(state);)
    NOT_LP64(get_thread(rthread);)
    movl(rdx, Address(rthread, JavaThread::interp_only_mode_offset()));
    testl(rdx, rdx);
    jcc(Assembler::zero, L);
    call_VM(noreg,
            CAST_FROM_FN_PTR(address, InterpreterRuntime::post_method_exit));
    bind(L);
    NOT_CC_INTERP(pop(state));
  }

  {
    SkipIfEqual skip(this, &DTraceMethodProbes, false);
    NOT_CC_INTERP(push(state));
    NOT_LP64(get_thread(rthread);)
    get_method(rarg);
    call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::dtrace_method_exit),
                 rthread, rarg);
    NOT_CC_INTERP(pop(state));
  }
}
