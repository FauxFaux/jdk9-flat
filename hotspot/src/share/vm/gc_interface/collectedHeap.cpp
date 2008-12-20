/*
 * Copyright 2001-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_collectedHeap.cpp.incl"


#ifdef ASSERT
int CollectedHeap::_fire_out_of_memory_count = 0;
#endif

size_t CollectedHeap::_filler_array_max_size = 0;

// Memory state functions.

CollectedHeap::CollectedHeap()
{
  const size_t max_len = size_t(arrayOopDesc::max_array_length(T_INT));
  const size_t elements_per_word = HeapWordSize / sizeof(jint);
  _filler_array_max_size = align_object_size(filler_array_hdr_size() +
                                             max_len * elements_per_word);

  _barrier_set = NULL;
  _is_gc_active = false;
  _total_collections = _total_full_collections = 0;
  _gc_cause = _gc_lastcause = GCCause::_no_gc;
  NOT_PRODUCT(_promotion_failure_alot_count = 0;)
  NOT_PRODUCT(_promotion_failure_alot_gc_number = 0;)

  if (UsePerfData) {
    EXCEPTION_MARK;

    // create the gc cause jvmstat counters
    _perf_gc_cause = PerfDataManager::create_string_variable(SUN_GC, "cause",
                             80, GCCause::to_string(_gc_cause), CHECK);

    _perf_gc_lastcause =
                PerfDataManager::create_string_variable(SUN_GC, "lastCause",
                             80, GCCause::to_string(_gc_lastcause), CHECK);
  }
}


#ifndef PRODUCT
void CollectedHeap::check_for_bad_heap_word_value(HeapWord* addr, size_t size) {
  if (CheckMemoryInitialization && ZapUnusedHeapArea) {
    for (size_t slot = 0; slot < size; slot += 1) {
      assert((*(intptr_t*) (addr + slot)) != ((intptr_t) badHeapWordVal),
             "Found badHeapWordValue in post-allocation check");
    }
  }
}

void CollectedHeap::check_for_non_bad_heap_word_value(HeapWord* addr, size_t size)
 {
  if (CheckMemoryInitialization && ZapUnusedHeapArea) {
    for (size_t slot = 0; slot < size; slot += 1) {
      assert((*(intptr_t*) (addr + slot)) == ((intptr_t) badHeapWordVal),
             "Found non badHeapWordValue in pre-allocation check");
    }
  }
}
#endif // PRODUCT

#ifdef ASSERT
void CollectedHeap::check_for_valid_allocation_state() {
  Thread *thread = Thread::current();
  // How to choose between a pending exception and a potential
  // OutOfMemoryError?  Don't allow pending exceptions.
  // This is a VM policy failure, so how do we exhaustively test it?
  assert(!thread->has_pending_exception(),
         "shouldn't be allocating with pending exception");
  if (StrictSafepointChecks) {
    assert(thread->allow_allocation(),
           "Allocation done by thread for which allocation is blocked "
           "by No_Allocation_Verifier!");
    // Allocation of an oop can always invoke a safepoint,
    // hence, the true argument
    thread->check_for_valid_safepoint_state(true);
  }
}
#endif

HeapWord* CollectedHeap::allocate_from_tlab_slow(Thread* thread, size_t size) {

  // Retain tlab and allocate object in shared space if
  // the amount free in the tlab is too large to discard.
  if (thread->tlab().free() > thread->tlab().refill_waste_limit()) {
    thread->tlab().record_slow_allocation(size);
    return NULL;
  }

  // Discard tlab and allocate a new one.
  // To minimize fragmentation, the last TLAB may be smaller than the rest.
  size_t new_tlab_size = thread->tlab().compute_size(size);

  thread->tlab().clear_before_allocation();

  if (new_tlab_size == 0) {
    return NULL;
  }

  // Allocate a new TLAB...
  HeapWord* obj = Universe::heap()->allocate_new_tlab(new_tlab_size);
  if (obj == NULL) {
    return NULL;
  }
  if (ZeroTLAB) {
    // ..and clear it.
    Copy::zero_to_words(obj, new_tlab_size);
  } else {
    // ...and clear just the allocated object.
    Copy::zero_to_words(obj, size);
  }
  thread->tlab().fill(obj, obj + size, new_tlab_size);
  return obj;
}

size_t CollectedHeap::filler_array_hdr_size() {
  return size_t(arrayOopDesc::header_size(T_INT));
}

size_t CollectedHeap::filler_array_min_size() {
  return align_object_size(filler_array_hdr_size());
}

size_t CollectedHeap::filler_array_max_size() {
  return _filler_array_max_size;
}

#ifdef ASSERT
void CollectedHeap::fill_args_check(HeapWord* start, size_t words)
{
  assert(words >= min_fill_size(), "too small to fill");
  assert(words % MinObjAlignment == 0, "unaligned size");
  assert(Universe::heap()->is_in_reserved(start), "not in heap");
  assert(Universe::heap()->is_in_reserved(start + words - 1), "not in heap");
}

void CollectedHeap::zap_filler_array(HeapWord* start, size_t words)
{
  if (ZapFillerObjects) {
    Copy::fill_to_words(start + filler_array_hdr_size(),
                        words - filler_array_hdr_size(), 0XDEAFBABE);
  }
}
#endif // ASSERT

void
CollectedHeap::fill_with_array(HeapWord* start, size_t words)
{
  assert(words >= filler_array_min_size(), "too small for an array");
  assert(words <= filler_array_max_size(), "too big for a single object");

  const size_t payload_size = words - filler_array_hdr_size();
  const size_t len = payload_size * HeapWordSize / sizeof(jint);

  // Set the length first for concurrent GC.
  ((arrayOop)start)->set_length((int)len);
  post_allocation_setup_common(Universe::intArrayKlassObj(), start, words);
  DEBUG_ONLY(zap_filler_array(start, words);)
}

void
CollectedHeap::fill_with_object_impl(HeapWord* start, size_t words)
{
  assert(words <= filler_array_max_size(), "too big for a single object");

  if (words >= filler_array_min_size()) {
    fill_with_array(start, words);
  } else if (words > 0) {
    assert(words == min_fill_size(), "unaligned size");
    post_allocation_setup_common(SystemDictionary::object_klass(), start,
                                 words);
  }
}

void CollectedHeap::fill_with_object(HeapWord* start, size_t words)
{
  DEBUG_ONLY(fill_args_check(start, words);)
  HandleMark hm;  // Free handles before leaving.
  fill_with_object_impl(start, words);
}

void CollectedHeap::fill_with_objects(HeapWord* start, size_t words)
{
  DEBUG_ONLY(fill_args_check(start, words);)
  HandleMark hm;  // Free handles before leaving.

#ifdef LP64
  // A single array can fill ~8G, so multiple objects are needed only in 64-bit.
  // First fill with arrays, ensuring that any remaining space is big enough to
  // fill.  The remainder is filled with a single object.
  const size_t min = min_fill_size();
  const size_t max = filler_array_max_size();
  while (words > max) {
    const size_t cur = words - max >= min ? max : max - min;
    fill_with_array(start, cur);
    start += cur;
    words -= cur;
  }
#endif

  fill_with_object_impl(start, words);
}

oop CollectedHeap::new_store_barrier(oop new_obj) {
  // %%% This needs refactoring.  (It was imported from the server compiler.)
  guarantee(can_elide_tlab_store_barriers(), "store barrier elision not supported");
  BarrierSet* bs = this->barrier_set();
  assert(bs->has_write_region_opt(), "Barrier set does not have write_region");
  int new_size = new_obj->size();
  bs->write_region(MemRegion((HeapWord*)new_obj, new_size));
  return new_obj;
}

HeapWord* CollectedHeap::allocate_new_tlab(size_t size) {
  guarantee(false, "thread-local allocation buffers not supported");
  return NULL;
}

void CollectedHeap::fill_all_tlabs(bool retire) {
  assert(UseTLAB, "should not reach here");
  // See note in ensure_parsability() below.
  assert(SafepointSynchronize::is_at_safepoint() ||
         !is_init_completed(),
         "should only fill tlabs at safepoint");
  // The main thread starts allocating via a TLAB even before it
  // has added itself to the threads list at vm boot-up.
  assert(Threads::first() != NULL,
         "Attempt to fill tlabs before main thread has been added"
         " to threads list is doomed to failure!");
  for(JavaThread *thread = Threads::first(); thread; thread = thread->next()) {
     thread->tlab().make_parsable(retire);
  }
}

void CollectedHeap::ensure_parsability(bool retire_tlabs) {
  // The second disjunct in the assertion below makes a concession
  // for the start-up verification done while the VM is being
  // created. Callers be careful that you know that mutators
  // aren't going to interfere -- for instance, this is permissible
  // if we are still single-threaded and have either not yet
  // started allocating (nothing much to verify) or we have
  // started allocating but are now a full-fledged JavaThread
  // (and have thus made our TLAB's) available for filling.
  assert(SafepointSynchronize::is_at_safepoint() ||
         !is_init_completed(),
         "Should only be called at a safepoint or at start-up"
         " otherwise concurrent mutator activity may make heap "
         " unparsable again");
  if (UseTLAB) {
    fill_all_tlabs(retire_tlabs);
  }
}

void CollectedHeap::accumulate_statistics_all_tlabs() {
  if (UseTLAB) {
    assert(SafepointSynchronize::is_at_safepoint() ||
         !is_init_completed(),
         "should only accumulate statistics on tlabs at safepoint");

    ThreadLocalAllocBuffer::accumulate_statistics_before_gc();
  }
}

void CollectedHeap::resize_all_tlabs() {
  if (UseTLAB) {
    assert(SafepointSynchronize::is_at_safepoint() ||
         !is_init_completed(),
         "should only resize tlabs at safepoint");

    ThreadLocalAllocBuffer::resize_all_tlabs();
  }
}
