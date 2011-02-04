/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "ci/ciKlass.hpp"
#include "ci/ciSymbol.hpp"
#include "ci/ciUtilities.hpp"
#include "oops/oop.inline.hpp"

// ciKlass
//
// This class represents a klassOop in the HotSpot virtual
// machine.

// ------------------------------------------------------------------
// ciKlass::ciKlass
ciKlass::ciKlass(KlassHandle h_k) : ciType(h_k) {
  assert(get_oop()->is_klass(), "wrong type");
  Klass* k = get_Klass();
  _layout_helper = k->layout_helper();
  Symbol* klass_name = k->name();
  assert(klass_name != NULL, "wrong ciKlass constructor");
  _name = CURRENT_ENV->get_symbol(klass_name);
}

// ------------------------------------------------------------------
// ciKlass::ciKlass
//
// Nameless klass variant.
ciKlass::ciKlass(KlassHandle h_k, ciSymbol* name) : ciType(h_k) {
  assert(get_oop()->is_klass(), "wrong type");
  _name = name;
  _layout_helper = Klass::_lh_neutral_value;
}

// ------------------------------------------------------------------
// ciKlass::ciKlass
//
// Unloaded klass variant.
ciKlass::ciKlass(ciSymbol* name, ciKlass* klass) : ciType(klass) {
  _name = name;
  _layout_helper = Klass::_lh_neutral_value;
}

// ------------------------------------------------------------------
// ciKlass::is_subtype_of
bool ciKlass::is_subtype_of(ciKlass* that) {
  assert(is_loaded() && that->is_loaded(), "must be loaded");
  assert(is_java_klass() && that->is_java_klass(), "must be java klasses");
  // Check to see if the klasses are identical.
  if (this == that) {
    return true;
  }

  VM_ENTRY_MARK;
  Klass* this_klass = get_Klass();
  klassOop that_klass = that->get_klassOop();
  bool result = this_klass->is_subtype_of(that_klass);

  return result;
}

// ------------------------------------------------------------------
// ciKlass::is_subclass_of
bool ciKlass::is_subclass_of(ciKlass* that) {
  assert(is_loaded() && that->is_loaded(), "must be loaded");
  assert(is_java_klass() && that->is_java_klass(), "must be java klasses");
  // Check to see if the klasses are identical.

  VM_ENTRY_MARK;
  Klass* this_klass = get_Klass();
  klassOop that_klass = that->get_klassOop();
  bool result = this_klass->is_subclass_of(that_klass);

  return result;
}

// ------------------------------------------------------------------
// ciKlass::super_depth
juint ciKlass::super_depth() {
  assert(is_loaded(), "must be loaded");
  assert(is_java_klass(), "must be java klasses");

  VM_ENTRY_MARK;
  Klass* this_klass = get_Klass();
  return this_klass->super_depth();
}

// ------------------------------------------------------------------
// ciKlass::super_check_offset
juint ciKlass::super_check_offset() {
  assert(is_loaded(), "must be loaded");
  assert(is_java_klass(), "must be java klasses");

  VM_ENTRY_MARK;
  Klass* this_klass = get_Klass();
  return this_klass->super_check_offset();
}

// ------------------------------------------------------------------
// ciKlass::super_of_depth
ciKlass* ciKlass::super_of_depth(juint i) {
  assert(is_loaded(), "must be loaded");
  assert(is_java_klass(), "must be java klasses");

  VM_ENTRY_MARK;
  Klass* this_klass = get_Klass();
  klassOop super = this_klass->primary_super_of_depth(i);
  return (super != NULL) ? CURRENT_THREAD_ENV->get_object(super)->as_klass() : NULL;
}

// ------------------------------------------------------------------
// ciKlass::can_be_primary_super
bool ciKlass::can_be_primary_super() {
  assert(is_loaded(), "must be loaded");
  assert(is_java_klass(), "must be java klasses");

  VM_ENTRY_MARK;
  Klass* this_klass = get_Klass();
  return this_klass->can_be_primary_super();
}

// ------------------------------------------------------------------
// ciKlass::least_common_ancestor
//
// Get the shared parent of two klasses.
//
// Implementation note: this method currently goes "over the wall"
// and does all of the work on the VM side.  It could be rewritten
// to use the super() method and do all of the work (aside from the
// lazy computation of super()) in native mode.  This may be
// worthwhile if the compiler is repeatedly requesting the same lca
// computation or possibly if most of the superklasses have already
// been created as ciObjects anyway.  Something to think about...
ciKlass*
ciKlass::least_common_ancestor(ciKlass* that) {
  assert(is_loaded() && that->is_loaded(), "must be loaded");
  assert(is_java_klass() && that->is_java_klass(), "must be java klasses");
  // Check to see if the klasses are identical.
  if (this == that) {
    return this;
  }

  VM_ENTRY_MARK;
  Klass* this_klass = get_Klass();
  Klass* that_klass = that->get_Klass();
  Klass* lca        = this_klass->LCA(that_klass);

  // Many times the LCA will be either this_klass or that_klass.
  // Treat these as special cases.
  if (lca == that_klass) {
    return that;
  }
  if (this_klass == lca) {
    return this;
  }

  // Create the ciInstanceKlass for the lca.
  ciKlass* result =
    CURRENT_THREAD_ENV->get_object(lca->as_klassOop())->as_klass();

  return result;
}

// ------------------------------------------------------------------
// ciKlass::find_klass
//
// Find a klass using this klass's class loader.
ciKlass* ciKlass::find_klass(ciSymbol* klass_name) {
  assert(is_loaded(), "cannot find_klass through an unloaded klass");
  return CURRENT_ENV->get_klass_by_name(this,
                                        klass_name, false);
}

// ------------------------------------------------------------------
// ciKlass::java_mirror
//
// Get the instance of java.lang.Class corresponding to this klass.
// If it is an unloaded instance or array klass, return an unloaded
// mirror object of type Class.
ciInstance* ciKlass::java_mirror() {
  GUARDED_VM_ENTRY(
    if (!is_loaded())
      return ciEnv::current()->get_unloaded_klass_mirror(this);
    oop java_mirror = get_Klass()->java_mirror();
    return CURRENT_ENV->get_object(java_mirror)->as_instance();
  )
}

// ------------------------------------------------------------------
// ciKlass::modifier_flags
jint ciKlass::modifier_flags() {
  assert(is_loaded(), "not loaded");
  GUARDED_VM_ENTRY(
    return get_Klass()->modifier_flags();
  )
}

// ------------------------------------------------------------------
// ciKlass::access_flags
jint ciKlass::access_flags() {
  assert(is_loaded(), "not loaded");
  GUARDED_VM_ENTRY(
    return get_Klass()->access_flags().as_int();
  )
}

// ------------------------------------------------------------------
// ciKlass::print_impl
//
// Implementation of the print method
void ciKlass::print_impl(outputStream* st) {
  st->print(" name=");
  print_name_on(st);
}

// ------------------------------------------------------------------
// ciKlass::print_name
//
// Print the name of this klass
void ciKlass::print_name_on(outputStream* st) {
  name()->print_symbol_on(st);
}
