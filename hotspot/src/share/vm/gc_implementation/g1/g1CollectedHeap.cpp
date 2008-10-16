/*
 * Copyright 2001-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "incls/_g1CollectedHeap.cpp.incl"

// turn it on so that the contents of the young list (scan-only /
// to-be-collected) are printed at "strategic" points before / during
// / after the collection --- this is useful for debugging
#define SCAN_ONLY_VERBOSE 0
// CURRENT STATUS
// This file is under construction.  Search for "FIXME".

// INVARIANTS/NOTES
//
// All allocation activity covered by the G1CollectedHeap interface is
//   serialized by acquiring the HeapLock.  This happens in
//   mem_allocate_work, which all such allocation functions call.
//   (Note that this does not apply to TLAB allocation, which is not part
//   of this interface: it is done by clients of this interface.)

// Local to this file.

// Finds the first HeapRegion.
// No longer used, but might be handy someday.

class FindFirstRegionClosure: public HeapRegionClosure {
  HeapRegion* _a_region;
public:
  FindFirstRegionClosure() : _a_region(NULL) {}
  bool doHeapRegion(HeapRegion* r) {
    _a_region = r;
    return true;
  }
  HeapRegion* result() { return _a_region; }
};


class RefineCardTableEntryClosure: public CardTableEntryClosure {
  SuspendibleThreadSet* _sts;
  G1RemSet* _g1rs;
  ConcurrentG1Refine* _cg1r;
  bool _concurrent;
public:
  RefineCardTableEntryClosure(SuspendibleThreadSet* sts,
                              G1RemSet* g1rs,
                              ConcurrentG1Refine* cg1r) :
    _sts(sts), _g1rs(g1rs), _cg1r(cg1r), _concurrent(true)
  {}
  bool do_card_ptr(jbyte* card_ptr, int worker_i) {
    _g1rs->concurrentRefineOneCard(card_ptr, worker_i);
    if (_concurrent && _sts->should_yield()) {
      // Caller will actually yield.
      return false;
    }
    // Otherwise, we finished successfully; return true.
    return true;
  }
  void set_concurrent(bool b) { _concurrent = b; }
};


class ClearLoggedCardTableEntryClosure: public CardTableEntryClosure {
  int _calls;
  G1CollectedHeap* _g1h;
  CardTableModRefBS* _ctbs;
  int _histo[256];
public:
  ClearLoggedCardTableEntryClosure() :
    _calls(0)
  {
    _g1h = G1CollectedHeap::heap();
    _ctbs = (CardTableModRefBS*)_g1h->barrier_set();
    for (int i = 0; i < 256; i++) _histo[i] = 0;
  }
  bool do_card_ptr(jbyte* card_ptr, int worker_i) {
    if (_g1h->is_in_reserved(_ctbs->addr_for(card_ptr))) {
      _calls++;
      unsigned char* ujb = (unsigned char*)card_ptr;
      int ind = (int)(*ujb);
      _histo[ind]++;
      *card_ptr = -1;
    }
    return true;
  }
  int calls() { return _calls; }
  void print_histo() {
    gclog_or_tty->print_cr("Card table value histogram:");
    for (int i = 0; i < 256; i++) {
      if (_histo[i] != 0) {
        gclog_or_tty->print_cr("  %d: %d", i, _histo[i]);
      }
    }
  }
};

class RedirtyLoggedCardTableEntryClosure: public CardTableEntryClosure {
  int _calls;
  G1CollectedHeap* _g1h;
  CardTableModRefBS* _ctbs;
public:
  RedirtyLoggedCardTableEntryClosure() :
    _calls(0)
  {
    _g1h = G1CollectedHeap::heap();
    _ctbs = (CardTableModRefBS*)_g1h->barrier_set();
  }
  bool do_card_ptr(jbyte* card_ptr, int worker_i) {
    if (_g1h->is_in_reserved(_ctbs->addr_for(card_ptr))) {
      _calls++;
      *card_ptr = 0;
    }
    return true;
  }
  int calls() { return _calls; }
};

YoungList::YoungList(G1CollectedHeap* g1h)
  : _g1h(g1h), _head(NULL),
    _scan_only_head(NULL), _scan_only_tail(NULL), _curr_scan_only(NULL),
    _length(0), _scan_only_length(0),
    _last_sampled_rs_lengths(0),
    _survivor_head(NULL), _survivors_tail(NULL), _survivor_length(0)
{
  guarantee( check_list_empty(false), "just making sure..." );
}

void YoungList::push_region(HeapRegion *hr) {
  assert(!hr->is_young(), "should not already be young");
  assert(hr->get_next_young_region() == NULL, "cause it should!");

  hr->set_next_young_region(_head);
  _head = hr;

  hr->set_young();
  double yg_surv_rate = _g1h->g1_policy()->predict_yg_surv_rate((int)_length);
  ++_length;
}

void YoungList::add_survivor_region(HeapRegion* hr) {
  assert(!hr->is_survivor(), "should not already be for survived");
  assert(hr->get_next_young_region() == NULL, "cause it should!");

  hr->set_next_young_region(_survivor_head);
  if (_survivor_head == NULL) {
    _survivors_tail = hr;
  }
  _survivor_head = hr;

  hr->set_survivor();
  ++_survivor_length;
}

HeapRegion* YoungList::pop_region() {
  while (_head != NULL) {
    assert( length() > 0, "list should not be empty" );
    HeapRegion* ret = _head;
    _head = ret->get_next_young_region();
    ret->set_next_young_region(NULL);
    --_length;
    assert(ret->is_young(), "region should be very young");

    // Replace 'Survivor' region type with 'Young'. So the region will
    // be treated as a young region and will not be 'confused' with
    // newly created survivor regions.
    if (ret->is_survivor()) {
      ret->set_young();
    }

    if (!ret->is_scan_only()) {
      return ret;
    }

    // scan-only, we'll add it to the scan-only list
    if (_scan_only_tail == NULL) {
      guarantee( _scan_only_head == NULL, "invariant" );

      _scan_only_head = ret;
      _curr_scan_only = ret;
    } else {
      guarantee( _scan_only_head != NULL, "invariant" );
      _scan_only_tail->set_next_young_region(ret);
    }
    guarantee( ret->get_next_young_region() == NULL, "invariant" );
    _scan_only_tail = ret;

    // no need to be tagged as scan-only any more
    ret->set_young();

    ++_scan_only_length;
  }
  assert( length() == 0, "list should be empty" );
  return NULL;
}

void YoungList::empty_list(HeapRegion* list) {
  while (list != NULL) {
    HeapRegion* next = list->get_next_young_region();
    list->set_next_young_region(NULL);
    list->uninstall_surv_rate_group();
    list->set_not_young();
    list = next;
  }
}

void YoungList::empty_list() {
  assert(check_list_well_formed(), "young list should be well formed");

  empty_list(_head);
  _head = NULL;
  _length = 0;

  empty_list(_scan_only_head);
  _scan_only_head = NULL;
  _scan_only_tail = NULL;
  _scan_only_length = 0;
  _curr_scan_only = NULL;

  empty_list(_survivor_head);
  _survivor_head = NULL;
  _survivors_tail = NULL;
  _survivor_length = 0;

  _last_sampled_rs_lengths = 0;

  assert(check_list_empty(false), "just making sure...");
}

bool YoungList::check_list_well_formed() {
  bool ret = true;

  size_t length = 0;
  HeapRegion* curr = _head;
  HeapRegion* last = NULL;
  while (curr != NULL) {
    if (!curr->is_young() || curr->is_scan_only()) {
      gclog_or_tty->print_cr("### YOUNG REGION "PTR_FORMAT"-"PTR_FORMAT" "
                             "incorrectly tagged (%d, %d)",
                             curr->bottom(), curr->end(),
                             curr->is_young(), curr->is_scan_only());
      ret = false;
    }
    ++length;
    last = curr;
    curr = curr->get_next_young_region();
  }
  ret = ret && (length == _length);

  if (!ret) {
    gclog_or_tty->print_cr("### YOUNG LIST seems not well formed!");
    gclog_or_tty->print_cr("###   list has %d entries, _length is %d",
                           length, _length);
  }

  bool scan_only_ret = true;
  length = 0;
  curr = _scan_only_head;
  last = NULL;
  while (curr != NULL) {
    if (!curr->is_young() || curr->is_scan_only()) {
      gclog_or_tty->print_cr("### SCAN-ONLY REGION "PTR_FORMAT"-"PTR_FORMAT" "
                             "incorrectly tagged (%d, %d)",
                             curr->bottom(), curr->end(),
                             curr->is_young(), curr->is_scan_only());
      scan_only_ret = false;
    }
    ++length;
    last = curr;
    curr = curr->get_next_young_region();
  }
  scan_only_ret = scan_only_ret && (length == _scan_only_length);

  if ( (last != _scan_only_tail) ||
       (_scan_only_head == NULL && _scan_only_tail != NULL) ||
       (_scan_only_head != NULL && _scan_only_tail == NULL) ) {
     gclog_or_tty->print_cr("## _scan_only_tail is set incorrectly");
     scan_only_ret = false;
  }

  if (_curr_scan_only != NULL && _curr_scan_only != _scan_only_head) {
    gclog_or_tty->print_cr("### _curr_scan_only is set incorrectly");
    scan_only_ret = false;
   }

  if (!scan_only_ret) {
    gclog_or_tty->print_cr("### SCAN-ONLY LIST seems not well formed!");
    gclog_or_tty->print_cr("###   list has %d entries, _scan_only_length is %d",
                  length, _scan_only_length);
  }

  return ret && scan_only_ret;
}

bool YoungList::check_list_empty(bool ignore_scan_only_list,
                                 bool check_sample) {
  bool ret = true;

  if (_length != 0) {
    gclog_or_tty->print_cr("### YOUNG LIST should have 0 length, not %d",
                  _length);
    ret = false;
  }
  if (check_sample && _last_sampled_rs_lengths != 0) {
    gclog_or_tty->print_cr("### YOUNG LIST has non-zero last sampled RS lengths");
    ret = false;
  }
  if (_head != NULL) {
    gclog_or_tty->print_cr("### YOUNG LIST does not have a NULL head");
    ret = false;
  }
  if (!ret) {
    gclog_or_tty->print_cr("### YOUNG LIST does not seem empty");
  }

  if (ignore_scan_only_list)
    return ret;

  bool scan_only_ret = true;
  if (_scan_only_length != 0) {
    gclog_or_tty->print_cr("### SCAN-ONLY LIST should have 0 length, not %d",
                  _scan_only_length);
    scan_only_ret = false;
  }
  if (_scan_only_head != NULL) {
    gclog_or_tty->print_cr("### SCAN-ONLY LIST does not have a NULL head");
     scan_only_ret = false;
  }
  if (_scan_only_tail != NULL) {
    gclog_or_tty->print_cr("### SCAN-ONLY LIST does not have a NULL tail");
    scan_only_ret = false;
  }
  if (!scan_only_ret) {
    gclog_or_tty->print_cr("### SCAN-ONLY LIST does not seem empty");
  }

  return ret && scan_only_ret;
}

void
YoungList::rs_length_sampling_init() {
  _sampled_rs_lengths = 0;
  _curr               = _head;
}

bool
YoungList::rs_length_sampling_more() {
  return _curr != NULL;
}

void
YoungList::rs_length_sampling_next() {
  assert( _curr != NULL, "invariant" );
  _sampled_rs_lengths += _curr->rem_set()->occupied();
  _curr = _curr->get_next_young_region();
  if (_curr == NULL) {
    _last_sampled_rs_lengths = _sampled_rs_lengths;
    // gclog_or_tty->print_cr("last sampled RS lengths = %d", _last_sampled_rs_lengths);
  }
}

void
YoungList::reset_auxilary_lists() {
  // We could have just "moved" the scan-only list to the young list.
  // However, the scan-only list is ordered according to the region
  // age in descending order, so, by moving one entry at a time, we
  // ensure that it is recreated in ascending order.

  guarantee( is_empty(), "young list should be empty" );
  assert(check_list_well_formed(), "young list should be well formed");

  // Add survivor regions to SurvRateGroup.
  _g1h->g1_policy()->note_start_adding_survivor_regions();
  for (HeapRegion* curr = _survivor_head;
       curr != NULL;
       curr = curr->get_next_young_region()) {
    _g1h->g1_policy()->set_region_survivors(curr);
  }
  _g1h->g1_policy()->note_stop_adding_survivor_regions();

  if (_survivor_head != NULL) {
    _head           = _survivor_head;
    _length         = _survivor_length + _scan_only_length;
    _survivors_tail->set_next_young_region(_scan_only_head);
  } else {
    _head           = _scan_only_head;
    _length         = _scan_only_length;
  }

  for (HeapRegion* curr = _scan_only_head;
       curr != NULL;
       curr = curr->get_next_young_region()) {
    curr->recalculate_age_in_surv_rate_group();
  }
  _scan_only_head   = NULL;
  _scan_only_tail   = NULL;
  _scan_only_length = 0;
  _curr_scan_only   = NULL;

  _survivor_head    = NULL;
  _survivors_tail   = NULL;
  _survivor_length  = 0;
  _g1h->g1_policy()->finished_recalculating_age_indexes();

  assert(check_list_well_formed(), "young list should be well formed");
}

void YoungList::print() {
  HeapRegion* lists[] = {_head,   _scan_only_head, _survivor_head};
  const char* names[] = {"YOUNG", "SCAN-ONLY",     "SURVIVOR"};

  for (unsigned int list = 0; list < ARRAY_SIZE(lists); ++list) {
    gclog_or_tty->print_cr("%s LIST CONTENTS", names[list]);
    HeapRegion *curr = lists[list];
    if (curr == NULL)
      gclog_or_tty->print_cr("  empty");
    while (curr != NULL) {
      gclog_or_tty->print_cr("  [%08x-%08x], t: %08x, P: %08x, N: %08x, C: %08x, "
                             "age: %4d, y: %d, s-o: %d, surv: %d",
                             curr->bottom(), curr->end(),
                             curr->top(),
                             curr->prev_top_at_mark_start(),
                             curr->next_top_at_mark_start(),
                             curr->top_at_conc_mark_count(),
                             curr->age_in_surv_rate_group_cond(),
                             curr->is_young(),
                             curr->is_scan_only(),
                             curr->is_survivor());
      curr = curr->get_next_young_region();
    }
  }

  gclog_or_tty->print_cr("");
}

void G1CollectedHeap::stop_conc_gc_threads() {
  _cg1r->cg1rThread()->stop();
  _czft->stop();
  _cmThread->stop();
}


void G1CollectedHeap::check_ct_logs_at_safepoint() {
  DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
  CardTableModRefBS* ct_bs = (CardTableModRefBS*)barrier_set();

  // Count the dirty cards at the start.
  CountNonCleanMemRegionClosure count1(this);
  ct_bs->mod_card_iterate(&count1);
  int orig_count = count1.n();

  // First clear the logged cards.
  ClearLoggedCardTableEntryClosure clear;
  dcqs.set_closure(&clear);
  dcqs.apply_closure_to_all_completed_buffers();
  dcqs.iterate_closure_all_threads(false);
  clear.print_histo();

  // Now ensure that there's no dirty cards.
  CountNonCleanMemRegionClosure count2(this);
  ct_bs->mod_card_iterate(&count2);
  if (count2.n() != 0) {
    gclog_or_tty->print_cr("Card table has %d entries; %d originally",
                           count2.n(), orig_count);
  }
  guarantee(count2.n() == 0, "Card table should be clean.");

  RedirtyLoggedCardTableEntryClosure redirty;
  JavaThread::dirty_card_queue_set().set_closure(&redirty);
  dcqs.apply_closure_to_all_completed_buffers();
  dcqs.iterate_closure_all_threads(false);
  gclog_or_tty->print_cr("Log entries = %d, dirty cards = %d.",
                         clear.calls(), orig_count);
  guarantee(redirty.calls() == clear.calls(),
            "Or else mechanism is broken.");

  CountNonCleanMemRegionClosure count3(this);
  ct_bs->mod_card_iterate(&count3);
  if (count3.n() != orig_count) {
    gclog_or_tty->print_cr("Should have restored them all: orig = %d, final = %d.",
                           orig_count, count3.n());
    guarantee(count3.n() >= orig_count, "Should have restored them all.");
  }

  JavaThread::dirty_card_queue_set().set_closure(_refine_cte_cl);
}

// Private class members.

G1CollectedHeap* G1CollectedHeap::_g1h;

// Private methods.

// Finds a HeapRegion that can be used to allocate a given size of block.


HeapRegion* G1CollectedHeap::newAllocRegion_work(size_t word_size,
                                                 bool do_expand,
                                                 bool zero_filled) {
  ConcurrentZFThread::note_region_alloc();
  HeapRegion* res = alloc_free_region_from_lists(zero_filled);
  if (res == NULL && do_expand) {
    expand(word_size * HeapWordSize);
    res = alloc_free_region_from_lists(zero_filled);
    assert(res == NULL ||
           (!res->isHumongous() &&
            (!zero_filled ||
             res->zero_fill_state() == HeapRegion::Allocated)),
           "Alloc Regions must be zero filled (and non-H)");
  }
  if (res != NULL && res->is_empty()) _free_regions--;
  assert(res == NULL ||
         (!res->isHumongous() &&
          (!zero_filled ||
           res->zero_fill_state() == HeapRegion::Allocated)),
         "Non-young alloc Regions must be zero filled (and non-H)");

  if (G1TraceRegions) {
    if (res != NULL) {
      gclog_or_tty->print_cr("new alloc region %d:["PTR_FORMAT", "PTR_FORMAT"], "
                             "top "PTR_FORMAT,
                             res->hrs_index(), res->bottom(), res->end(), res->top());
    }
  }

  return res;
}

HeapRegion* G1CollectedHeap::newAllocRegionWithExpansion(int purpose,
                                                         size_t word_size,
                                                         bool zero_filled) {
  HeapRegion* alloc_region = NULL;
  if (_gc_alloc_region_counts[purpose] < g1_policy()->max_regions(purpose)) {
    alloc_region = newAllocRegion_work(word_size, true, zero_filled);
    if (purpose == GCAllocForSurvived && alloc_region != NULL) {
      _young_list->add_survivor_region(alloc_region);
    }
    ++_gc_alloc_region_counts[purpose];
  } else {
    g1_policy()->note_alloc_region_limit_reached(purpose);
  }
  return alloc_region;
}

// If could fit into free regions w/o expansion, try.
// Otherwise, if can expand, do so.
// Otherwise, if using ex regions might help, try with ex given back.
HeapWord* G1CollectedHeap::humongousObjAllocate(size_t word_size) {
  assert(regions_accounted_for(), "Region leakage!");

  // We can't allocate H regions while cleanupComplete is running, since
  // some of the regions we find to be empty might not yet be added to the
  // unclean list.  (If we're already at a safepoint, this call is
  // unnecessary, not to mention wrong.)
  if (!SafepointSynchronize::is_at_safepoint())
    wait_for_cleanup_complete();

  size_t num_regions =
    round_to(word_size, HeapRegion::GrainWords) / HeapRegion::GrainWords;

  // Special case if < one region???

  // Remember the ft size.
  size_t x_size = expansion_regions();

  HeapWord* res = NULL;
  bool eliminated_allocated_from_lists = false;

  // Can the allocation potentially fit in the free regions?
  if (free_regions() >= num_regions) {
    res = _hrs->obj_allocate(word_size);
  }
  if (res == NULL) {
    // Try expansion.
    size_t fs = _hrs->free_suffix();
    if (fs + x_size >= num_regions) {
      expand((num_regions - fs) * HeapRegion::GrainBytes);
      res = _hrs->obj_allocate(word_size);
      assert(res != NULL, "This should have worked.");
    } else {
      // Expansion won't help.  Are there enough free regions if we get rid
      // of reservations?
      size_t avail = free_regions();
      if (avail >= num_regions) {
        res = _hrs->obj_allocate(word_size);
        if (res != NULL) {
          remove_allocated_regions_from_lists();
          eliminated_allocated_from_lists = true;
        }
      }
    }
  }
  if (res != NULL) {
    // Increment by the number of regions allocated.
    // FIXME: Assumes regions all of size GrainBytes.
#ifndef PRODUCT
    mr_bs()->verify_clean_region(MemRegion(res, res + num_regions *
                                           HeapRegion::GrainWords));
#endif
    if (!eliminated_allocated_from_lists)
      remove_allocated_regions_from_lists();
    _summary_bytes_used += word_size * HeapWordSize;
    _free_regions -= num_regions;
    _num_humongous_regions += (int) num_regions;
  }
  assert(regions_accounted_for(), "Region Leakage");
  return res;
}

HeapWord*
G1CollectedHeap::attempt_allocation_slow(size_t word_size,
                                         bool permit_collection_pause) {
  HeapWord* res = NULL;
  HeapRegion* allocated_young_region = NULL;

  assert( SafepointSynchronize::is_at_safepoint() ||
          Heap_lock->owned_by_self(), "pre condition of the call" );

  if (isHumongous(word_size)) {
    // Allocation of a humongous object can, in a sense, complete a
    // partial region, if the previous alloc was also humongous, and
    // caused the test below to succeed.
    if (permit_collection_pause)
      do_collection_pause_if_appropriate(word_size);
    res = humongousObjAllocate(word_size);
    assert(_cur_alloc_region == NULL
           || !_cur_alloc_region->isHumongous(),
           "Prevent a regression of this bug.");

  } else {
    // We may have concurrent cleanup working at the time. Wait for it
    // to complete. In the future we would probably want to make the
    // concurrent cleanup truly concurrent by decoupling it from the
    // allocation.
    if (!SafepointSynchronize::is_at_safepoint())
      wait_for_cleanup_complete();
    // If we do a collection pause, this will be reset to a non-NULL
    // value.  If we don't, nulling here ensures that we allocate a new
    // region below.
    if (_cur_alloc_region != NULL) {
      // We're finished with the _cur_alloc_region.
      _summary_bytes_used += _cur_alloc_region->used();
      _cur_alloc_region = NULL;
    }
    assert(_cur_alloc_region == NULL, "Invariant.");
    // Completion of a heap region is perhaps a good point at which to do
    // a collection pause.
    if (permit_collection_pause)
      do_collection_pause_if_appropriate(word_size);
    // Make sure we have an allocation region available.
    if (_cur_alloc_region == NULL) {
      if (!SafepointSynchronize::is_at_safepoint())
        wait_for_cleanup_complete();
      bool next_is_young = should_set_young_locked();
      // If the next region is not young, make sure it's zero-filled.
      _cur_alloc_region = newAllocRegion(word_size, !next_is_young);
      if (_cur_alloc_region != NULL) {
        _summary_bytes_used -= _cur_alloc_region->used();
        if (next_is_young) {
          set_region_short_lived_locked(_cur_alloc_region);
          allocated_young_region = _cur_alloc_region;
        }
      }
    }
    assert(_cur_alloc_region == NULL || !_cur_alloc_region->isHumongous(),
           "Prevent a regression of this bug.");

    // Now retry the allocation.
    if (_cur_alloc_region != NULL) {
      res = _cur_alloc_region->allocate(word_size);
    }
  }

  // NOTE: fails frequently in PRT
  assert(regions_accounted_for(), "Region leakage!");

  if (res != NULL) {
    if (!SafepointSynchronize::is_at_safepoint()) {
      assert( permit_collection_pause, "invariant" );
      assert( Heap_lock->owned_by_self(), "invariant" );
      Heap_lock->unlock();
    }

    if (allocated_young_region != NULL) {
      HeapRegion* hr = allocated_young_region;
      HeapWord* bottom = hr->bottom();
      HeapWord* end = hr->end();
      MemRegion mr(bottom, end);
      ((CardTableModRefBS*)_g1h->barrier_set())->dirty(mr);
    }
  }

  assert( SafepointSynchronize::is_at_safepoint() ||
          (res == NULL && Heap_lock->owned_by_self()) ||
          (res != NULL && !Heap_lock->owned_by_self()),
          "post condition of the call" );

  return res;
}

HeapWord*
G1CollectedHeap::mem_allocate(size_t word_size,
                              bool   is_noref,
                              bool   is_tlab,
                              bool* gc_overhead_limit_was_exceeded) {
  debug_only(check_for_valid_allocation_state());
  assert(no_gc_in_progress(), "Allocation during gc not allowed");
  HeapWord* result = NULL;

  // Loop until the allocation is satisified,
  // or unsatisfied after GC.
  for (int try_count = 1; /* return or throw */; try_count += 1) {
    int gc_count_before;
    {
      Heap_lock->lock();
      result = attempt_allocation(word_size);
      if (result != NULL) {
        // attempt_allocation should have unlocked the heap lock
        assert(is_in(result), "result not in heap");
        return result;
      }
      // Read the gc count while the heap lock is held.
      gc_count_before = SharedHeap::heap()->total_collections();
      Heap_lock->unlock();
    }

    // Create the garbage collection operation...
    VM_G1CollectForAllocation op(word_size,
                                 gc_count_before);

    // ...and get the VM thread to execute it.
    VMThread::execute(&op);
    if (op.prologue_succeeded()) {
      result = op.result();
      assert(result == NULL || is_in(result), "result not in heap");
      return result;
    }

    // Give a warning if we seem to be looping forever.
    if ((QueuedAllocationWarningCount > 0) &&
        (try_count % QueuedAllocationWarningCount == 0)) {
      warning("G1CollectedHeap::mem_allocate_work retries %d times",
              try_count);
    }
  }
}

void G1CollectedHeap::abandon_cur_alloc_region() {
  if (_cur_alloc_region != NULL) {
    // We're finished with the _cur_alloc_region.
    if (_cur_alloc_region->is_empty()) {
      _free_regions++;
      free_region(_cur_alloc_region);
    } else {
      _summary_bytes_used += _cur_alloc_region->used();
    }
    _cur_alloc_region = NULL;
  }
}

class PostMCRemSetClearClosure: public HeapRegionClosure {
  ModRefBarrierSet* _mr_bs;
public:
  PostMCRemSetClearClosure(ModRefBarrierSet* mr_bs) : _mr_bs(mr_bs) {}
  bool doHeapRegion(HeapRegion* r) {
    r->reset_gc_time_stamp();
    if (r->continuesHumongous())
      return false;
    HeapRegionRemSet* hrrs = r->rem_set();
    if (hrrs != NULL) hrrs->clear();
    // You might think here that we could clear just the cards
    // corresponding to the used region.  But no: if we leave a dirty card
    // in a region we might allocate into, then it would prevent that card
    // from being enqueued, and cause it to be missed.
    // Re: the performance cost: we shouldn't be doing full GC anyway!
    _mr_bs->clear(MemRegion(r->bottom(), r->end()));
    return false;
  }
};


class PostMCRemSetInvalidateClosure: public HeapRegionClosure {
  ModRefBarrierSet* _mr_bs;
public:
  PostMCRemSetInvalidateClosure(ModRefBarrierSet* mr_bs) : _mr_bs(mr_bs) {}
  bool doHeapRegion(HeapRegion* r) {
    if (r->continuesHumongous()) return false;
    if (r->used_region().word_size() != 0) {
      _mr_bs->invalidate(r->used_region(), true /*whole heap*/);
    }
    return false;
  }
};

void G1CollectedHeap::do_collection(bool full, bool clear_all_soft_refs,
                                    size_t word_size) {
  ResourceMark rm;

  if (full && DisableExplicitGC) {
    gclog_or_tty->print("\n\n\nDisabling Explicit GC\n\n\n");
    return;
  }

  assert(SafepointSynchronize::is_at_safepoint(), "should be at safepoint");
  assert(Thread::current() == VMThread::vm_thread(), "should be in vm thread");

  if (GC_locker::is_active()) {
    return; // GC is disabled (e.g. JNI GetXXXCritical operation)
  }

  {
    IsGCActiveMark x;

    // Timing
    gclog_or_tty->date_stamp(PrintGC && PrintGCDateStamps);
    TraceCPUTime tcpu(PrintGCDetails, true, gclog_or_tty);
    TraceTime t(full ? "Full GC (System.gc())" : "Full GC", PrintGC, true, gclog_or_tty);

    double start = os::elapsedTime();
    GCOverheadReporter::recordSTWStart(start);
    g1_policy()->record_full_collection_start();

    gc_prologue(true);
    increment_total_collections();

    size_t g1h_prev_used = used();
    assert(used() == recalculate_used(), "Should be equal");

    if (VerifyBeforeGC && total_collections() >= VerifyGCStartAt) {
      HandleMark hm;  // Discard invalid handles created during verification
      prepare_for_verify();
      gclog_or_tty->print(" VerifyBeforeGC:");
      Universe::verify(true);
    }
    assert(regions_accounted_for(), "Region leakage!");

    COMPILER2_PRESENT(DerivedPointerTable::clear());

    // We want to discover references, but not process them yet.
    // This mode is disabled in
    // instanceRefKlass::process_discovered_references if the
    // generation does some collection work, or
    // instanceRefKlass::enqueue_discovered_references if the
    // generation returns without doing any work.
    ref_processor()->disable_discovery();
    ref_processor()->abandon_partial_discovery();
    ref_processor()->verify_no_references_recorded();

    // Abandon current iterations of concurrent marking and concurrent
    // refinement, if any are in progress.
    concurrent_mark()->abort();

    // Make sure we'll choose a new allocation region afterwards.
    abandon_cur_alloc_region();
    assert(_cur_alloc_region == NULL, "Invariant.");
    g1_rem_set()->as_HRInto_G1RemSet()->cleanupHRRS();
    tear_down_region_lists();
    set_used_regions_to_need_zero_fill();
    if (g1_policy()->in_young_gc_mode()) {
      empty_young_list();
      g1_policy()->set_full_young_gcs(true);
    }

    // Temporarily make reference _discovery_ single threaded (non-MT).
    ReferenceProcessorMTMutator rp_disc_ser(ref_processor(), false);

    // Temporarily make refs discovery atomic
    ReferenceProcessorAtomicMutator rp_disc_atomic(ref_processor(), true);

    // Temporarily clear _is_alive_non_header
    ReferenceProcessorIsAliveMutator rp_is_alive_null(ref_processor(), NULL);

    ref_processor()->enable_discovery();

    // Do collection work
    {
      HandleMark hm;  // Discard invalid handles created during gc
      G1MarkSweep::invoke_at_safepoint(ref_processor(), clear_all_soft_refs);
    }
    // Because freeing humongous regions may have added some unclean
    // regions, it is necessary to tear down again before rebuilding.
    tear_down_region_lists();
    rebuild_region_lists();

    _summary_bytes_used = recalculate_used();

    ref_processor()->enqueue_discovered_references();

    COMPILER2_PRESENT(DerivedPointerTable::update_pointers());

    if (VerifyAfterGC && total_collections() >= VerifyGCStartAt) {
      HandleMark hm;  // Discard invalid handles created during verification
      gclog_or_tty->print(" VerifyAfterGC:");
      Universe::verify(false);
    }
    NOT_PRODUCT(ref_processor()->verify_no_references_recorded());

    reset_gc_time_stamp();
    // Since everything potentially moved, we will clear all remembered
    // sets, and clear all cards.  Later we will also cards in the used
    // portion of the heap after the resizing (which could be a shrinking.)
    // We will also reset the GC time stamps of the regions.
    PostMCRemSetClearClosure rs_clear(mr_bs());
    heap_region_iterate(&rs_clear);

    // Resize the heap if necessary.
    resize_if_necessary_after_full_collection(full ? 0 : word_size);

    // Since everything potentially moved, we will clear all remembered
    // sets, but also dirty all cards corresponding to used regions.
    PostMCRemSetInvalidateClosure rs_invalidate(mr_bs());
    heap_region_iterate(&rs_invalidate);
    if (_cg1r->use_cache()) {
      _cg1r->clear_and_record_card_counts();
      _cg1r->clear_hot_cache();
    }

    if (PrintGC) {
      print_size_transition(gclog_or_tty, g1h_prev_used, used(), capacity());
    }

    if (true) { // FIXME
      // Ask the permanent generation to adjust size for full collections
      perm()->compute_new_size();
    }

    double end = os::elapsedTime();
    GCOverheadReporter::recordSTWEnd(end);
    g1_policy()->record_full_collection_end();

    gc_epilogue(true);

    // Abandon concurrent refinement.  This must happen last: in the
    // dirty-card logging system, some cards may be dirty by weak-ref
    // processing, and may be enqueued.  But the whole card table is
    // dirtied, so this should abandon those logs, and set "do_traversal"
    // to true.
    concurrent_g1_refine()->set_pya_restart();

    assert(regions_accounted_for(), "Region leakage!");
  }

  if (g1_policy()->in_young_gc_mode()) {
    _young_list->reset_sampled_info();
    assert( check_young_list_empty(false, false),
            "young list should be empty at this point");
  }
}

void G1CollectedHeap::do_full_collection(bool clear_all_soft_refs) {
  do_collection(true, clear_all_soft_refs, 0);
}

// This code is mostly copied from TenuredGeneration.
void
G1CollectedHeap::
resize_if_necessary_after_full_collection(size_t word_size) {
  assert(MinHeapFreeRatio <= MaxHeapFreeRatio, "sanity check");

  // Include the current allocation, if any, and bytes that will be
  // pre-allocated to support collections, as "used".
  const size_t used_after_gc = used();
  const size_t capacity_after_gc = capacity();
  const size_t free_after_gc = capacity_after_gc - used_after_gc;

  // We don't have floating point command-line arguments
  const double minimum_free_percentage = (double) MinHeapFreeRatio / 100;
  const double maximum_used_percentage = 1.0 - minimum_free_percentage;
  const double maximum_free_percentage = (double) MaxHeapFreeRatio / 100;
  const double minimum_used_percentage = 1.0 - maximum_free_percentage;

  size_t minimum_desired_capacity = (size_t) (used_after_gc / maximum_used_percentage);
  size_t maximum_desired_capacity = (size_t) (used_after_gc / minimum_used_percentage);

  // Don't shrink less than the initial size.
  minimum_desired_capacity =
    MAX2(minimum_desired_capacity,
         collector_policy()->initial_heap_byte_size());
  maximum_desired_capacity =
    MAX2(maximum_desired_capacity,
         collector_policy()->initial_heap_byte_size());

  // We are failing here because minimum_desired_capacity is
  assert(used_after_gc <= minimum_desired_capacity, "sanity check");
  assert(minimum_desired_capacity <= maximum_desired_capacity, "sanity check");

  if (PrintGC && Verbose) {
    const double free_percentage = ((double)free_after_gc) / capacity();
    gclog_or_tty->print_cr("Computing new size after full GC ");
    gclog_or_tty->print_cr("  "
                           "  minimum_free_percentage: %6.2f",
                           minimum_free_percentage);
    gclog_or_tty->print_cr("  "
                           "  maximum_free_percentage: %6.2f",
                           maximum_free_percentage);
    gclog_or_tty->print_cr("  "
                           "  capacity: %6.1fK"
                           "  minimum_desired_capacity: %6.1fK"
                           "  maximum_desired_capacity: %6.1fK",
                           capacity() / (double) K,
                           minimum_desired_capacity / (double) K,
                           maximum_desired_capacity / (double) K);
    gclog_or_tty->print_cr("  "
                           "   free_after_gc   : %6.1fK"
                           "   used_after_gc   : %6.1fK",
                           free_after_gc / (double) K,
                           used_after_gc / (double) K);
    gclog_or_tty->print_cr("  "
                           "   free_percentage: %6.2f",
                           free_percentage);
  }
  if (capacity() < minimum_desired_capacity) {
    // Don't expand unless it's significant
    size_t expand_bytes = minimum_desired_capacity - capacity_after_gc;
    expand(expand_bytes);
    if (PrintGC && Verbose) {
      gclog_or_tty->print_cr("    expanding:"
                             "  minimum_desired_capacity: %6.1fK"
                             "  expand_bytes: %6.1fK",
                             minimum_desired_capacity / (double) K,
                             expand_bytes / (double) K);
    }

    // No expansion, now see if we want to shrink
  } else if (capacity() > maximum_desired_capacity) {
    // Capacity too large, compute shrinking size
    size_t shrink_bytes = capacity_after_gc - maximum_desired_capacity;
    shrink(shrink_bytes);
    if (PrintGC && Verbose) {
      gclog_or_tty->print_cr("  "
                             "  shrinking:"
                             "  initSize: %.1fK"
                             "  maximum_desired_capacity: %.1fK",
                             collector_policy()->initial_heap_byte_size() / (double) K,
                             maximum_desired_capacity / (double) K);
      gclog_or_tty->print_cr("  "
                             "  shrink_bytes: %.1fK",
                             shrink_bytes / (double) K);
    }
  }
}


HeapWord*
G1CollectedHeap::satisfy_failed_allocation(size_t word_size) {
  HeapWord* result = NULL;

  // In a G1 heap, we're supposed to keep allocation from failing by
  // incremental pauses.  Therefore, at least for now, we'll favor
  // expansion over collection.  (This might change in the future if we can
  // do something smarter than full collection to satisfy a failed alloc.)

  result = expand_and_allocate(word_size);
  if (result != NULL) {
    assert(is_in(result), "result not in heap");
    return result;
  }

  // OK, I guess we have to try collection.

  do_collection(false, false, word_size);

  result = attempt_allocation(word_size, /*permit_collection_pause*/false);

  if (result != NULL) {
    assert(is_in(result), "result not in heap");
    return result;
  }

  // Try collecting soft references.
  do_collection(false, true, word_size);
  result = attempt_allocation(word_size, /*permit_collection_pause*/false);
  if (result != NULL) {
    assert(is_in(result), "result not in heap");
    return result;
  }

  // What else?  We might try synchronous finalization later.  If the total
  // space available is large enough for the allocation, then a more
  // complete compaction phase than we've tried so far might be
  // appropriate.
  return NULL;
}

// Attempting to expand the heap sufficiently
// to support an allocation of the given "word_size".  If
// successful, perform the allocation and return the address of the
// allocated block, or else "NULL".

HeapWord* G1CollectedHeap::expand_and_allocate(size_t word_size) {
  size_t expand_bytes = word_size * HeapWordSize;
  if (expand_bytes < MinHeapDeltaBytes) {
    expand_bytes = MinHeapDeltaBytes;
  }
  expand(expand_bytes);
  assert(regions_accounted_for(), "Region leakage!");
  HeapWord* result = attempt_allocation(word_size, false /* permit_collection_pause */);
  return result;
}

size_t G1CollectedHeap::free_region_if_totally_empty(HeapRegion* hr) {
  size_t pre_used = 0;
  size_t cleared_h_regions = 0;
  size_t freed_regions = 0;
  UncleanRegionList local_list;
  free_region_if_totally_empty_work(hr, pre_used, cleared_h_regions,
                                    freed_regions, &local_list);

  finish_free_region_work(pre_used, cleared_h_regions, freed_regions,
                          &local_list);
  return pre_used;
}

void
G1CollectedHeap::free_region_if_totally_empty_work(HeapRegion* hr,
                                                   size_t& pre_used,
                                                   size_t& cleared_h,
                                                   size_t& freed_regions,
                                                   UncleanRegionList* list,
                                                   bool par) {
  assert(!hr->continuesHumongous(), "should have filtered these out");
  size_t res = 0;
  if (!hr->popular() && hr->used() > 0 && hr->garbage_bytes() == hr->used()) {
    if (!hr->is_young()) {
      if (G1PolicyVerbose > 0)
        gclog_or_tty->print_cr("Freeing empty region "PTR_FORMAT "(" SIZE_FORMAT " bytes)"
                               " during cleanup", hr, hr->used());
      free_region_work(hr, pre_used, cleared_h, freed_regions, list, par);
    }
  }
}

// FIXME: both this and shrink could probably be more efficient by
// doing one "VirtualSpace::expand_by" call rather than several.
void G1CollectedHeap::expand(size_t expand_bytes) {
  size_t old_mem_size = _g1_storage.committed_size();
  // We expand by a minimum of 1K.
  expand_bytes = MAX2(expand_bytes, (size_t)K);
  size_t aligned_expand_bytes =
    ReservedSpace::page_align_size_up(expand_bytes);
  aligned_expand_bytes = align_size_up(aligned_expand_bytes,
                                       HeapRegion::GrainBytes);
  expand_bytes = aligned_expand_bytes;
  while (expand_bytes > 0) {
    HeapWord* base = (HeapWord*)_g1_storage.high();
    // Commit more storage.
    bool successful = _g1_storage.expand_by(HeapRegion::GrainBytes);
    if (!successful) {
        expand_bytes = 0;
    } else {
      expand_bytes -= HeapRegion::GrainBytes;
      // Expand the committed region.
      HeapWord* high = (HeapWord*) _g1_storage.high();
      _g1_committed.set_end(high);
      // Create a new HeapRegion.
      MemRegion mr(base, high);
      bool is_zeroed = !_g1_max_committed.contains(base);
      HeapRegion* hr = new HeapRegion(_bot_shared, mr, is_zeroed);

      // Now update max_committed if necessary.
      _g1_max_committed.set_end(MAX2(_g1_max_committed.end(), high));

      // Add it to the HeapRegionSeq.
      _hrs->insert(hr);
      // Set the zero-fill state, according to whether it's already
      // zeroed.
      {
        MutexLockerEx x(ZF_mon, Mutex::_no_safepoint_check_flag);
        if (is_zeroed) {
          hr->set_zero_fill_complete();
          put_free_region_on_list_locked(hr);
        } else {
          hr->set_zero_fill_needed();
          put_region_on_unclean_list_locked(hr);
        }
      }
      _free_regions++;
      // And we used up an expansion region to create it.
      _expansion_regions--;
      // Tell the cardtable about it.
      Universe::heap()->barrier_set()->resize_covered_region(_g1_committed);
      // And the offset table as well.
      _bot_shared->resize(_g1_committed.word_size());
    }
  }
  if (Verbose && PrintGC) {
    size_t new_mem_size = _g1_storage.committed_size();
    gclog_or_tty->print_cr("Expanding garbage-first heap from %ldK by %ldK to %ldK",
                           old_mem_size/K, aligned_expand_bytes/K,
                           new_mem_size/K);
  }
}

void G1CollectedHeap::shrink_helper(size_t shrink_bytes)
{
  size_t old_mem_size = _g1_storage.committed_size();
  size_t aligned_shrink_bytes =
    ReservedSpace::page_align_size_down(shrink_bytes);
  aligned_shrink_bytes = align_size_down(aligned_shrink_bytes,
                                         HeapRegion::GrainBytes);
  size_t num_regions_deleted = 0;
  MemRegion mr = _hrs->shrink_by(aligned_shrink_bytes, num_regions_deleted);

  assert(mr.end() == (HeapWord*)_g1_storage.high(), "Bad shrink!");
  if (mr.byte_size() > 0)
    _g1_storage.shrink_by(mr.byte_size());
  assert(mr.start() == (HeapWord*)_g1_storage.high(), "Bad shrink!");

  _g1_committed.set_end(mr.start());
  _free_regions -= num_regions_deleted;
  _expansion_regions += num_regions_deleted;

  // Tell the cardtable about it.
  Universe::heap()->barrier_set()->resize_covered_region(_g1_committed);

  // And the offset table as well.
  _bot_shared->resize(_g1_committed.word_size());

  HeapRegionRemSet::shrink_heap(n_regions());

  if (Verbose && PrintGC) {
    size_t new_mem_size = _g1_storage.committed_size();
    gclog_or_tty->print_cr("Shrinking garbage-first heap from %ldK by %ldK to %ldK",
                           old_mem_size/K, aligned_shrink_bytes/K,
                           new_mem_size/K);
  }
}

void G1CollectedHeap::shrink(size_t shrink_bytes) {
  release_gc_alloc_regions();
  tear_down_region_lists();  // We will rebuild them in a moment.
  shrink_helper(shrink_bytes);
  rebuild_region_lists();
}

// Public methods.

#ifdef _MSC_VER // the use of 'this' below gets a warning, make it go away
#pragma warning( disable:4355 ) // 'this' : used in base member initializer list
#endif // _MSC_VER


G1CollectedHeap::G1CollectedHeap(G1CollectorPolicy* policy_) :
  SharedHeap(policy_),
  _g1_policy(policy_),
  _ref_processor(NULL),
  _process_strong_tasks(new SubTasksDone(G1H_PS_NumElements)),
  _bot_shared(NULL),
  _par_alloc_during_gc_lock(Mutex::leaf, "par alloc during GC lock"),
  _objs_with_preserved_marks(NULL), _preserved_marks_of_objs(NULL),
  _evac_failure_scan_stack(NULL) ,
  _mark_in_progress(false),
  _cg1r(NULL), _czft(NULL), _summary_bytes_used(0),
  _cur_alloc_region(NULL),
  _refine_cte_cl(NULL),
  _free_region_list(NULL), _free_region_list_size(0),
  _free_regions(0),
  _popular_object_boundary(NULL),
  _cur_pop_hr_index(0),
  _popular_regions_to_be_evacuated(NULL),
  _pop_obj_rc_at_copy(),
  _full_collection(false),
  _unclean_region_list(),
  _unclean_regions_coming(false),
  _young_list(new YoungList(this)),
  _gc_time_stamp(0),
  _surviving_young_words(NULL)
{
  _g1h = this; // To catch bugs.
  if (_process_strong_tasks == NULL || !_process_strong_tasks->valid()) {
    vm_exit_during_initialization("Failed necessary allocation.");
  }
  int n_queues = MAX2((int)ParallelGCThreads, 1);
  _task_queues = new RefToScanQueueSet(n_queues);

  int n_rem_sets = HeapRegionRemSet::num_par_rem_sets();
  assert(n_rem_sets > 0, "Invariant.");

  HeapRegionRemSetIterator** iter_arr =
    NEW_C_HEAP_ARRAY(HeapRegionRemSetIterator*, n_queues);
  for (int i = 0; i < n_queues; i++) {
    iter_arr[i] = new HeapRegionRemSetIterator();
  }
  _rem_set_iterator = iter_arr;

  for (int i = 0; i < n_queues; i++) {
    RefToScanQueue* q = new RefToScanQueue();
    q->initialize();
    _task_queues->register_queue(i, q);
  }

  for (int ap = 0; ap < GCAllocPurposeCount; ++ap) {
    _gc_alloc_regions[ap]       = NULL;
    _gc_alloc_region_counts[ap] = 0;
  }
  guarantee(_task_queues != NULL, "task_queues allocation failure.");
}

jint G1CollectedHeap::initialize() {
  os::enable_vtime();

  // Necessary to satisfy locking discipline assertions.

  MutexLocker x(Heap_lock);

  // While there are no constraints in the GC code that HeapWordSize
  // be any particular value, there are multiple other areas in the
  // system which believe this to be true (e.g. oop->object_size in some
  // cases incorrectly returns the size in wordSize units rather than
  // HeapWordSize).
  guarantee(HeapWordSize == wordSize, "HeapWordSize must equal wordSize");

  size_t init_byte_size = collector_policy()->initial_heap_byte_size();
  size_t max_byte_size = collector_policy()->max_heap_byte_size();

  // Ensure that the sizes are properly aligned.
  Universe::check_alignment(init_byte_size, HeapRegion::GrainBytes, "g1 heap");
  Universe::check_alignment(max_byte_size, HeapRegion::GrainBytes, "g1 heap");

  // We allocate this in any case, but only do no work if the command line
  // param is off.
  _cg1r = new ConcurrentG1Refine();

  // Reserve the maximum.
  PermanentGenerationSpec* pgs = collector_policy()->permanent_generation();
  // Includes the perm-gen.
  ReservedSpace heap_rs(max_byte_size + pgs->max_size(),
                        HeapRegion::GrainBytes,
                        false /*ism*/);

  if (!heap_rs.is_reserved()) {
    vm_exit_during_initialization("Could not reserve enough space for object heap");
    return JNI_ENOMEM;
  }

  // It is important to do this in a way such that concurrent readers can't
  // temporarily think somethings in the heap.  (I've actually seen this
  // happen in asserts: DLD.)
  _reserved.set_word_size(0);
  _reserved.set_start((HeapWord*)heap_rs.base());
  _reserved.set_end((HeapWord*)(heap_rs.base() + heap_rs.size()));

  _expansion_regions = max_byte_size/HeapRegion::GrainBytes;

  _num_humongous_regions = 0;

  // Create the gen rem set (and barrier set) for the entire reserved region.
  _rem_set = collector_policy()->create_rem_set(_reserved, 2);
  set_barrier_set(rem_set()->bs());
  if (barrier_set()->is_a(BarrierSet::ModRef)) {
    _mr_bs = (ModRefBarrierSet*)_barrier_set;
  } else {
    vm_exit_during_initialization("G1 requires a mod ref bs.");
    return JNI_ENOMEM;
  }

  // Also create a G1 rem set.
  if (G1UseHRIntoRS) {
    if (mr_bs()->is_a(BarrierSet::CardTableModRef)) {
      _g1_rem_set = new HRInto_G1RemSet(this, (CardTableModRefBS*)mr_bs());
    } else {
      vm_exit_during_initialization("G1 requires a cardtable mod ref bs.");
      return JNI_ENOMEM;
    }
  } else {
    _g1_rem_set = new StupidG1RemSet(this);
  }

  // Carve out the G1 part of the heap.

  ReservedSpace g1_rs   = heap_rs.first_part(max_byte_size);
  _g1_reserved = MemRegion((HeapWord*)g1_rs.base(),
                           g1_rs.size()/HeapWordSize);
  ReservedSpace perm_gen_rs = heap_rs.last_part(max_byte_size);

  _perm_gen = pgs->init(perm_gen_rs, pgs->init_size(), rem_set());

  _g1_storage.initialize(g1_rs, 0);
  _g1_committed = MemRegion((HeapWord*)_g1_storage.low(), (size_t) 0);
  _g1_max_committed = _g1_committed;
  _hrs = new HeapRegionSeq(_expansion_regions);
  guarantee(_hrs != NULL, "Couldn't allocate HeapRegionSeq");
  guarantee(_cur_alloc_region == NULL, "from constructor");

  _bot_shared = new G1BlockOffsetSharedArray(_reserved,
                                             heap_word_size(init_byte_size));

  _g1h = this;

  // Create the ConcurrentMark data structure and thread.
  // (Must do this late, so that "max_regions" is defined.)
  _cm       = new ConcurrentMark(heap_rs, (int) max_regions());
  _cmThread = _cm->cmThread();

  // ...and the concurrent zero-fill thread, if necessary.
  if (G1ConcZeroFill) {
    _czft = new ConcurrentZFThread();
  }



  // Allocate the popular regions; take them off free lists.
  size_t pop_byte_size = G1NumPopularRegions * HeapRegion::GrainBytes;
  expand(pop_byte_size);
  _popular_object_boundary =
    _g1_reserved.start() + (G1NumPopularRegions * HeapRegion::GrainWords);
  for (int i = 0; i < G1NumPopularRegions; i++) {
    HeapRegion* hr = newAllocRegion(HeapRegion::GrainWords);
    //    assert(hr != NULL && hr->bottom() < _popular_object_boundary,
    //     "Should be enough, and all should be below boundary.");
    hr->set_popular(true);
  }
  assert(_cur_pop_hr_index == 0, "Start allocating at the first region.");

  // Initialize the from_card cache structure of HeapRegionRemSet.
  HeapRegionRemSet::init_heap(max_regions());

  // Now expand into the rest of the initial heap size.
  expand(init_byte_size - pop_byte_size);

  // Perform any initialization actions delegated to the policy.
  g1_policy()->init();

  g1_policy()->note_start_of_mark_thread();

  _refine_cte_cl =
    new RefineCardTableEntryClosure(ConcurrentG1RefineThread::sts(),
                                    g1_rem_set(),
                                    concurrent_g1_refine());
  JavaThread::dirty_card_queue_set().set_closure(_refine_cte_cl);

  JavaThread::satb_mark_queue_set().initialize(SATB_Q_CBL_mon,
                                               SATB_Q_FL_lock,
                                               0,
                                               Shared_SATB_Q_lock);
  if (G1RSBarrierUseQueue) {
    JavaThread::dirty_card_queue_set().initialize(DirtyCardQ_CBL_mon,
                                                  DirtyCardQ_FL_lock,
                                                  G1DirtyCardQueueMax,
                                                  Shared_DirtyCardQ_lock);
  }
  // In case we're keeping closure specialization stats, initialize those
  // counts and that mechanism.
  SpecializationStats::clear();

  _gc_alloc_region_list = NULL;

  // Do later initialization work for concurrent refinement.
  _cg1r->init();

  const char* group_names[] = { "CR", "ZF", "CM", "CL" };
  GCOverheadReporter::initGCOverheadReporter(4, group_names);

  return JNI_OK;
}

void G1CollectedHeap::ref_processing_init() {
  SharedHeap::ref_processing_init();
  MemRegion mr = reserved_region();
  _ref_processor = ReferenceProcessor::create_ref_processor(
                                         mr,    // span
                                         false, // Reference discovery is not atomic
                                                // (though it shouldn't matter here.)
                                         true,  // mt_discovery
                                         NULL,  // is alive closure: need to fill this in for efficiency
                                         ParallelGCThreads,
                                         ParallelRefProcEnabled,
                                         true); // Setting next fields of discovered
                                                // lists requires a barrier.
}

size_t G1CollectedHeap::capacity() const {
  return _g1_committed.byte_size();
}

void G1CollectedHeap::iterate_dirty_card_closure(bool concurrent,
                                                 int worker_i) {
  DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
  int n_completed_buffers = 0;
  while (dcqs.apply_closure_to_completed_buffer(worker_i, 0, true)) {
    n_completed_buffers++;
  }
  g1_policy()->record_update_rs_processed_buffers(worker_i,
                                                  (double) n_completed_buffers);
  dcqs.clear_n_completed_buffers();
  // Finish up the queue...
  if (worker_i == 0) concurrent_g1_refine()->clean_up_cache(worker_i,
                                                            g1_rem_set());
  assert(!dcqs.completed_buffers_exist_dirty(), "Completed buffers exist!");
}


// Computes the sum of the storage used by the various regions.

size_t G1CollectedHeap::used() const {
  assert(Heap_lock->owner() != NULL,
         "Should be owned on this thread's behalf.");
  size_t result = _summary_bytes_used;
  if (_cur_alloc_region != NULL)
    result += _cur_alloc_region->used();
  return result;
}

class SumUsedClosure: public HeapRegionClosure {
  size_t _used;
public:
  SumUsedClosure() : _used(0) {}
  bool doHeapRegion(HeapRegion* r) {
    if (!r->continuesHumongous()) {
      _used += r->used();
    }
    return false;
  }
  size_t result() { return _used; }
};

size_t G1CollectedHeap::recalculate_used() const {
  SumUsedClosure blk;
  _hrs->iterate(&blk);
  return blk.result();
}

#ifndef PRODUCT
class SumUsedRegionsClosure: public HeapRegionClosure {
  size_t _num;
public:
  // _num is set to 1 to account for the popular region
  SumUsedRegionsClosure() : _num(G1NumPopularRegions) {}
  bool doHeapRegion(HeapRegion* r) {
    if (r->continuesHumongous() || r->used() > 0 || r->is_gc_alloc_region()) {
      _num += 1;
    }
    return false;
  }
  size_t result() { return _num; }
};

size_t G1CollectedHeap::recalculate_used_regions() const {
  SumUsedRegionsClosure blk;
  _hrs->iterate(&blk);
  return blk.result();
}
#endif // PRODUCT

size_t G1CollectedHeap::unsafe_max_alloc() {
  if (_free_regions > 0) return HeapRegion::GrainBytes;
  // otherwise, is there space in the current allocation region?

  // We need to store the current allocation region in a local variable
  // here. The problem is that this method doesn't take any locks and
  // there may be other threads which overwrite the current allocation
  // region field. attempt_allocation(), for example, sets it to NULL
  // and this can happen *after* the NULL check here but before the call
  // to free(), resulting in a SIGSEGV. Note that this doesn't appear
  // to be a problem in the optimized build, since the two loads of the
  // current allocation region field are optimized away.
  HeapRegion* car = _cur_alloc_region;

  // FIXME: should iterate over all regions?
  if (car == NULL) {
    return 0;
  }
  return car->free();
}

void G1CollectedHeap::collect(GCCause::Cause cause) {
  // The caller doesn't have the Heap_lock
  assert(!Heap_lock->owned_by_self(), "this thread should not own the Heap_lock");
  MutexLocker ml(Heap_lock);
  collect_locked(cause);
}

void G1CollectedHeap::collect_as_vm_thread(GCCause::Cause cause) {
  assert(Thread::current()->is_VM_thread(), "Precondition#1");
  assert(Heap_lock->is_locked(), "Precondition#2");
  GCCauseSetter gcs(this, cause);
  switch (cause) {
    case GCCause::_heap_inspection:
    case GCCause::_heap_dump: {
      HandleMark hm;
      do_full_collection(false);         // don't clear all soft refs
      break;
    }
    default: // XXX FIX ME
      ShouldNotReachHere(); // Unexpected use of this function
  }
}


void G1CollectedHeap::collect_locked(GCCause::Cause cause) {
  // Don't want to do a GC until cleanup is completed.
  wait_for_cleanup_complete();

  // Read the GC count while holding the Heap_lock
  int gc_count_before = SharedHeap::heap()->total_collections();
  {
    MutexUnlocker mu(Heap_lock);  // give up heap lock, execute gets it back
    VM_G1CollectFull op(gc_count_before, cause);
    VMThread::execute(&op);
  }
}

bool G1CollectedHeap::is_in(const void* p) const {
  if (_g1_committed.contains(p)) {
    HeapRegion* hr = _hrs->addr_to_region(p);
    return hr->is_in(p);
  } else {
    return _perm_gen->as_gen()->is_in(p);
  }
}

// Iteration functions.

// Iterates an OopClosure over all ref-containing fields of objects
// within a HeapRegion.

class IterateOopClosureRegionClosure: public HeapRegionClosure {
  MemRegion _mr;
  OopClosure* _cl;
public:
  IterateOopClosureRegionClosure(MemRegion mr, OopClosure* cl)
    : _mr(mr), _cl(cl) {}
  bool doHeapRegion(HeapRegion* r) {
    if (! r->continuesHumongous()) {
      r->oop_iterate(_cl);
    }
    return false;
  }
};

void G1CollectedHeap::oop_iterate(OopClosure* cl) {
  IterateOopClosureRegionClosure blk(_g1_committed, cl);
  _hrs->iterate(&blk);
}

void G1CollectedHeap::oop_iterate(MemRegion mr, OopClosure* cl) {
  IterateOopClosureRegionClosure blk(mr, cl);
  _hrs->iterate(&blk);
}

// Iterates an ObjectClosure over all objects within a HeapRegion.

class IterateObjectClosureRegionClosure: public HeapRegionClosure {
  ObjectClosure* _cl;
public:
  IterateObjectClosureRegionClosure(ObjectClosure* cl) : _cl(cl) {}
  bool doHeapRegion(HeapRegion* r) {
    if (! r->continuesHumongous()) {
      r->object_iterate(_cl);
    }
    return false;
  }
};

void G1CollectedHeap::object_iterate(ObjectClosure* cl) {
  IterateObjectClosureRegionClosure blk(cl);
  _hrs->iterate(&blk);
}

void G1CollectedHeap::object_iterate_since_last_GC(ObjectClosure* cl) {
  // FIXME: is this right?
  guarantee(false, "object_iterate_since_last_GC not supported by G1 heap");
}

// Calls a SpaceClosure on a HeapRegion.

class SpaceClosureRegionClosure: public HeapRegionClosure {
  SpaceClosure* _cl;
public:
  SpaceClosureRegionClosure(SpaceClosure* cl) : _cl(cl) {}
  bool doHeapRegion(HeapRegion* r) {
    _cl->do_space(r);
    return false;
  }
};

void G1CollectedHeap::space_iterate(SpaceClosure* cl) {
  SpaceClosureRegionClosure blk(cl);
  _hrs->iterate(&blk);
}

void G1CollectedHeap::heap_region_iterate(HeapRegionClosure* cl) {
  _hrs->iterate(cl);
}

void G1CollectedHeap::heap_region_iterate_from(HeapRegion* r,
                                               HeapRegionClosure* cl) {
  _hrs->iterate_from(r, cl);
}

void
G1CollectedHeap::heap_region_iterate_from(int idx, HeapRegionClosure* cl) {
  _hrs->iterate_from(idx, cl);
}

HeapRegion* G1CollectedHeap::region_at(size_t idx) { return _hrs->at(idx); }

void
G1CollectedHeap::heap_region_par_iterate_chunked(HeapRegionClosure* cl,
                                                 int worker,
                                                 jint claim_value) {
  const size_t regions = n_regions();
  const size_t worker_num = (ParallelGCThreads > 0 ? ParallelGCThreads : 1);
  // try to spread out the starting points of the workers
  const size_t start_index = regions / worker_num * (size_t) worker;

  // each worker will actually look at all regions
  for (size_t count = 0; count < regions; ++count) {
    const size_t index = (start_index + count) % regions;
    assert(0 <= index && index < regions, "sanity");
    HeapRegion* r = region_at(index);
    // we'll ignore "continues humongous" regions (we'll process them
    // when we come across their corresponding "start humongous"
    // region) and regions already claimed
    if (r->claim_value() == claim_value || r->continuesHumongous()) {
      continue;
    }
    // OK, try to claim it
    if (r->claimHeapRegion(claim_value)) {
      // success!
      assert(!r->continuesHumongous(), "sanity");
      if (r->startsHumongous()) {
        // If the region is "starts humongous" we'll iterate over its
        // "continues humongous" first; in fact we'll do them
        // first. The order is important. In on case, calling the
        // closure on the "starts humongous" region might de-allocate
        // and clear all its "continues humongous" regions and, as a
        // result, we might end up processing them twice. So, we'll do
        // them first (notice: most closures will ignore them anyway) and
        // then we'll do the "starts humongous" region.
        for (size_t ch_index = index + 1; ch_index < regions; ++ch_index) {
          HeapRegion* chr = region_at(ch_index);

          // if the region has already been claimed or it's not
          // "continues humongous" we're done
          if (chr->claim_value() == claim_value ||
              !chr->continuesHumongous()) {
            break;
          }

          // Noone should have claimed it directly. We can given
          // that we claimed its "starts humongous" region.
          assert(chr->claim_value() != claim_value, "sanity");
          assert(chr->humongous_start_region() == r, "sanity");

          if (chr->claimHeapRegion(claim_value)) {
            // we should always be able to claim it; noone else should
            // be trying to claim this region

            bool res2 = cl->doHeapRegion(chr);
            assert(!res2, "Should not abort");

            // Right now, this holds (i.e., no closure that actually
            // does something with "continues humongous" regions
            // clears them). We might have to weaken it in the future,
            // but let's leave these two asserts here for extra safety.
            assert(chr->continuesHumongous(), "should still be the case");
            assert(chr->humongous_start_region() == r, "sanity");
          } else {
            guarantee(false, "we should not reach here");
          }
        }
      }

      assert(!r->continuesHumongous(), "sanity");
      bool res = cl->doHeapRegion(r);
      assert(!res, "Should not abort");
    }
  }
}

class ResetClaimValuesClosure: public HeapRegionClosure {
public:
  bool doHeapRegion(HeapRegion* r) {
    r->set_claim_value(HeapRegion::InitialClaimValue);
    return false;
  }
};

void
G1CollectedHeap::reset_heap_region_claim_values() {
  ResetClaimValuesClosure blk;
  heap_region_iterate(&blk);
}

#ifdef ASSERT
// This checks whether all regions in the heap have the correct claim
// value. I also piggy-backed on this a check to ensure that the
// humongous_start_region() information on "continues humongous"
// regions is correct.

class CheckClaimValuesClosure : public HeapRegionClosure {
private:
  jint _claim_value;
  size_t _failures;
  HeapRegion* _sh_region;
public:
  CheckClaimValuesClosure(jint claim_value) :
    _claim_value(claim_value), _failures(0), _sh_region(NULL) { }
  bool doHeapRegion(HeapRegion* r) {
    if (r->claim_value() != _claim_value) {
      gclog_or_tty->print_cr("Region ["PTR_FORMAT","PTR_FORMAT"), "
                             "claim value = %d, should be %d",
                             r->bottom(), r->end(), r->claim_value(),
                             _claim_value);
      ++_failures;
    }
    if (!r->isHumongous()) {
      _sh_region = NULL;
    } else if (r->startsHumongous()) {
      _sh_region = r;
    } else if (r->continuesHumongous()) {
      if (r->humongous_start_region() != _sh_region) {
        gclog_or_tty->print_cr("Region ["PTR_FORMAT","PTR_FORMAT"), "
                               "HS = "PTR_FORMAT", should be "PTR_FORMAT,
                               r->bottom(), r->end(),
                               r->humongous_start_region(),
                               _sh_region);
        ++_failures;
      }
    }
    return false;
  }
  size_t failures() {
    return _failures;
  }
};

bool G1CollectedHeap::check_heap_region_claim_values(jint claim_value) {
  CheckClaimValuesClosure cl(claim_value);
  heap_region_iterate(&cl);
  return cl.failures() == 0;
}
#endif // ASSERT

void G1CollectedHeap::collection_set_iterate(HeapRegionClosure* cl) {
  HeapRegion* r = g1_policy()->collection_set();
  while (r != NULL) {
    HeapRegion* next = r->next_in_collection_set();
    if (cl->doHeapRegion(r)) {
      cl->incomplete();
      return;
    }
    r = next;
  }
}

void G1CollectedHeap::collection_set_iterate_from(HeapRegion* r,
                                                  HeapRegionClosure *cl) {
  assert(r->in_collection_set(),
         "Start region must be a member of the collection set.");
  HeapRegion* cur = r;
  while (cur != NULL) {
    HeapRegion* next = cur->next_in_collection_set();
    if (cl->doHeapRegion(cur) && false) {
      cl->incomplete();
      return;
    }
    cur = next;
  }
  cur = g1_policy()->collection_set();
  while (cur != r) {
    HeapRegion* next = cur->next_in_collection_set();
    if (cl->doHeapRegion(cur) && false) {
      cl->incomplete();
      return;
    }
    cur = next;
  }
}

CompactibleSpace* G1CollectedHeap::first_compactible_space() {
  return _hrs->length() > 0 ? _hrs->at(0) : NULL;
}


Space* G1CollectedHeap::space_containing(const void* addr) const {
  Space* res = heap_region_containing(addr);
  if (res == NULL)
    res = perm_gen()->space_containing(addr);
  return res;
}

HeapWord* G1CollectedHeap::block_start(const void* addr) const {
  Space* sp = space_containing(addr);
  if (sp != NULL) {
    return sp->block_start(addr);
  }
  return NULL;
}

size_t G1CollectedHeap::block_size(const HeapWord* addr) const {
  Space* sp = space_containing(addr);
  assert(sp != NULL, "block_size of address outside of heap");
  return sp->block_size(addr);
}

bool G1CollectedHeap::block_is_obj(const HeapWord* addr) const {
  Space* sp = space_containing(addr);
  return sp->block_is_obj(addr);
}

bool G1CollectedHeap::supports_tlab_allocation() const {
  return true;
}

size_t G1CollectedHeap::tlab_capacity(Thread* ignored) const {
  return HeapRegion::GrainBytes;
}

size_t G1CollectedHeap::unsafe_max_tlab_alloc(Thread* ignored) const {
  // Return the remaining space in the cur alloc region, but not less than
  // the min TLAB size.
  // Also, no more than half the region size, since we can't allow tlabs to
  // grow big enough to accomodate humongous objects.

  // We need to story it locally, since it might change between when we
  // test for NULL and when we use it later.
  ContiguousSpace* cur_alloc_space = _cur_alloc_region;
  if (cur_alloc_space == NULL) {
    return HeapRegion::GrainBytes/2;
  } else {
    return MAX2(MIN2(cur_alloc_space->free(),
                     (size_t)(HeapRegion::GrainBytes/2)),
                (size_t)MinTLABSize);
  }
}

HeapWord* G1CollectedHeap::allocate_new_tlab(size_t size) {
  bool dummy;
  return G1CollectedHeap::mem_allocate(size, false, true, &dummy);
}

bool G1CollectedHeap::allocs_are_zero_filled() {
  return false;
}

size_t G1CollectedHeap::large_typearray_limit() {
  // FIXME
  return HeapRegion::GrainBytes/HeapWordSize;
}

size_t G1CollectedHeap::max_capacity() const {
  return _g1_committed.byte_size();
}

jlong G1CollectedHeap::millis_since_last_gc() {
  // assert(false, "NYI");
  return 0;
}


void G1CollectedHeap::prepare_for_verify() {
  if (SafepointSynchronize::is_at_safepoint() || ! UseTLAB) {
    ensure_parsability(false);
  }
  g1_rem_set()->prepare_for_verify();
}

class VerifyLivenessOopClosure: public OopClosure {
  G1CollectedHeap* g1h;
public:
  VerifyLivenessOopClosure(G1CollectedHeap* _g1h) {
    g1h = _g1h;
  }
  void do_oop(narrowOop *p) {
    guarantee(false, "NYI");
  }
  void do_oop(oop *p) {
    oop obj = *p;
    assert(obj == NULL || !g1h->is_obj_dead(obj),
           "Dead object referenced by a not dead object");
  }
};

class VerifyObjsInRegionClosure: public ObjectClosure {
  G1CollectedHeap* _g1h;
  size_t _live_bytes;
  HeapRegion *_hr;
public:
  VerifyObjsInRegionClosure(HeapRegion *hr) : _live_bytes(0), _hr(hr) {
    _g1h = G1CollectedHeap::heap();
  }
  void do_object(oop o) {
    VerifyLivenessOopClosure isLive(_g1h);
    assert(o != NULL, "Huh?");
    if (!_g1h->is_obj_dead(o)) {
      o->oop_iterate(&isLive);
      if (!_hr->obj_allocated_since_prev_marking(o))
        _live_bytes += (o->size() * HeapWordSize);
    }
  }
  size_t live_bytes() { return _live_bytes; }
};

class PrintObjsInRegionClosure : public ObjectClosure {
  HeapRegion *_hr;
  G1CollectedHeap *_g1;
public:
  PrintObjsInRegionClosure(HeapRegion *hr) : _hr(hr) {
    _g1 = G1CollectedHeap::heap();
  };

  void do_object(oop o) {
    if (o != NULL) {
      HeapWord *start = (HeapWord *) o;
      size_t word_sz = o->size();
      gclog_or_tty->print("\nPrinting obj "PTR_FORMAT" of size " SIZE_FORMAT
                          " isMarkedPrev %d isMarkedNext %d isAllocSince %d\n",
                          (void*) o, word_sz,
                          _g1->isMarkedPrev(o),
                          _g1->isMarkedNext(o),
                          _hr->obj_allocated_since_prev_marking(o));
      HeapWord *end = start + word_sz;
      HeapWord *cur;
      int *val;
      for (cur = start; cur < end; cur++) {
        val = (int *) cur;
        gclog_or_tty->print("\t "PTR_FORMAT":"PTR_FORMAT"\n", val, *val);
      }
    }
  }
};

class VerifyRegionClosure: public HeapRegionClosure {
public:
  bool _allow_dirty;
  bool _par;
  VerifyRegionClosure(bool allow_dirty, bool par = false)
    : _allow_dirty(allow_dirty), _par(par) {}
  bool doHeapRegion(HeapRegion* r) {
    guarantee(_par || r->claim_value() == HeapRegion::InitialClaimValue,
              "Should be unclaimed at verify points.");
    if (r->isHumongous()) {
      if (r->startsHumongous()) {
        // Verify the single H object.
        oop(r->bottom())->verify();
        size_t word_sz = oop(r->bottom())->size();
        guarantee(r->top() == r->bottom() + word_sz,
                  "Only one object in a humongous region");
      }
    } else {
      VerifyObjsInRegionClosure not_dead_yet_cl(r);
      r->verify(_allow_dirty);
      r->object_iterate(&not_dead_yet_cl);
      guarantee(r->max_live_bytes() >= not_dead_yet_cl.live_bytes(),
                "More live objects than counted in last complete marking.");
    }
    return false;
  }
};

class VerifyRootsClosure: public OopsInGenClosure {
private:
  G1CollectedHeap* _g1h;
  bool             _failures;

public:
  VerifyRootsClosure() :
    _g1h(G1CollectedHeap::heap()), _failures(false) { }

  bool failures() { return _failures; }

  void do_oop(narrowOop* p) {
    guarantee(false, "NYI");
  }

  void do_oop(oop* p) {
    oop obj = *p;
    if (obj != NULL) {
      if (_g1h->is_obj_dead(obj)) {
        gclog_or_tty->print_cr("Root location "PTR_FORMAT" "
                               "points to dead obj "PTR_FORMAT, p, (void*) obj);
        obj->print_on(gclog_or_tty);
        _failures = true;
      }
    }
  }
};

// This is the task used for parallel heap verification.

class G1ParVerifyTask: public AbstractGangTask {
private:
  G1CollectedHeap* _g1h;
  bool _allow_dirty;

public:
  G1ParVerifyTask(G1CollectedHeap* g1h, bool allow_dirty) :
    AbstractGangTask("Parallel verify task"),
    _g1h(g1h), _allow_dirty(allow_dirty) { }

  void work(int worker_i) {
    VerifyRegionClosure blk(_allow_dirty, true);
    _g1h->heap_region_par_iterate_chunked(&blk, worker_i,
                                          HeapRegion::ParVerifyClaimValue);
  }
};

void G1CollectedHeap::verify(bool allow_dirty, bool silent) {
  if (SafepointSynchronize::is_at_safepoint() || ! UseTLAB) {
    if (!silent) { gclog_or_tty->print("roots "); }
    VerifyRootsClosure rootsCl;
    process_strong_roots(false,
                         SharedHeap::SO_AllClasses,
                         &rootsCl,
                         &rootsCl);
    rem_set()->invalidate(perm_gen()->used_region(), false);
    if (!silent) { gclog_or_tty->print("heapRegions "); }
    if (GCParallelVerificationEnabled && ParallelGCThreads > 1) {
      assert(check_heap_region_claim_values(HeapRegion::InitialClaimValue),
             "sanity check");

      G1ParVerifyTask task(this, allow_dirty);
      int n_workers = workers()->total_workers();
      set_par_threads(n_workers);
      workers()->run_task(&task);
      set_par_threads(0);

      assert(check_heap_region_claim_values(HeapRegion::ParVerifyClaimValue),
             "sanity check");

      reset_heap_region_claim_values();

      assert(check_heap_region_claim_values(HeapRegion::InitialClaimValue),
             "sanity check");
    } else {
      VerifyRegionClosure blk(allow_dirty);
      _hrs->iterate(&blk);
    }
    if (!silent) gclog_or_tty->print("remset ");
    rem_set()->verify();
    guarantee(!rootsCl.failures(), "should not have had failures");
  } else {
    if (!silent) gclog_or_tty->print("(SKIPPING roots, heapRegions, remset) ");
  }
}

class PrintRegionClosure: public HeapRegionClosure {
  outputStream* _st;
public:
  PrintRegionClosure(outputStream* st) : _st(st) {}
  bool doHeapRegion(HeapRegion* r) {
    r->print_on(_st);
    return false;
  }
};

void G1CollectedHeap::print() const { print_on(gclog_or_tty); }

void G1CollectedHeap::print_on(outputStream* st) const {
  PrintRegionClosure blk(st);
  _hrs->iterate(&blk);
}

void G1CollectedHeap::print_gc_threads_on(outputStream* st) const {
  if (ParallelGCThreads > 0) {
    workers()->print_worker_threads();
  }
  st->print("\"G1 concurrent mark GC Thread\" ");
  _cmThread->print();
  st->cr();
  st->print("\"G1 concurrent refinement GC Thread\" ");
  _cg1r->cg1rThread()->print_on(st);
  st->cr();
  st->print("\"G1 zero-fill GC Thread\" ");
  _czft->print_on(st);
  st->cr();
}

void G1CollectedHeap::gc_threads_do(ThreadClosure* tc) const {
  if (ParallelGCThreads > 0) {
    workers()->threads_do(tc);
  }
  tc->do_thread(_cmThread);
  tc->do_thread(_cg1r->cg1rThread());
  tc->do_thread(_czft);
}

void G1CollectedHeap::print_tracing_info() const {
  concurrent_g1_refine()->print_final_card_counts();

  // We'll overload this to mean "trace GC pause statistics."
  if (TraceGen0Time || TraceGen1Time) {
    // The "G1CollectorPolicy" is keeping track of these stats, so delegate
    // to that.
    g1_policy()->print_tracing_info();
  }
  if (SummarizeG1RSStats) {
    g1_rem_set()->print_summary_info();
  }
  if (SummarizeG1ConcMark) {
    concurrent_mark()->print_summary_info();
  }
  if (SummarizeG1ZFStats) {
    ConcurrentZFThread::print_summary_info();
  }
  if (G1SummarizePopularity) {
    print_popularity_summary_info();
  }
  g1_policy()->print_yg_surv_rate_info();

  GCOverheadReporter::printGCOverhead();

  SpecializationStats::print();
}


int G1CollectedHeap::addr_to_arena_id(void* addr) const {
  HeapRegion* hr = heap_region_containing(addr);
  if (hr == NULL) {
    return 0;
  } else {
    return 1;
  }
}

G1CollectedHeap* G1CollectedHeap::heap() {
  assert(_sh->kind() == CollectedHeap::G1CollectedHeap,
         "not a garbage-first heap");
  return _g1h;
}

void G1CollectedHeap::gc_prologue(bool full /* Ignored */) {
  if (PrintHeapAtGC){
    gclog_or_tty->print_cr(" {Heap before GC collections=%d:", total_collections());
    Universe::print();
  }
  assert(InlineCacheBuffer::is_empty(), "should have cleaned up ICBuffer");
  // Call allocation profiler
  AllocationProfiler::iterate_since_last_gc();
  // Fill TLAB's and such
  ensure_parsability(true);
}

void G1CollectedHeap::gc_epilogue(bool full /* Ignored */) {
  // FIXME: what is this about?
  // I'm ignoring the "fill_newgen()" call if "alloc_event_enabled"
  // is set.
  COMPILER2_PRESENT(assert(DerivedPointerTable::is_empty(),
                        "derived pointer present"));

  if (PrintHeapAtGC){
    gclog_or_tty->print_cr(" Heap after GC collections=%d:", total_collections());
    Universe::print();
    gclog_or_tty->print("} ");
  }
}

void G1CollectedHeap::do_collection_pause() {
  // Read the GC count while holding the Heap_lock
  // we need to do this _before_ wait_for_cleanup_complete(), to
  // ensure that we do not give up the heap lock and potentially
  // pick up the wrong count
  int gc_count_before = SharedHeap::heap()->total_collections();

  // Don't want to do a GC pause while cleanup is being completed!
  wait_for_cleanup_complete();

  g1_policy()->record_stop_world_start();
  {
    MutexUnlocker mu(Heap_lock);  // give up heap lock, execute gets it back
    VM_G1IncCollectionPause op(gc_count_before);
    VMThread::execute(&op);
  }
}

void
G1CollectedHeap::doConcurrentMark() {
  if (G1ConcMark) {
    MutexLockerEx x(CGC_lock, Mutex::_no_safepoint_check_flag);
    if (!_cmThread->in_progress()) {
      _cmThread->set_started();
      CGC_lock->notify();
    }
  }
}

class VerifyMarkedObjsClosure: public ObjectClosure {
    G1CollectedHeap* _g1h;
    public:
    VerifyMarkedObjsClosure(G1CollectedHeap* g1h) : _g1h(g1h) {}
    void do_object(oop obj) {
      assert(obj->mark()->is_marked() ? !_g1h->is_obj_dead(obj) : true,
             "markandsweep mark should agree with concurrent deadness");
    }
};

void
G1CollectedHeap::checkConcurrentMark() {
    VerifyMarkedObjsClosure verifycl(this);
    doConcurrentMark();
    //    MutexLockerEx x(getMarkBitMapLock(),
    //              Mutex::_no_safepoint_check_flag);
    object_iterate(&verifycl);
}

void G1CollectedHeap::do_sync_mark() {
  _cm->checkpointRootsInitial();
  _cm->markFromRoots();
  _cm->checkpointRootsFinal(false);
}

// <NEW PREDICTION>

double G1CollectedHeap::predict_region_elapsed_time_ms(HeapRegion *hr,
                                                       bool young) {
  return _g1_policy->predict_region_elapsed_time_ms(hr, young);
}

void G1CollectedHeap::check_if_region_is_too_expensive(double
                                                           predicted_time_ms) {
  _g1_policy->check_if_region_is_too_expensive(predicted_time_ms);
}

size_t G1CollectedHeap::pending_card_num() {
  size_t extra_cards = 0;
  JavaThread *curr = Threads::first();
  while (curr != NULL) {
    DirtyCardQueue& dcq = curr->dirty_card_queue();
    extra_cards += dcq.size();
    curr = curr->next();
  }
  DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
  size_t buffer_size = dcqs.buffer_size();
  size_t buffer_num = dcqs.completed_buffers_num();
  return buffer_size * buffer_num + extra_cards;
}

size_t G1CollectedHeap::max_pending_card_num() {
  DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
  size_t buffer_size = dcqs.buffer_size();
  size_t buffer_num  = dcqs.completed_buffers_num();
  int thread_num  = Threads::number_of_threads();
  return (buffer_num + thread_num) * buffer_size;
}

size_t G1CollectedHeap::cards_scanned() {
  HRInto_G1RemSet* g1_rset = (HRInto_G1RemSet*) g1_rem_set();
  return g1_rset->cardsScanned();
}

void
G1CollectedHeap::setup_surviving_young_words() {
  guarantee( _surviving_young_words == NULL, "pre-condition" );
  size_t array_length = g1_policy()->young_cset_length();
  _surviving_young_words = NEW_C_HEAP_ARRAY(size_t, array_length);
  if (_surviving_young_words == NULL) {
    vm_exit_out_of_memory(sizeof(size_t) * array_length,
                          "Not enough space for young surv words summary.");
  }
  memset(_surviving_young_words, 0, array_length * sizeof(size_t));
  for (size_t i = 0;  i < array_length; ++i) {
    guarantee( _surviving_young_words[i] == 0, "invariant" );
  }
}

void
G1CollectedHeap::update_surviving_young_words(size_t* surv_young_words) {
  MutexLockerEx x(ParGCRareEvent_lock, Mutex::_no_safepoint_check_flag);
  size_t array_length = g1_policy()->young_cset_length();
  for (size_t i = 0; i < array_length; ++i)
    _surviving_young_words[i] += surv_young_words[i];
}

void
G1CollectedHeap::cleanup_surviving_young_words() {
  guarantee( _surviving_young_words != NULL, "pre-condition" );
  FREE_C_HEAP_ARRAY(size_t, _surviving_young_words);
  _surviving_young_words = NULL;
}

// </NEW PREDICTION>

void
G1CollectedHeap::do_collection_pause_at_safepoint(HeapRegion* popular_region) {
  char verbose_str[128];
  sprintf(verbose_str, "GC pause ");
  if (popular_region != NULL)
    strcat(verbose_str, "(popular)");
  else if (g1_policy()->in_young_gc_mode()) {
    if (g1_policy()->full_young_gcs())
      strcat(verbose_str, "(young)");
    else
      strcat(verbose_str, "(partial)");
  }
  bool reset_should_initiate_conc_mark = false;
  if (popular_region != NULL && g1_policy()->should_initiate_conc_mark()) {
    // we currently do not allow an initial mark phase to be piggy-backed
    // on a popular pause
    reset_should_initiate_conc_mark = true;
    g1_policy()->unset_should_initiate_conc_mark();
  }
  if (g1_policy()->should_initiate_conc_mark())
    strcat(verbose_str, " (initial-mark)");

  GCCauseSetter x(this, (popular_region == NULL ?
                         GCCause::_g1_inc_collection_pause :
                         GCCause::_g1_pop_region_collection_pause));

  // if PrintGCDetails is on, we'll print long statistics information
  // in the collector policy code, so let's not print this as the output
  // is messy if we do.
  gclog_or_tty->date_stamp(PrintGC && PrintGCDateStamps);
  TraceCPUTime tcpu(PrintGCDetails, true, gclog_or_tty);
  TraceTime t(verbose_str, PrintGC && !PrintGCDetails, true, gclog_or_tty);

  ResourceMark rm;
  assert(SafepointSynchronize::is_at_safepoint(), "should be at safepoint");
  assert(Thread::current() == VMThread::vm_thread(), "should be in vm thread");
  guarantee(!is_gc_active(), "collection is not reentrant");
  assert(regions_accounted_for(), "Region leakage!");

  increment_gc_time_stamp();

  if (g1_policy()->in_young_gc_mode()) {
    assert(check_young_list_well_formed(),
                "young list should be well formed");
  }

  if (GC_locker::is_active()) {
    return; // GC is disabled (e.g. JNI GetXXXCritical operation)
  }

  bool abandoned = false;
  { // Call to jvmpi::post_class_unload_events must occur outside of active GC
    IsGCActiveMark x;

    gc_prologue(false);
    increment_total_collections();

#if G1_REM_SET_LOGGING
    gclog_or_tty->print_cr("\nJust chose CS, heap:");
    print();
#endif

    if (VerifyBeforeGC && total_collections() >= VerifyGCStartAt) {
      HandleMark hm;  // Discard invalid handles created during verification
      prepare_for_verify();
      gclog_or_tty->print(" VerifyBeforeGC:");
      Universe::verify(false);
    }

    COMPILER2_PRESENT(DerivedPointerTable::clear());

    // We want to turn off ref discovere, if necessary, and turn it back on
    // on again later if we do.
    bool was_enabled = ref_processor()->discovery_enabled();
    if (was_enabled) ref_processor()->disable_discovery();

    // Forget the current alloc region (we might even choose it to be part
    // of the collection set!).
    abandon_cur_alloc_region();

    // The elapsed time induced by the start time below deliberately elides
    // the possible verification above.
    double start_time_sec = os::elapsedTime();
    GCOverheadReporter::recordSTWStart(start_time_sec);
    size_t start_used_bytes = used();
    if (!G1ConcMark) {
      do_sync_mark();
    }

    g1_policy()->record_collection_pause_start(start_time_sec,
                                               start_used_bytes);

#if SCAN_ONLY_VERBOSE
    _young_list->print();
#endif // SCAN_ONLY_VERBOSE

    if (g1_policy()->should_initiate_conc_mark()) {
      concurrent_mark()->checkpointRootsInitialPre();
    }
    save_marks();

    // We must do this before any possible evacuation that should propogate
    // marks, including evacuation of popular objects in a popular pause.
    if (mark_in_progress()) {
      double start_time_sec = os::elapsedTime();

      _cm->drainAllSATBBuffers();
      double finish_mark_ms = (os::elapsedTime() - start_time_sec) * 1000.0;
      g1_policy()->record_satb_drain_time(finish_mark_ms);

    }
    // Record the number of elements currently on the mark stack, so we
    // only iterate over these.  (Since evacuation may add to the mark
    // stack, doing more exposes race conditions.)  If no mark is in
    // progress, this will be zero.
    _cm->set_oops_do_bound();

    assert(regions_accounted_for(), "Region leakage.");

    bool abandoned = false;

    if (mark_in_progress())
      concurrent_mark()->newCSet();

    // Now choose the CS.
    if (popular_region == NULL) {
      g1_policy()->choose_collection_set();
    } else {
      // We may be evacuating a single region (for popularity).
      g1_policy()->record_popular_pause_preamble_start();
      popularity_pause_preamble(popular_region);
      g1_policy()->record_popular_pause_preamble_end();
      abandoned = (g1_policy()->collection_set() == NULL);
      // Now we allow more regions to be added (we have to collect
      // all popular regions).
      if (!abandoned) {
        g1_policy()->choose_collection_set(popular_region);
      }
    }
    // We may abandon a pause if we find no region that will fit in the MMU
    // pause.
    abandoned = (g1_policy()->collection_set() == NULL);

    // Nothing to do if we were unable to choose a collection set.
    if (!abandoned) {
#if G1_REM_SET_LOGGING
      gclog_or_tty->print_cr("\nAfter pause, heap:");
      print();
#endif

      setup_surviving_young_words();

      // Set up the gc allocation regions.
      get_gc_alloc_regions();

      // Actually do the work...
      evacuate_collection_set();
      free_collection_set(g1_policy()->collection_set());
      g1_policy()->clear_collection_set();

      if (popular_region != NULL) {
        // We have to wait until now, because we don't want the region to
        // be rescheduled for pop-evac during RS update.
        popular_region->set_popular_pending(false);
      }

      release_gc_alloc_regions();

      cleanup_surviving_young_words();

      if (g1_policy()->in_young_gc_mode()) {
        _young_list->reset_sampled_info();
        assert(check_young_list_empty(true),
               "young list should be empty");

#if SCAN_ONLY_VERBOSE
        _young_list->print();
#endif // SCAN_ONLY_VERBOSE

        _young_list->reset_auxilary_lists();
      }
    } else {
      COMPILER2_PRESENT(DerivedPointerTable::update_pointers());
    }

    if (evacuation_failed()) {
      _summary_bytes_used = recalculate_used();
    } else {
      // The "used" of the the collection set have already been subtracted
      // when they were freed.  Add in the bytes evacuated.
      _summary_bytes_used += g1_policy()->bytes_in_to_space();
    }

    if (g1_policy()->in_young_gc_mode() &&
        g1_policy()->should_initiate_conc_mark()) {
      concurrent_mark()->checkpointRootsInitialPost();
      set_marking_started();
      doConcurrentMark();
    }

#if SCAN_ONLY_VERBOSE
    _young_list->print();
#endif // SCAN_ONLY_VERBOSE

    double end_time_sec = os::elapsedTime();
    g1_policy()->record_pause_time((end_time_sec - start_time_sec)*1000.0);
    GCOverheadReporter::recordSTWEnd(end_time_sec);
    g1_policy()->record_collection_pause_end(popular_region != NULL,
                                             abandoned);

    assert(regions_accounted_for(), "Region leakage.");

    if (VerifyAfterGC && total_collections() >= VerifyGCStartAt) {
      HandleMark hm;  // Discard invalid handles created during verification
      gclog_or_tty->print(" VerifyAfterGC:");
      Universe::verify(false);
    }

    if (was_enabled) ref_processor()->enable_discovery();

    {
      size_t expand_bytes = g1_policy()->expansion_amount();
      if (expand_bytes > 0) {
        size_t bytes_before = capacity();
        expand(expand_bytes);
      }
    }

    if (mark_in_progress())
      concurrent_mark()->update_g1_committed();

    gc_epilogue(false);
  }

  assert(verify_region_lists(), "Bad region lists.");

  if (reset_should_initiate_conc_mark)
    g1_policy()->set_should_initiate_conc_mark();

  if (ExitAfterGCNum > 0 && total_collections() == ExitAfterGCNum) {
    gclog_or_tty->print_cr("Stopping after GC #%d", ExitAfterGCNum);
    print_tracing_info();
    vm_exit(-1);
  }
}

void G1CollectedHeap::set_gc_alloc_region(int purpose, HeapRegion* r) {
  assert(purpose >= 0 && purpose < GCAllocPurposeCount, "invalid purpose");
  HeapWord* original_top = NULL;
  if (r != NULL)
    original_top = r->top();

  // We will want to record the used space in r as being there before gc.
  // One we install it as a GC alloc region it's eligible for allocation.
  // So record it now and use it later.
  size_t r_used = 0;
  if (r != NULL) {
    r_used = r->used();

    if (ParallelGCThreads > 0) {
      // need to take the lock to guard against two threads calling
      // get_gc_alloc_region concurrently (very unlikely but...)
      MutexLockerEx x(ParGCRareEvent_lock, Mutex::_no_safepoint_check_flag);
      r->save_marks();
    }
  }
  HeapRegion* old_alloc_region = _gc_alloc_regions[purpose];
  _gc_alloc_regions[purpose] = r;
  if (old_alloc_region != NULL) {
    // Replace aliases too.
    for (int ap = 0; ap < GCAllocPurposeCount; ++ap) {
      if (_gc_alloc_regions[ap] == old_alloc_region) {
        _gc_alloc_regions[ap] = r;
      }
    }
  }
  if (r != NULL) {
    push_gc_alloc_region(r);
    if (mark_in_progress() && original_top != r->next_top_at_mark_start()) {
      // We are using a region as a GC alloc region after it has been used
      // as a mutator allocation region during the current marking cycle.
      // The mutator-allocated objects are currently implicitly marked, but
      // when we move hr->next_top_at_mark_start() forward at the the end
      // of the GC pause, they won't be.  We therefore mark all objects in
      // the "gap".  We do this object-by-object, since marking densely
      // does not currently work right with marking bitmap iteration.  This
      // means we rely on TLAB filling at the start of pauses, and no
      // "resuscitation" of filled TLAB's.  If we want to do this, we need
      // to fix the marking bitmap iteration.
      HeapWord* curhw = r->next_top_at_mark_start();
      HeapWord* t = original_top;

      while (curhw < t) {
        oop cur = (oop)curhw;
        // We'll assume parallel for generality.  This is rare code.
        concurrent_mark()->markAndGrayObjectIfNecessary(cur); // can't we just mark them?
        curhw = curhw + cur->size();
      }
      assert(curhw == t, "Should have parsed correctly.");
    }
    if (G1PolicyVerbose > 1) {
      gclog_or_tty->print("New alloc region ["PTR_FORMAT", "PTR_FORMAT", " PTR_FORMAT") "
                          "for survivors:", r->bottom(), original_top, r->end());
      r->print();
    }
    g1_policy()->record_before_bytes(r_used);
  }
}

void G1CollectedHeap::push_gc_alloc_region(HeapRegion* hr) {
  assert(Thread::current()->is_VM_thread() ||
         par_alloc_during_gc_lock()->owned_by_self(), "Precondition");
  assert(!hr->is_gc_alloc_region() && !hr->in_collection_set(),
         "Precondition.");
  hr->set_is_gc_alloc_region(true);
  hr->set_next_gc_alloc_region(_gc_alloc_region_list);
  _gc_alloc_region_list = hr;
}

#ifdef G1_DEBUG
class FindGCAllocRegion: public HeapRegionClosure {
public:
  bool doHeapRegion(HeapRegion* r) {
    if (r->is_gc_alloc_region()) {
      gclog_or_tty->print_cr("Region %d ["PTR_FORMAT"...] is still a gc_alloc_region.",
                             r->hrs_index(), r->bottom());
    }
    return false;
  }
};
#endif // G1_DEBUG

void G1CollectedHeap::forget_alloc_region_list() {
  assert(Thread::current()->is_VM_thread(), "Precondition");
  while (_gc_alloc_region_list != NULL) {
    HeapRegion* r = _gc_alloc_region_list;
    assert(r->is_gc_alloc_region(), "Invariant.");
    _gc_alloc_region_list = r->next_gc_alloc_region();
    r->set_next_gc_alloc_region(NULL);
    r->set_is_gc_alloc_region(false);
    if (r->is_empty()) {
      ++_free_regions;
    }
  }
#ifdef G1_DEBUG
  FindGCAllocRegion fa;
  heap_region_iterate(&fa);
#endif // G1_DEBUG
}


bool G1CollectedHeap::check_gc_alloc_regions() {
  // TODO: allocation regions check
  return true;
}

void G1CollectedHeap::get_gc_alloc_regions() {
  for (int ap = 0; ap < GCAllocPurposeCount; ++ap) {
    // Create new GC alloc regions.
    HeapRegion* alloc_region = _gc_alloc_regions[ap];
    // Clear this alloc region, so that in case it turns out to be
    // unacceptable, we end up with no allocation region, rather than a bad
    // one.
    _gc_alloc_regions[ap] = NULL;
    if (alloc_region == NULL || alloc_region->in_collection_set()) {
      // Can't re-use old one.  Allocate a new one.
      alloc_region = newAllocRegionWithExpansion(ap, 0);
    }
    if (alloc_region != NULL) {
      set_gc_alloc_region(ap, alloc_region);
    }
  }
  // Set alternative regions for allocation purposes that have reached
  // thier limit.
  for (int ap = 0; ap < GCAllocPurposeCount; ++ap) {
    GCAllocPurpose alt_purpose = g1_policy()->alternative_purpose(ap);
    if (_gc_alloc_regions[ap] == NULL && alt_purpose != ap) {
      _gc_alloc_regions[ap] = _gc_alloc_regions[alt_purpose];
    }
  }
  assert(check_gc_alloc_regions(), "alloc regions messed up");
}

void G1CollectedHeap::release_gc_alloc_regions() {
  // We keep a separate list of all regions that have been alloc regions in
  // the current collection pause.  Forget that now.
  forget_alloc_region_list();

  // The current alloc regions contain objs that have survived
  // collection. Make them no longer GC alloc regions.
  for (int ap = 0; ap < GCAllocPurposeCount; ++ap) {
    HeapRegion* r = _gc_alloc_regions[ap];
    if (r != NULL && r->is_empty()) {
      {
        MutexLockerEx x(ZF_mon, Mutex::_no_safepoint_check_flag);
        r->set_zero_fill_complete();
        put_free_region_on_list_locked(r);
      }
    }
    // set_gc_alloc_region will also NULLify all aliases to the region
    set_gc_alloc_region(ap, NULL);
    _gc_alloc_region_counts[ap] = 0;
  }
}

void G1CollectedHeap::init_for_evac_failure(OopsInHeapRegionClosure* cl) {
  _drain_in_progress = false;
  set_evac_failure_closure(cl);
  _evac_failure_scan_stack = new (ResourceObj::C_HEAP) GrowableArray<oop>(40, true);
}

void G1CollectedHeap::finalize_for_evac_failure() {
  assert(_evac_failure_scan_stack != NULL &&
         _evac_failure_scan_stack->length() == 0,
         "Postcondition");
  assert(!_drain_in_progress, "Postcondition");
  // Don't have to delete, since the scan stack is a resource object.
  _evac_failure_scan_stack = NULL;
}



// *** Sequential G1 Evacuation

HeapWord* G1CollectedHeap::allocate_during_gc(GCAllocPurpose purpose, size_t word_size) {
  HeapRegion* alloc_region = _gc_alloc_regions[purpose];
  // let the caller handle alloc failure
  if (alloc_region == NULL) return NULL;
  assert(isHumongous(word_size) || !alloc_region->isHumongous(),
         "Either the object is humongous or the region isn't");
  HeapWord* block = alloc_region->allocate(word_size);
  if (block == NULL) {
    block = allocate_during_gc_slow(purpose, alloc_region, false, word_size);
  }
  return block;
}

class G1IsAliveClosure: public BoolObjectClosure {
  G1CollectedHeap* _g1;
public:
  G1IsAliveClosure(G1CollectedHeap* g1) : _g1(g1) {}
  void do_object(oop p) { assert(false, "Do not call."); }
  bool do_object_b(oop p) {
    // It is reachable if it is outside the collection set, or is inside
    // and forwarded.

#ifdef G1_DEBUG
    gclog_or_tty->print_cr("is alive "PTR_FORMAT" in CS %d forwarded %d overall %d",
                           (void*) p, _g1->obj_in_cs(p), p->is_forwarded(),
                           !_g1->obj_in_cs(p) || p->is_forwarded());
#endif // G1_DEBUG

    return !_g1->obj_in_cs(p) || p->is_forwarded();
  }
};

class G1KeepAliveClosure: public OopClosure {
  G1CollectedHeap* _g1;
public:
  G1KeepAliveClosure(G1CollectedHeap* g1) : _g1(g1) {}
  void do_oop(narrowOop* p) {
    guarantee(false, "NYI");
  }
  void do_oop(oop* p) {
    oop obj = *p;
#ifdef G1_DEBUG
    if (PrintGC && Verbose) {
      gclog_or_tty->print_cr("keep alive *"PTR_FORMAT" = "PTR_FORMAT" "PTR_FORMAT,
                             p, (void*) obj, (void*) *p);
    }
#endif // G1_DEBUG

    if (_g1->obj_in_cs(obj)) {
      assert( obj->is_forwarded(), "invariant" );
      *p = obj->forwardee();

#ifdef G1_DEBUG
      gclog_or_tty->print_cr("     in CSet: moved "PTR_FORMAT" -> "PTR_FORMAT,
                             (void*) obj, (void*) *p);
#endif // G1_DEBUG
    }
  }
};

class RecreateRSetEntriesClosure: public OopClosure {
private:
  G1CollectedHeap* _g1;
  G1RemSet* _g1_rem_set;
  HeapRegion* _from;
public:
  RecreateRSetEntriesClosure(G1CollectedHeap* g1, HeapRegion* from) :
    _g1(g1), _g1_rem_set(g1->g1_rem_set()), _from(from)
  {}

  void do_oop(narrowOop* p) {
    guarantee(false, "NYI");
  }
  void do_oop(oop* p) {
    assert(_from->is_in_reserved(p), "paranoia");
    if (*p != NULL) {
      _g1_rem_set->write_ref(_from, p);
    }
  }
};

class RemoveSelfPointerClosure: public ObjectClosure {
private:
  G1CollectedHeap* _g1;
  ConcurrentMark* _cm;
  HeapRegion* _hr;
  size_t _prev_marked_bytes;
  size_t _next_marked_bytes;
public:
  RemoveSelfPointerClosure(G1CollectedHeap* g1, HeapRegion* hr) :
    _g1(g1), _cm(_g1->concurrent_mark()), _hr(hr),
    _prev_marked_bytes(0), _next_marked_bytes(0)
  {}

  size_t prev_marked_bytes() { return _prev_marked_bytes; }
  size_t next_marked_bytes() { return _next_marked_bytes; }

  // The original idea here was to coalesce evacuated and dead objects.
  // However that caused complications with the block offset table (BOT).
  // In particular if there were two TLABs, one of them partially refined.
  // |----- TLAB_1--------|----TLAB_2-~~~(partially refined part)~~~|
  // The BOT entries of the unrefined part of TLAB_2 point to the start
  // of TLAB_2. If the last object of the TLAB_1 and the first object
  // of TLAB_2 are coalesced, then the cards of the unrefined part
  // would point into middle of the filler object.
  //
  // The current approach is to not coalesce and leave the BOT contents intact.
  void do_object(oop obj) {
    if (obj->is_forwarded() && obj->forwardee() == obj) {
      // The object failed to move.
      assert(!_g1->is_obj_dead(obj), "We should not be preserving dead objs.");
      _cm->markPrev(obj);
      assert(_cm->isPrevMarked(obj), "Should be marked!");
      _prev_marked_bytes += (obj->size() * HeapWordSize);
      if (_g1->mark_in_progress() && !_g1->is_obj_ill(obj)) {
        _cm->markAndGrayObjectIfNecessary(obj);
      }
      obj->set_mark(markOopDesc::prototype());
      // While we were processing RSet buffers during the
      // collection, we actually didn't scan any cards on the
      // collection set, since we didn't want to update remebered
      // sets with entries that point into the collection set, given
      // that live objects fromthe collection set are about to move
      // and such entries will be stale very soon. This change also
      // dealt with a reliability issue which involved scanning a
      // card in the collection set and coming across an array that
      // was being chunked and looking malformed. The problem is
      // that, if evacuation fails, we might have remembered set
      // entries missing given that we skipped cards on the
      // collection set. So, we'll recreate such entries now.
      RecreateRSetEntriesClosure cl(_g1, _hr);
      obj->oop_iterate(&cl);
      assert(_cm->isPrevMarked(obj), "Should be marked!");
    } else {
      // The object has been either evacuated or is dead. Fill it with a
      // dummy object.
      MemRegion mr((HeapWord*)obj, obj->size());
      SharedHeap::fill_region_with_object(mr);
      _cm->clearRangeBothMaps(mr);
    }
  }
};

void G1CollectedHeap::remove_self_forwarding_pointers() {
  HeapRegion* cur = g1_policy()->collection_set();

  while (cur != NULL) {
    assert(g1_policy()->assertMarkedBytesDataOK(), "Should be!");

    if (cur->evacuation_failed()) {
      RemoveSelfPointerClosure rspc(_g1h, cur);
      assert(cur->in_collection_set(), "bad CS");
      cur->object_iterate(&rspc);

      // A number of manipulations to make the TAMS be the current top,
      // and the marked bytes be the ones observed in the iteration.
      if (_g1h->concurrent_mark()->at_least_one_mark_complete()) {
        // The comments below are the postconditions achieved by the
        // calls.  Note especially the last such condition, which says that
        // the count of marked bytes has been properly restored.
        cur->note_start_of_marking(false);
        // _next_top_at_mark_start == top, _next_marked_bytes == 0
        cur->add_to_marked_bytes(rspc.prev_marked_bytes());
        // _next_marked_bytes == prev_marked_bytes.
        cur->note_end_of_marking();
        // _prev_top_at_mark_start == top(),
        // _prev_marked_bytes == prev_marked_bytes
      }
      // If there is no mark in progress, we modified the _next variables
      // above needlessly, but harmlessly.
      if (_g1h->mark_in_progress()) {
        cur->note_start_of_marking(false);
        // _next_top_at_mark_start == top, _next_marked_bytes == 0
        // _next_marked_bytes == next_marked_bytes.
      }

      // Now make sure the region has the right index in the sorted array.
      g1_policy()->note_change_in_marked_bytes(cur);
    }
    cur = cur->next_in_collection_set();
  }
  assert(g1_policy()->assertMarkedBytesDataOK(), "Should be!");

  // Now restore saved marks, if any.
  if (_objs_with_preserved_marks != NULL) {
    assert(_preserved_marks_of_objs != NULL, "Both or none.");
    assert(_objs_with_preserved_marks->length() ==
           _preserved_marks_of_objs->length(), "Both or none.");
    guarantee(_objs_with_preserved_marks->length() ==
              _preserved_marks_of_objs->length(), "Both or none.");
    for (int i = 0; i < _objs_with_preserved_marks->length(); i++) {
      oop obj   = _objs_with_preserved_marks->at(i);
      markOop m = _preserved_marks_of_objs->at(i);
      obj->set_mark(m);
    }
    // Delete the preserved marks growable arrays (allocated on the C heap).
    delete _objs_with_preserved_marks;
    delete _preserved_marks_of_objs;
    _objs_with_preserved_marks = NULL;
    _preserved_marks_of_objs = NULL;
  }
}

void G1CollectedHeap::push_on_evac_failure_scan_stack(oop obj) {
  _evac_failure_scan_stack->push(obj);
}

void G1CollectedHeap::drain_evac_failure_scan_stack() {
  assert(_evac_failure_scan_stack != NULL, "precondition");

  while (_evac_failure_scan_stack->length() > 0) {
     oop obj = _evac_failure_scan_stack->pop();
     _evac_failure_closure->set_region(heap_region_containing(obj));
     obj->oop_iterate_backwards(_evac_failure_closure);
  }
}

void G1CollectedHeap::handle_evacuation_failure(oop old) {
  markOop m = old->mark();
  // forward to self
  assert(!old->is_forwarded(), "precondition");

  old->forward_to(old);
  handle_evacuation_failure_common(old, m);
}

oop
G1CollectedHeap::handle_evacuation_failure_par(OopsInHeapRegionClosure* cl,
                                               oop old) {
  markOop m = old->mark();
  oop forward_ptr = old->forward_to_atomic(old);
  if (forward_ptr == NULL) {
    // Forward-to-self succeeded.
    if (_evac_failure_closure != cl) {
      MutexLockerEx x(EvacFailureStack_lock, Mutex::_no_safepoint_check_flag);
      assert(!_drain_in_progress,
             "Should only be true while someone holds the lock.");
      // Set the global evac-failure closure to the current thread's.
      assert(_evac_failure_closure == NULL, "Or locking has failed.");
      set_evac_failure_closure(cl);
      // Now do the common part.
      handle_evacuation_failure_common(old, m);
      // Reset to NULL.
      set_evac_failure_closure(NULL);
    } else {
      // The lock is already held, and this is recursive.
      assert(_drain_in_progress, "This should only be the recursive case.");
      handle_evacuation_failure_common(old, m);
    }
    return old;
  } else {
    // Someone else had a place to copy it.
    return forward_ptr;
  }
}

void G1CollectedHeap::handle_evacuation_failure_common(oop old, markOop m) {
  set_evacuation_failed(true);

  preserve_mark_if_necessary(old, m);

  HeapRegion* r = heap_region_containing(old);
  if (!r->evacuation_failed()) {
    r->set_evacuation_failed(true);
    if (G1TraceRegions) {
      gclog_or_tty->print("evacuation failed in heap region "PTR_FORMAT" "
                          "["PTR_FORMAT","PTR_FORMAT")\n",
                          r, r->bottom(), r->end());
    }
  }

  push_on_evac_failure_scan_stack(old);

  if (!_drain_in_progress) {
    // prevent recursion in copy_to_survivor_space()
    _drain_in_progress = true;
    drain_evac_failure_scan_stack();
    _drain_in_progress = false;
  }
}

void G1CollectedHeap::preserve_mark_if_necessary(oop obj, markOop m) {
  if (m != markOopDesc::prototype()) {
    if (_objs_with_preserved_marks == NULL) {
      assert(_preserved_marks_of_objs == NULL, "Both or none.");
      _objs_with_preserved_marks =
        new (ResourceObj::C_HEAP) GrowableArray<oop>(40, true);
      _preserved_marks_of_objs =
        new (ResourceObj::C_HEAP) GrowableArray<markOop>(40, true);
    }
    _objs_with_preserved_marks->push(obj);
    _preserved_marks_of_objs->push(m);
  }
}

// *** Parallel G1 Evacuation

HeapWord* G1CollectedHeap::par_allocate_during_gc(GCAllocPurpose purpose,
                                                  size_t word_size) {
  HeapRegion* alloc_region = _gc_alloc_regions[purpose];
  // let the caller handle alloc failure
  if (alloc_region == NULL) return NULL;

  HeapWord* block = alloc_region->par_allocate(word_size);
  if (block == NULL) {
    MutexLockerEx x(par_alloc_during_gc_lock(),
                    Mutex::_no_safepoint_check_flag);
    block = allocate_during_gc_slow(purpose, alloc_region, true, word_size);
  }
  return block;
}

HeapWord*
G1CollectedHeap::allocate_during_gc_slow(GCAllocPurpose purpose,
                                         HeapRegion*    alloc_region,
                                         bool           par,
                                         size_t         word_size) {
  HeapWord* block = NULL;
  // In the parallel case, a previous thread to obtain the lock may have
  // already assigned a new gc_alloc_region.
  if (alloc_region != _gc_alloc_regions[purpose]) {
    assert(par, "But should only happen in parallel case.");
    alloc_region = _gc_alloc_regions[purpose];
    if (alloc_region == NULL) return NULL;
    block = alloc_region->par_allocate(word_size);
    if (block != NULL) return block;
    // Otherwise, continue; this new region is empty, too.
  }
  assert(alloc_region != NULL, "We better have an allocation region");
  // Another thread might have obtained alloc_region for the given
  // purpose, and might be attempting to allocate in it, and might
  // succeed.  Therefore, we can't do the "finalization" stuff on the
  // region below until we're sure the last allocation has happened.
  // We ensure this by allocating the remaining space with a garbage
  // object.
  if (par) par_allocate_remaining_space(alloc_region);
  // Now we can do the post-GC stuff on the region.
  alloc_region->note_end_of_copying();
  g1_policy()->record_after_bytes(alloc_region->used());

  if (_gc_alloc_region_counts[purpose] >= g1_policy()->max_regions(purpose)) {
    // Cannot allocate more regions for the given purpose.
    GCAllocPurpose alt_purpose = g1_policy()->alternative_purpose(purpose);
    // Is there an alternative?
    if (purpose != alt_purpose) {
      HeapRegion* alt_region = _gc_alloc_regions[alt_purpose];
      // Has not the alternative region been aliased?
      if (alloc_region != alt_region) {
        // Try to allocate in the alternative region.
        if (par) {
          block = alt_region->par_allocate(word_size);
        } else {
          block = alt_region->allocate(word_size);
        }
        // Make an alias.
        _gc_alloc_regions[purpose] = _gc_alloc_regions[alt_purpose];
      }
      if (block != NULL) {
        return block;
      }
      // Both the allocation region and the alternative one are full
      // and aliased, replace them with a new allocation region.
      purpose = alt_purpose;
    } else {
      set_gc_alloc_region(purpose, NULL);
      return NULL;
    }
  }

  // Now allocate a new region for allocation.
  alloc_region = newAllocRegionWithExpansion(purpose, word_size, false /*zero_filled*/);

  // let the caller handle alloc failure
  if (alloc_region != NULL) {

    assert(check_gc_alloc_regions(), "alloc regions messed up");
    assert(alloc_region->saved_mark_at_top(),
           "Mark should have been saved already.");
    // We used to assert that the region was zero-filled here, but no
    // longer.

    // This must be done last: once it's installed, other regions may
    // allocate in it (without holding the lock.)
    set_gc_alloc_region(purpose, alloc_region);

    if (par) {
      block = alloc_region->par_allocate(word_size);
    } else {
      block = alloc_region->allocate(word_size);
    }
    // Caller handles alloc failure.
  } else {
    // This sets other apis using the same old alloc region to NULL, also.
    set_gc_alloc_region(purpose, NULL);
  }
  return block;  // May be NULL.
}

void G1CollectedHeap::par_allocate_remaining_space(HeapRegion* r) {
  HeapWord* block = NULL;
  size_t free_words;
  do {
    free_words = r->free()/HeapWordSize;
    // If there's too little space, no one can allocate, so we're done.
    if (free_words < (size_t)oopDesc::header_size()) return;
    // Otherwise, try to claim it.
    block = r->par_allocate(free_words);
  } while (block == NULL);
  SharedHeap::fill_region_with_object(MemRegion(block, free_words));
}

#define use_local_bitmaps         1
#define verify_local_bitmaps      0

#ifndef PRODUCT

class GCLabBitMap;
class GCLabBitMapClosure: public BitMapClosure {
private:
  ConcurrentMark* _cm;
  GCLabBitMap*    _bitmap;

public:
  GCLabBitMapClosure(ConcurrentMark* cm,
                     GCLabBitMap* bitmap) {
    _cm     = cm;
    _bitmap = bitmap;
  }

  virtual bool do_bit(size_t offset);
};

#endif // PRODUCT

#define oop_buffer_length 256

class GCLabBitMap: public BitMap {
private:
  ConcurrentMark* _cm;

  int       _shifter;
  size_t    _bitmap_word_covers_words;

  // beginning of the heap
  HeapWord* _heap_start;

  // this is the actual start of the GCLab
  HeapWord* _real_start_word;

  // this is the actual end of the GCLab
  HeapWord* _real_end_word;

  // this is the first word, possibly located before the actual start
  // of the GCLab, that corresponds to the first bit of the bitmap
  HeapWord* _start_word;

  // size of a GCLab in words
  size_t _gclab_word_size;

  static int shifter() {
    return MinObjAlignment - 1;
  }

  // how many heap words does a single bitmap word corresponds to?
  static size_t bitmap_word_covers_words() {
    return BitsPerWord << shifter();
  }

  static size_t gclab_word_size() {
    return ParallelGCG1AllocBufferSize / HeapWordSize;
  }

  static size_t bitmap_size_in_bits() {
    size_t bits_in_bitmap = gclab_word_size() >> shifter();
    // We are going to ensure that the beginning of a word in this
    // bitmap also corresponds to the beginning of a word in the
    // global marking bitmap. To handle the case where a GCLab
    // starts from the middle of the bitmap, we need to add enough
    // space (i.e. up to a bitmap word) to ensure that we have
    // enough bits in the bitmap.
    return bits_in_bitmap + BitsPerWord - 1;
  }
public:
  GCLabBitMap(HeapWord* heap_start)
    : BitMap(bitmap_size_in_bits()),
      _cm(G1CollectedHeap::heap()->concurrent_mark()),
      _shifter(shifter()),
      _bitmap_word_covers_words(bitmap_word_covers_words()),
      _heap_start(heap_start),
      _gclab_word_size(gclab_word_size()),
      _real_start_word(NULL),
      _real_end_word(NULL),
      _start_word(NULL)
  {
    guarantee( size_in_words() >= bitmap_size_in_words(),
               "just making sure");
  }

  inline unsigned heapWordToOffset(HeapWord* addr) {
    unsigned offset = (unsigned) pointer_delta(addr, _start_word) >> _shifter;
    assert(offset < size(), "offset should be within bounds");
    return offset;
  }

  inline HeapWord* offsetToHeapWord(size_t offset) {
    HeapWord* addr =  _start_word + (offset << _shifter);
    assert(_real_start_word <= addr && addr < _real_end_word, "invariant");
    return addr;
  }

  bool fields_well_formed() {
    bool ret1 = (_real_start_word == NULL) &&
                (_real_end_word == NULL) &&
                (_start_word == NULL);
    if (ret1)
      return true;

    bool ret2 = _real_start_word >= _start_word &&
      _start_word < _real_end_word &&
      (_real_start_word + _gclab_word_size) == _real_end_word &&
      (_start_word + _gclab_word_size + _bitmap_word_covers_words)
                                                              > _real_end_word;
    return ret2;
  }

  inline bool mark(HeapWord* addr) {
    guarantee(use_local_bitmaps, "invariant");
    assert(fields_well_formed(), "invariant");

    if (addr >= _real_start_word && addr < _real_end_word) {
      assert(!isMarked(addr), "should not have already been marked");

      // first mark it on the bitmap
      at_put(heapWordToOffset(addr), true);

      return true;
    } else {
      return false;
    }
  }

  inline bool isMarked(HeapWord* addr) {
    guarantee(use_local_bitmaps, "invariant");
    assert(fields_well_formed(), "invariant");

    return at(heapWordToOffset(addr));
  }

  void set_buffer(HeapWord* start) {
    guarantee(use_local_bitmaps, "invariant");
    clear();

    assert(start != NULL, "invariant");
    _real_start_word = start;
    _real_end_word   = start + _gclab_word_size;

    size_t diff =
      pointer_delta(start, _heap_start) % _bitmap_word_covers_words;
    _start_word = start - diff;

    assert(fields_well_formed(), "invariant");
  }

#ifndef PRODUCT
  void verify() {
    // verify that the marks have been propagated
    GCLabBitMapClosure cl(_cm, this);
    iterate(&cl);
  }
#endif // PRODUCT

  void retire() {
    guarantee(use_local_bitmaps, "invariant");
    assert(fields_well_formed(), "invariant");

    if (_start_word != NULL) {
      CMBitMap*       mark_bitmap = _cm->nextMarkBitMap();

      // this means that the bitmap was set up for the GCLab
      assert(_real_start_word != NULL && _real_end_word != NULL, "invariant");

      mark_bitmap->mostly_disjoint_range_union(this,
                                0, // always start from the start of the bitmap
                                _start_word,
                                size_in_words());
      _cm->grayRegionIfNecessary(MemRegion(_real_start_word, _real_end_word));

#ifndef PRODUCT
      if (use_local_bitmaps && verify_local_bitmaps)
        verify();
#endif // PRODUCT
    } else {
      assert(_real_start_word == NULL && _real_end_word == NULL, "invariant");
    }
  }

  static size_t bitmap_size_in_words() {
    return (bitmap_size_in_bits() + BitsPerWord - 1) / BitsPerWord;
  }
};

#ifndef PRODUCT

bool GCLabBitMapClosure::do_bit(size_t offset) {
  HeapWord* addr = _bitmap->offsetToHeapWord(offset);
  guarantee(_cm->isMarked(oop(addr)), "it should be!");
  return true;
}

#endif // PRODUCT

class G1ParGCAllocBuffer: public ParGCAllocBuffer {
private:
  bool        _retired;
  bool        _during_marking;
  GCLabBitMap _bitmap;

public:
  G1ParGCAllocBuffer() :
    ParGCAllocBuffer(ParallelGCG1AllocBufferSize / HeapWordSize),
    _during_marking(G1CollectedHeap::heap()->mark_in_progress()),
    _bitmap(G1CollectedHeap::heap()->reserved_region().start()),
    _retired(false)
  { }

  inline bool mark(HeapWord* addr) {
    guarantee(use_local_bitmaps, "invariant");
    assert(_during_marking, "invariant");
    return _bitmap.mark(addr);
  }

  inline void set_buf(HeapWord* buf) {
    if (use_local_bitmaps && _during_marking)
      _bitmap.set_buffer(buf);
    ParGCAllocBuffer::set_buf(buf);
    _retired = false;
  }

  inline void retire(bool end_of_gc, bool retain) {
    if (_retired)
      return;
    if (use_local_bitmaps && _during_marking) {
      _bitmap.retire();
    }
    ParGCAllocBuffer::retire(end_of_gc, retain);
    _retired = true;
  }
};


class G1ParScanThreadState : public StackObj {
protected:
  G1CollectedHeap* _g1h;
  RefToScanQueue*  _refs;

  typedef GrowableArray<oop*> OverflowQueue;
  OverflowQueue* _overflowed_refs;

  G1ParGCAllocBuffer _alloc_buffers[GCAllocPurposeCount];

  size_t           _alloc_buffer_waste;
  size_t           _undo_waste;

  OopsInHeapRegionClosure*      _evac_failure_cl;
  G1ParScanHeapEvacClosure*     _evac_cl;
  G1ParScanPartialArrayClosure* _partial_scan_cl;

  int _hash_seed;
  int _queue_num;

  int _term_attempts;
#if G1_DETAILED_STATS
  int _pushes, _pops, _steals, _steal_attempts;
  int _overflow_pushes;
#endif

  double _start;
  double _start_strong_roots;
  double _strong_roots_time;
  double _start_term;
  double _term_time;

  // Map from young-age-index (0 == not young, 1 is youngest) to
  // surviving words. base is what we get back from the malloc call
  size_t* _surviving_young_words_base;
  // this points into the array, as we use the first few entries for padding
  size_t* _surviving_young_words;

#define PADDING_ELEM_NUM (64 / sizeof(size_t))

  void   add_to_alloc_buffer_waste(size_t waste) { _alloc_buffer_waste += waste; }

  void   add_to_undo_waste(size_t waste)         { _undo_waste += waste; }

public:
  G1ParScanThreadState(G1CollectedHeap* g1h, int queue_num)
    : _g1h(g1h),
      _refs(g1h->task_queue(queue_num)),
      _hash_seed(17), _queue_num(queue_num),
      _term_attempts(0),
#if G1_DETAILED_STATS
      _pushes(0), _pops(0), _steals(0),
      _steal_attempts(0),  _overflow_pushes(0),
#endif
      _strong_roots_time(0), _term_time(0),
      _alloc_buffer_waste(0), _undo_waste(0)
  {
    // we allocate G1YoungSurvRateNumRegions plus one entries, since
    // we "sacrifice" entry 0 to keep track of surviving bytes for
    // non-young regions (where the age is -1)
    // We also add a few elements at the beginning and at the end in
    // an attempt to eliminate cache contention
    size_t real_length = 1 + _g1h->g1_policy()->young_cset_length();
    size_t array_length = PADDING_ELEM_NUM +
                          real_length +
                          PADDING_ELEM_NUM;
    _surviving_young_words_base = NEW_C_HEAP_ARRAY(size_t, array_length);
    if (_surviving_young_words_base == NULL)
      vm_exit_out_of_memory(array_length * sizeof(size_t),
                            "Not enough space for young surv histo.");
    _surviving_young_words = _surviving_young_words_base + PADDING_ELEM_NUM;
    memset(_surviving_young_words, 0, real_length * sizeof(size_t));

    _overflowed_refs = new OverflowQueue(10);

    _start = os::elapsedTime();
  }

  ~G1ParScanThreadState() {
    FREE_C_HEAP_ARRAY(size_t, _surviving_young_words_base);
  }

  RefToScanQueue*   refs()            { return _refs;             }
  OverflowQueue*    overflowed_refs() { return _overflowed_refs;  }

  inline G1ParGCAllocBuffer* alloc_buffer(GCAllocPurpose purpose) {
    return &_alloc_buffers[purpose];
  }

  size_t alloc_buffer_waste()                    { return _alloc_buffer_waste; }
  size_t undo_waste()                            { return _undo_waste; }

  void push_on_queue(oop* ref) {
    if (!refs()->push(ref)) {
      overflowed_refs()->push(ref);
      IF_G1_DETAILED_STATS(note_overflow_push());
    } else {
      IF_G1_DETAILED_STATS(note_push());
    }
  }

  void pop_from_queue(oop*& ref) {
    if (!refs()->pop_local(ref)) {
      ref = NULL;
    } else {
      IF_G1_DETAILED_STATS(note_pop());
    }
  }

  void pop_from_overflow_queue(oop*& ref) {
    ref = overflowed_refs()->pop();
  }

  int refs_to_scan()                             { return refs()->size();                 }
  int overflowed_refs_to_scan()                  { return overflowed_refs()->length();    }

  HeapWord* allocate_slow(GCAllocPurpose purpose, size_t word_sz) {

    HeapWord* obj = NULL;
    if (word_sz * 100 <
        (size_t)(ParallelGCG1AllocBufferSize / HeapWordSize) *
                                                  ParallelGCBufferWastePct) {
      G1ParGCAllocBuffer* alloc_buf = alloc_buffer(purpose);
      add_to_alloc_buffer_waste(alloc_buf->words_remaining());
      alloc_buf->retire(false, false);

      HeapWord* buf =
        _g1h->par_allocate_during_gc(purpose, ParallelGCG1AllocBufferSize / HeapWordSize);
      if (buf == NULL) return NULL; // Let caller handle allocation failure.
      // Otherwise.
      alloc_buf->set_buf(buf);

      obj = alloc_buf->allocate(word_sz);
      assert(obj != NULL, "buffer was definitely big enough...");
    }
    else {
      obj = _g1h->par_allocate_during_gc(purpose, word_sz);
    }
    return obj;
  }

  HeapWord* allocate(GCAllocPurpose purpose, size_t word_sz) {
    HeapWord* obj = alloc_buffer(purpose)->allocate(word_sz);
    if (obj != NULL) return obj;
    return allocate_slow(purpose, word_sz);
  }

  void undo_allocation(GCAllocPurpose purpose, HeapWord* obj, size_t word_sz) {
    if (alloc_buffer(purpose)->contains(obj)) {
      guarantee(alloc_buffer(purpose)->contains(obj + word_sz - 1),
                "should contain whole object");
      alloc_buffer(purpose)->undo_allocation(obj, word_sz);
    }
    else {
      SharedHeap::fill_region_with_object(MemRegion(obj, word_sz));
      add_to_undo_waste(word_sz);
    }
  }

  void set_evac_failure_closure(OopsInHeapRegionClosure* evac_failure_cl) {
    _evac_failure_cl = evac_failure_cl;
  }
  OopsInHeapRegionClosure* evac_failure_closure() {
    return _evac_failure_cl;
  }

  void set_evac_closure(G1ParScanHeapEvacClosure* evac_cl) {
    _evac_cl = evac_cl;
  }

  void set_partial_scan_closure(G1ParScanPartialArrayClosure* partial_scan_cl) {
    _partial_scan_cl = partial_scan_cl;
  }

  int* hash_seed() { return &_hash_seed; }
  int  queue_num() { return _queue_num; }

  int term_attempts()   { return _term_attempts; }
  void note_term_attempt()  { _term_attempts++; }

#if G1_DETAILED_STATS
  int pushes()          { return _pushes; }
  int pops()            { return _pops; }
  int steals()          { return _steals; }
  int steal_attempts()  { return _steal_attempts; }
  int overflow_pushes() { return _overflow_pushes; }

  void note_push()          { _pushes++; }
  void note_pop()           { _pops++; }
  void note_steal()         { _steals++; }
  void note_steal_attempt() { _steal_attempts++; }
  void note_overflow_push() { _overflow_pushes++; }
#endif

  void start_strong_roots() {
    _start_strong_roots = os::elapsedTime();
  }
  void end_strong_roots() {
    _strong_roots_time += (os::elapsedTime() - _start_strong_roots);
  }
  double strong_roots_time() { return _strong_roots_time; }

  void start_term_time() {
    note_term_attempt();
    _start_term = os::elapsedTime();
  }
  void end_term_time() {
    _term_time += (os::elapsedTime() - _start_term);
  }
  double term_time() { return _term_time; }

  double elapsed() {
    return os::elapsedTime() - _start;
  }

  size_t* surviving_young_words() {
    // We add on to hide entry 0 which accumulates surviving words for
    // age -1 regions (i.e. non-young ones)
    return _surviving_young_words;
  }

  void retire_alloc_buffers() {
    for (int ap = 0; ap < GCAllocPurposeCount; ++ap) {
      size_t waste = _alloc_buffers[ap].words_remaining();
      add_to_alloc_buffer_waste(waste);
      _alloc_buffers[ap].retire(true, false);
    }
  }

  void trim_queue() {
    while (refs_to_scan() > 0 || overflowed_refs_to_scan() > 0) {
      oop *ref_to_scan = NULL;
      if (overflowed_refs_to_scan() == 0) {
        pop_from_queue(ref_to_scan);
      } else {
        pop_from_overflow_queue(ref_to_scan);
      }
      if (ref_to_scan != NULL) {
        if ((intptr_t)ref_to_scan & G1_PARTIAL_ARRAY_MASK) {
          _partial_scan_cl->do_oop_nv(ref_to_scan);
        } else {
          // Note: we can use "raw" versions of "region_containing" because
          // "obj_to_scan" is definitely in the heap, and is not in a
          // humongous region.
          HeapRegion* r = _g1h->heap_region_containing_raw(ref_to_scan);
          _evac_cl->set_region(r);
          _evac_cl->do_oop_nv(ref_to_scan);
        }
      }
    }
  }
};


G1ParClosureSuper::G1ParClosureSuper(G1CollectedHeap* g1, G1ParScanThreadState* par_scan_state) :
  _g1(g1), _g1_rem(_g1->g1_rem_set()), _cm(_g1->concurrent_mark()),
  _par_scan_state(par_scan_state) { }

// This closure is applied to the fields of the objects that have just been copied.
// Should probably be made inline and moved in g1OopClosures.inline.hpp.
void G1ParScanClosure::do_oop_nv(oop* p) {
  oop obj = *p;
  if (obj != NULL) {
    if (_g1->obj_in_cs(obj)) {
      if (obj->is_forwarded()) {
        *p = obj->forwardee();
      } else {
        _par_scan_state->push_on_queue(p);
        return;
      }
    }
    _g1_rem->par_write_ref(_from, p, _par_scan_state->queue_num());
  }
}

void G1ParCopyHelper::mark_forwardee(oop* p) {
  // This is called _after_ do_oop_work has been called, hence after
  // the object has been relocated to its new location and *p points
  // to its new location.

  oop thisOop = *p;
  if (thisOop != NULL) {
    assert((_g1->evacuation_failed()) || (!_g1->obj_in_cs(thisOop)),
           "shouldn't still be in the CSet if evacuation didn't fail.");
    HeapWord* addr = (HeapWord*)thisOop;
    if (_g1->is_in_g1_reserved(addr))
      _cm->grayRoot(oop(addr));
  }
}

oop G1ParCopyHelper::copy_to_survivor_space(oop old) {
  size_t    word_sz = old->size();
  HeapRegion* from_region = _g1->heap_region_containing_raw(old);
  // +1 to make the -1 indexes valid...
  int       young_index = from_region->young_index_in_cset()+1;
  assert( (from_region->is_young() && young_index > 0) ||
          (!from_region->is_young() && young_index == 0), "invariant" );
  G1CollectorPolicy* g1p = _g1->g1_policy();
  markOop m = old->mark();
  GCAllocPurpose alloc_purpose = g1p->evacuation_destination(from_region, m->age(),
                                                             word_sz);
  HeapWord* obj_ptr = _par_scan_state->allocate(alloc_purpose, word_sz);
  oop       obj     = oop(obj_ptr);

  if (obj_ptr == NULL) {
    // This will either forward-to-self, or detect that someone else has
    // installed a forwarding pointer.
    OopsInHeapRegionClosure* cl = _par_scan_state->evac_failure_closure();
    return _g1->handle_evacuation_failure_par(cl, old);
  }

  oop forward_ptr = old->forward_to_atomic(obj);
  if (forward_ptr == NULL) {
    Copy::aligned_disjoint_words((HeapWord*) old, obj_ptr, word_sz);
    obj->set_mark(m);
    if (g1p->track_object_age(alloc_purpose)) {
      obj->incr_age();
    }
    // preserve "next" mark bit
    if (_g1->mark_in_progress() && !_g1->is_obj_ill(old)) {
      if (!use_local_bitmaps ||
          !_par_scan_state->alloc_buffer(alloc_purpose)->mark(obj_ptr)) {
        // if we couldn't mark it on the local bitmap (this happens when
        // the object was not allocated in the GCLab), we have to bite
        // the bullet and do the standard parallel mark
        _cm->markAndGrayObjectIfNecessary(obj);
      }
#if 1
      if (_g1->isMarkedNext(old)) {
        _cm->nextMarkBitMap()->parClear((HeapWord*)old);
      }
#endif
    }

    size_t* surv_young_words = _par_scan_state->surviving_young_words();
    surv_young_words[young_index] += word_sz;

    if (obj->is_objArray() && arrayOop(obj)->length() >= ParGCArrayScanChunk) {
      arrayOop(old)->set_length(0);
      _par_scan_state->push_on_queue((oop*) ((intptr_t)old | G1_PARTIAL_ARRAY_MASK));
    } else {
      _scanner->set_region(_g1->heap_region_containing(obj));
      obj->oop_iterate_backwards(_scanner);
    }
  } else {
    _par_scan_state->undo_allocation(alloc_purpose, obj_ptr, word_sz);
    obj = forward_ptr;
  }
  return obj;
}

template<bool do_gen_barrier, G1Barrier barrier, bool do_mark_forwardee>
void G1ParCopyClosure<do_gen_barrier, barrier, do_mark_forwardee>::do_oop_work(oop* p) {
  oop obj = *p;
  assert(barrier != G1BarrierRS || obj != NULL,
         "Precondition: G1BarrierRS implies obj is nonNull");

  if (obj != NULL) {
    if (_g1->obj_in_cs(obj)) {
#if G1_REM_SET_LOGGING
      gclog_or_tty->print_cr("Loc "PTR_FORMAT" contains pointer "PTR_FORMAT" into CS.",
                             p, (void*) obj);
#endif
      if (obj->is_forwarded()) {
        *p = obj->forwardee();
      } else {
        *p = copy_to_survivor_space(obj);
      }
      // When scanning the RS, we only care about objs in CS.
      if (barrier == G1BarrierRS) {
        _g1_rem->par_write_ref(_from, p, _par_scan_state->queue_num());
      }
    }
    // When scanning moved objs, must look at all oops.
    if (barrier == G1BarrierEvac) {
      _g1_rem->par_write_ref(_from, p, _par_scan_state->queue_num());
    }

    if (do_gen_barrier) {
      par_do_barrier(p);
    }
  }
}

template void G1ParCopyClosure<false, G1BarrierEvac, false>::do_oop_work(oop* p);

template <class T> void G1ParScanPartialArrayClosure::process_array_chunk(
  oop obj, int start, int end) {
  // process our set of indices (include header in first chunk)
  assert(start < end, "invariant");
  T* const base      = (T*)objArrayOop(obj)->base();
  T* const start_addr = base + start;
  T* const end_addr   = base + end;
  MemRegion mr((HeapWord*)start_addr, (HeapWord*)end_addr);
  _scanner.set_region(_g1->heap_region_containing(obj));
  obj->oop_iterate(&_scanner, mr);
}

void G1ParScanPartialArrayClosure::do_oop_nv(oop* p) {
  assert(!UseCompressedOops, "Needs to be fixed to work with compressed oops");
  oop old = oop((intptr_t)p & ~G1_PARTIAL_ARRAY_MASK);
  assert(old->is_objArray(), "must be obj array");
  assert(old->is_forwarded(), "must be forwarded");
  assert(Universe::heap()->is_in_reserved(old), "must be in heap.");

  objArrayOop obj = objArrayOop(old->forwardee());
  assert((void*)old != (void*)old->forwardee(), "self forwarding here?");
  // Process ParGCArrayScanChunk elements now
  // and push the remainder back onto queue
  int start     = arrayOop(old)->length();
  int end       = obj->length();
  int remainder = end - start;
  assert(start <= end, "just checking");
  if (remainder > 2 * ParGCArrayScanChunk) {
    // Test above combines last partial chunk with a full chunk
    end = start + ParGCArrayScanChunk;
    arrayOop(old)->set_length(end);
    // Push remainder.
    _par_scan_state->push_on_queue((oop*) ((intptr_t) old | G1_PARTIAL_ARRAY_MASK));
  } else {
    // Restore length so that the heap remains parsable in
    // case of evacuation failure.
    arrayOop(old)->set_length(end);
  }

  // process our set of indices (include header in first chunk)
  process_array_chunk<oop>(obj, start, end);
  oop* start_addr = start == 0 ? (oop*)obj : obj->obj_at_addr<oop>(start);
  oop* end_addr   = (oop*)(obj->base()) + end; // obj_at_addr(end) asserts end < length
  MemRegion mr((HeapWord*)start_addr, (HeapWord*)end_addr);
  _scanner.set_region(_g1->heap_region_containing(obj));
  obj->oop_iterate(&_scanner, mr);
}

int G1ScanAndBalanceClosure::_nq = 0;

class G1ParEvacuateFollowersClosure : public VoidClosure {
protected:
  G1CollectedHeap*              _g1h;
  G1ParScanThreadState*         _par_scan_state;
  RefToScanQueueSet*            _queues;
  ParallelTaskTerminator*       _terminator;

  G1ParScanThreadState*   par_scan_state() { return _par_scan_state; }
  RefToScanQueueSet*      queues()         { return _queues; }
  ParallelTaskTerminator* terminator()     { return _terminator; }

public:
  G1ParEvacuateFollowersClosure(G1CollectedHeap* g1h,
                                G1ParScanThreadState* par_scan_state,
                                RefToScanQueueSet* queues,
                                ParallelTaskTerminator* terminator)
    : _g1h(g1h), _par_scan_state(par_scan_state),
      _queues(queues), _terminator(terminator) {}

  void do_void() {
    G1ParScanThreadState* pss = par_scan_state();
    while (true) {
      oop* ref_to_scan;
      pss->trim_queue();
      IF_G1_DETAILED_STATS(pss->note_steal_attempt());
      if (queues()->steal(pss->queue_num(),
                          pss->hash_seed(),
                          ref_to_scan)) {
        IF_G1_DETAILED_STATS(pss->note_steal());
        pss->push_on_queue(ref_to_scan);
        continue;
      }
      pss->start_term_time();
      if (terminator()->offer_termination()) break;
      pss->end_term_time();
    }
    pss->end_term_time();
    pss->retire_alloc_buffers();
  }
};

class G1ParTask : public AbstractGangTask {
protected:
  G1CollectedHeap*       _g1h;
  RefToScanQueueSet      *_queues;
  ParallelTaskTerminator _terminator;

  Mutex _stats_lock;
  Mutex* stats_lock() { return &_stats_lock; }

  size_t getNCards() {
    return (_g1h->capacity() + G1BlockOffsetSharedArray::N_bytes - 1)
      / G1BlockOffsetSharedArray::N_bytes;
  }

public:
  G1ParTask(G1CollectedHeap* g1h, int workers, RefToScanQueueSet *task_queues)
    : AbstractGangTask("G1 collection"),
      _g1h(g1h),
      _queues(task_queues),
      _terminator(workers, _queues),
      _stats_lock(Mutex::leaf, "parallel G1 stats lock", true)
  {}

  RefToScanQueueSet* queues() { return _queues; }

  RefToScanQueue *work_queue(int i) {
    return queues()->queue(i);
  }

  void work(int i) {
    ResourceMark rm;
    HandleMark   hm;

    G1ParScanThreadState pss(_g1h, i);
    G1ParScanHeapEvacClosure     scan_evac_cl(_g1h, &pss);
    G1ParScanHeapEvacClosure     evac_failure_cl(_g1h, &pss);
    G1ParScanPartialArrayClosure partial_scan_cl(_g1h, &pss);

    pss.set_evac_closure(&scan_evac_cl);
    pss.set_evac_failure_closure(&evac_failure_cl);
    pss.set_partial_scan_closure(&partial_scan_cl);

    G1ParScanExtRootClosure         only_scan_root_cl(_g1h, &pss);
    G1ParScanPermClosure            only_scan_perm_cl(_g1h, &pss);
    G1ParScanHeapRSClosure          only_scan_heap_rs_cl(_g1h, &pss);
    G1ParScanAndMarkExtRootClosure  scan_mark_root_cl(_g1h, &pss);
    G1ParScanAndMarkPermClosure     scan_mark_perm_cl(_g1h, &pss);
    G1ParScanAndMarkHeapRSClosure   scan_mark_heap_rs_cl(_g1h, &pss);

    OopsInHeapRegionClosure        *scan_root_cl;
    OopsInHeapRegionClosure        *scan_perm_cl;
    OopsInHeapRegionClosure        *scan_so_cl;

    if (_g1h->g1_policy()->should_initiate_conc_mark()) {
      scan_root_cl = &scan_mark_root_cl;
      scan_perm_cl = &scan_mark_perm_cl;
      scan_so_cl   = &scan_mark_heap_rs_cl;
    } else {
      scan_root_cl = &only_scan_root_cl;
      scan_perm_cl = &only_scan_perm_cl;
      scan_so_cl   = &only_scan_heap_rs_cl;
    }

    pss.start_strong_roots();
    _g1h->g1_process_strong_roots(/* not collecting perm */ false,
                                  SharedHeap::SO_AllClasses,
                                  scan_root_cl,
                                  &only_scan_heap_rs_cl,
                                  scan_so_cl,
                                  scan_perm_cl,
                                  i);
    pss.end_strong_roots();
    {
      double start = os::elapsedTime();
      G1ParEvacuateFollowersClosure evac(_g1h, &pss, _queues, &_terminator);
      evac.do_void();
      double elapsed_ms = (os::elapsedTime()-start)*1000.0;
      double term_ms = pss.term_time()*1000.0;
      _g1h->g1_policy()->record_obj_copy_time(i, elapsed_ms-term_ms);
      _g1h->g1_policy()->record_termination_time(i, term_ms);
    }
    _g1h->update_surviving_young_words(pss.surviving_young_words()+1);

    // Clean up any par-expanded rem sets.
    HeapRegionRemSet::par_cleanup();

    MutexLocker x(stats_lock());
    if (ParallelGCVerbose) {
      gclog_or_tty->print("Thread %d complete:\n", i);
#if G1_DETAILED_STATS
      gclog_or_tty->print("  Pushes: %7d    Pops: %7d   Overflows: %7d   Steals %7d (in %d attempts)\n",
                          pss.pushes(),
                          pss.pops(),
                          pss.overflow_pushes(),
                          pss.steals(),
                          pss.steal_attempts());
#endif
      double elapsed      = pss.elapsed();
      double strong_roots = pss.strong_roots_time();
      double term         = pss.term_time();
      gclog_or_tty->print("  Elapsed: %7.2f ms.\n"
                          "    Strong roots: %7.2f ms (%6.2f%%)\n"
                          "    Termination:  %7.2f ms (%6.2f%%) (in %d entries)\n",
                          elapsed * 1000.0,
                          strong_roots * 1000.0, (strong_roots*100.0/elapsed),
                          term * 1000.0, (term*100.0/elapsed),
                          pss.term_attempts());
      size_t total_waste = pss.alloc_buffer_waste() + pss.undo_waste();
      gclog_or_tty->print("  Waste: %8dK\n"
                 "    Alloc Buffer: %8dK\n"
                 "    Undo: %8dK\n",
                 (total_waste * HeapWordSize) / K,
                 (pss.alloc_buffer_waste() * HeapWordSize) / K,
                 (pss.undo_waste() * HeapWordSize) / K);
    }

    assert(pss.refs_to_scan() == 0, "Task queue should be empty");
    assert(pss.overflowed_refs_to_scan() == 0, "Overflow queue should be empty");
  }
};

// *** Common G1 Evacuation Stuff

class G1CountClosure: public OopsInHeapRegionClosure {
public:
  int n;
  G1CountClosure() : n(0) {}
  void do_oop(narrowOop* p) {
    guarantee(false, "NYI");
  }
  void do_oop(oop* p) {
    oop obj = *p;
    assert(obj != NULL && G1CollectedHeap::heap()->obj_in_cs(obj),
           "Rem set closure called on non-rem-set pointer.");
    n++;
  }
};

static G1CountClosure count_closure;

void
G1CollectedHeap::
g1_process_strong_roots(bool collecting_perm_gen,
                        SharedHeap::ScanningOption so,
                        OopClosure* scan_non_heap_roots,
                        OopsInHeapRegionClosure* scan_rs,
                        OopsInHeapRegionClosure* scan_so,
                        OopsInGenClosure* scan_perm,
                        int worker_i) {
  // First scan the strong roots, including the perm gen.
  double ext_roots_start = os::elapsedTime();
  double closure_app_time_sec = 0.0;

  BufferingOopClosure buf_scan_non_heap_roots(scan_non_heap_roots);
  BufferingOopsInGenClosure buf_scan_perm(scan_perm);
  buf_scan_perm.set_generation(perm_gen());

  process_strong_roots(collecting_perm_gen, so,
                       &buf_scan_non_heap_roots,
                       &buf_scan_perm);
  // Finish up any enqueued closure apps.
  buf_scan_non_heap_roots.done();
  buf_scan_perm.done();
  double ext_roots_end = os::elapsedTime();
  g1_policy()->reset_obj_copy_time(worker_i);
  double obj_copy_time_sec =
    buf_scan_non_heap_roots.closure_app_seconds() +
    buf_scan_perm.closure_app_seconds();
  g1_policy()->record_obj_copy_time(worker_i, obj_copy_time_sec * 1000.0);
  double ext_root_time_ms =
    ((ext_roots_end - ext_roots_start) - obj_copy_time_sec) * 1000.0;
  g1_policy()->record_ext_root_scan_time(worker_i, ext_root_time_ms);

  // Scan strong roots in mark stack.
  if (!_process_strong_tasks->is_task_claimed(G1H_PS_mark_stack_oops_do)) {
    concurrent_mark()->oops_do(scan_non_heap_roots);
  }
  double mark_stack_scan_ms = (os::elapsedTime() - ext_roots_end) * 1000.0;
  g1_policy()->record_mark_stack_scan_time(worker_i, mark_stack_scan_ms);

  // XXX What should this be doing in the parallel case?
  g1_policy()->record_collection_pause_end_CH_strong_roots();
  if (G1VerifyRemSet) {
    // :::: FIXME ::::
    // The stupid remembered set doesn't know how to filter out dead
    // objects, which the smart one does, and so when it is created
    // and then compared the number of entries in each differs and
    // the verification code fails.
    guarantee(false, "verification code is broken, see note");

    // Let's make sure that the current rem set agrees with the stupidest
    // one possible!
    bool refs_enabled = ref_processor()->discovery_enabled();
    if (refs_enabled) ref_processor()->disable_discovery();
    StupidG1RemSet stupid(this);
    count_closure.n = 0;
    stupid.oops_into_collection_set_do(&count_closure, worker_i);
    int stupid_n = count_closure.n;
    count_closure.n = 0;
    g1_rem_set()->oops_into_collection_set_do(&count_closure, worker_i);
    guarantee(count_closure.n == stupid_n, "Old and new rem sets differ.");
    gclog_or_tty->print_cr("\nFound %d pointers in heap RS.", count_closure.n);
    if (refs_enabled) ref_processor()->enable_discovery();
  }
  if (scan_so != NULL) {
    scan_scan_only_set(scan_so, worker_i);
  }
  // Now scan the complement of the collection set.
  if (scan_rs != NULL) {
    g1_rem_set()->oops_into_collection_set_do(scan_rs, worker_i);
  }
  // Finish with the ref_processor roots.
  if (!_process_strong_tasks->is_task_claimed(G1H_PS_refProcessor_oops_do)) {
    ref_processor()->oops_do(scan_non_heap_roots);
  }
  g1_policy()->record_collection_pause_end_G1_strong_roots();
  _process_strong_tasks->all_tasks_completed();
}

void
G1CollectedHeap::scan_scan_only_region(HeapRegion* r,
                                       OopsInHeapRegionClosure* oc,
                                       int worker_i) {
  HeapWord* startAddr = r->bottom();
  HeapWord* endAddr = r->used_region().end();

  oc->set_region(r);

  HeapWord* p = r->bottom();
  HeapWord* t = r->top();
  guarantee( p == r->next_top_at_mark_start(), "invariant" );
  while (p < t) {
    oop obj = oop(p);
    p += obj->oop_iterate(oc);
  }
}

void
G1CollectedHeap::scan_scan_only_set(OopsInHeapRegionClosure* oc,
                                    int worker_i) {
  double start = os::elapsedTime();

  BufferingOopsInHeapRegionClosure boc(oc);

  FilterInHeapRegionAndIntoCSClosure scan_only(this, &boc);
  FilterAndMarkInHeapRegionAndIntoCSClosure scan_and_mark(this, &boc, concurrent_mark());

  OopsInHeapRegionClosure *foc;
  if (g1_policy()->should_initiate_conc_mark())
    foc = &scan_and_mark;
  else
    foc = &scan_only;

  HeapRegion* hr;
  int n = 0;
  while ((hr = _young_list->par_get_next_scan_only_region()) != NULL) {
    scan_scan_only_region(hr, foc, worker_i);
    ++n;
  }
  boc.done();

  double closure_app_s = boc.closure_app_seconds();
  g1_policy()->record_obj_copy_time(worker_i, closure_app_s * 1000.0);
  double ms = (os::elapsedTime() - start - closure_app_s)*1000.0;
  g1_policy()->record_scan_only_time(worker_i, ms, n);
}

void
G1CollectedHeap::g1_process_weak_roots(OopClosure* root_closure,
                                       OopClosure* non_root_closure) {
  SharedHeap::process_weak_roots(root_closure, non_root_closure);
}


class SaveMarksClosure: public HeapRegionClosure {
public:
  bool doHeapRegion(HeapRegion* r) {
    r->save_marks();
    return false;
  }
};

void G1CollectedHeap::save_marks() {
  if (ParallelGCThreads == 0) {
    SaveMarksClosure sm;
    heap_region_iterate(&sm);
  }
  // We do this even in the parallel case
  perm_gen()->save_marks();
}

void G1CollectedHeap::evacuate_collection_set() {
  set_evacuation_failed(false);

  g1_rem_set()->prepare_for_oops_into_collection_set_do();
  concurrent_g1_refine()->set_use_cache(false);
  int n_workers = (ParallelGCThreads > 0 ? workers()->total_workers() : 1);

  set_par_threads(n_workers);
  G1ParTask g1_par_task(this, n_workers, _task_queues);

  init_for_evac_failure(NULL);

  change_strong_roots_parity();  // In preparation for parallel strong roots.
  rem_set()->prepare_for_younger_refs_iterate(true);
  double start_par = os::elapsedTime();

  if (ParallelGCThreads > 0) {
    // The individual threads will set their evac-failure closures.
    workers()->run_task(&g1_par_task);
  } else {
    g1_par_task.work(0);
  }

  double par_time = (os::elapsedTime() - start_par) * 1000.0;
  g1_policy()->record_par_time(par_time);
  set_par_threads(0);
  // Is this the right thing to do here?  We don't save marks
  // on individual heap regions when we allocate from
  // them in parallel, so this seems like the correct place for this.
  all_alloc_regions_note_end_of_copying();
  {
    G1IsAliveClosure is_alive(this);
    G1KeepAliveClosure keep_alive(this);
    JNIHandles::weak_oops_do(&is_alive, &keep_alive);
  }

  g1_rem_set()->cleanup_after_oops_into_collection_set_do();
  concurrent_g1_refine()->set_use_cache(true);

  finalize_for_evac_failure();

  // Must do this before removing self-forwarding pointers, which clears
  // the per-region evac-failure flags.
  concurrent_mark()->complete_marking_in_collection_set();

  if (evacuation_failed()) {
    remove_self_forwarding_pointers();

    if (PrintGCDetails) {
      gclog_or_tty->print(" (evacuation failed)");
    } else if (PrintGC) {
      gclog_or_tty->print("--");
    }
  }

  COMPILER2_PRESENT(DerivedPointerTable::update_pointers());
}

void G1CollectedHeap::free_region(HeapRegion* hr) {
  size_t pre_used = 0;
  size_t cleared_h_regions = 0;
  size_t freed_regions = 0;
  UncleanRegionList local_list;

  HeapWord* start = hr->bottom();
  HeapWord* end   = hr->prev_top_at_mark_start();
  size_t used_bytes = hr->used();
  size_t live_bytes = hr->max_live_bytes();
  if (used_bytes > 0) {
    guarantee( live_bytes <= used_bytes, "invariant" );
  } else {
    guarantee( live_bytes == 0, "invariant" );
  }

  size_t garbage_bytes = used_bytes - live_bytes;
  if (garbage_bytes > 0)
    g1_policy()->decrease_known_garbage_bytes(garbage_bytes);

  free_region_work(hr, pre_used, cleared_h_regions, freed_regions,
                   &local_list);
  finish_free_region_work(pre_used, cleared_h_regions, freed_regions,
                          &local_list);
}

void
G1CollectedHeap::free_region_work(HeapRegion* hr,
                                  size_t& pre_used,
                                  size_t& cleared_h_regions,
                                  size_t& freed_regions,
                                  UncleanRegionList* list,
                                  bool par) {
  assert(!hr->popular(), "should not free popular regions");
  pre_used += hr->used();
  if (hr->isHumongous()) {
    assert(hr->startsHumongous(),
           "Only the start of a humongous region should be freed.");
    int ind = _hrs->find(hr);
    assert(ind != -1, "Should have an index.");
    // Clear the start region.
    hr->hr_clear(par, true /*clear_space*/);
    list->insert_before_head(hr);
    cleared_h_regions++;
    freed_regions++;
    // Clear any continued regions.
    ind++;
    while ((size_t)ind < n_regions()) {
      HeapRegion* hrc = _hrs->at(ind);
      if (!hrc->continuesHumongous()) break;
      // Otherwise, does continue the H region.
      assert(hrc->humongous_start_region() == hr, "Huh?");
      hrc->hr_clear(par, true /*clear_space*/);
      cleared_h_regions++;
      freed_regions++;
      list->insert_before_head(hrc);
      ind++;
    }
  } else {
    hr->hr_clear(par, true /*clear_space*/);
    list->insert_before_head(hr);
    freed_regions++;
    // If we're using clear2, this should not be enabled.
    // assert(!hr->in_cohort(), "Can't be both free and in a cohort.");
  }
}

void G1CollectedHeap::finish_free_region_work(size_t pre_used,
                                              size_t cleared_h_regions,
                                              size_t freed_regions,
                                              UncleanRegionList* list) {
  if (list != NULL && list->sz() > 0) {
    prepend_region_list_on_unclean_list(list);
  }
  // Acquire a lock, if we're parallel, to update possibly-shared
  // variables.
  Mutex* lock = (n_par_threads() > 0) ? ParGCRareEvent_lock : NULL;
  {
    MutexLockerEx x(lock, Mutex::_no_safepoint_check_flag);
    _summary_bytes_used -= pre_used;
    _num_humongous_regions -= (int) cleared_h_regions;
    _free_regions += freed_regions;
  }
}


void G1CollectedHeap::dirtyCardsForYoungRegions(CardTableModRefBS* ct_bs, HeapRegion* list) {
  while (list != NULL) {
    guarantee( list->is_young(), "invariant" );

    HeapWord* bottom = list->bottom();
    HeapWord* end = list->end();
    MemRegion mr(bottom, end);
    ct_bs->dirty(mr);

    list = list->get_next_young_region();
  }
}

void G1CollectedHeap::cleanUpCardTable() {
  CardTableModRefBS* ct_bs = (CardTableModRefBS*) (barrier_set());
  double start = os::elapsedTime();

  ct_bs->clear(_g1_committed);

  // now, redirty the cards of the scan-only and survivor regions
  // (it seemed faster to do it this way, instead of iterating over
  // all regions and then clearing / dirtying as approprite)
  dirtyCardsForYoungRegions(ct_bs, _young_list->first_scan_only_region());
  dirtyCardsForYoungRegions(ct_bs, _young_list->first_survivor_region());

  double elapsed = os::elapsedTime() - start;
  g1_policy()->record_clear_ct_time( elapsed * 1000.0);
}


void G1CollectedHeap::do_collection_pause_if_appropriate(size_t word_size) {
  // First do any popular regions.
  HeapRegion* hr;
  while ((hr = popular_region_to_evac()) != NULL) {
    evac_popular_region(hr);
  }
  // Now do heuristic pauses.
  if (g1_policy()->should_do_collection_pause(word_size)) {
    do_collection_pause();
  }
}

void G1CollectedHeap::free_collection_set(HeapRegion* cs_head) {
  double young_time_ms     = 0.0;
  double non_young_time_ms = 0.0;

  G1CollectorPolicy* policy = g1_policy();

  double start_sec = os::elapsedTime();
  bool non_young = true;

  HeapRegion* cur = cs_head;
  int age_bound = -1;
  size_t rs_lengths = 0;

  while (cur != NULL) {
    if (non_young) {
      if (cur->is_young()) {
        double end_sec = os::elapsedTime();
        double elapsed_ms = (end_sec - start_sec) * 1000.0;
        non_young_time_ms += elapsed_ms;

        start_sec = os::elapsedTime();
        non_young = false;
      }
    } else {
      if (!cur->is_on_free_list()) {
        double end_sec = os::elapsedTime();
        double elapsed_ms = (end_sec - start_sec) * 1000.0;
        young_time_ms += elapsed_ms;

        start_sec = os::elapsedTime();
        non_young = true;
      }
    }

    rs_lengths += cur->rem_set()->occupied();

    HeapRegion* next = cur->next_in_collection_set();
    assert(cur->in_collection_set(), "bad CS");
    cur->set_next_in_collection_set(NULL);
    cur->set_in_collection_set(false);

    if (cur->is_young()) {
      int index = cur->young_index_in_cset();
      guarantee( index != -1, "invariant" );
      guarantee( (size_t)index < policy->young_cset_length(), "invariant" );
      size_t words_survived = _surviving_young_words[index];
      cur->record_surv_words_in_group(words_survived);
    } else {
      int index = cur->young_index_in_cset();
      guarantee( index == -1, "invariant" );
    }

    assert( (cur->is_young() && cur->young_index_in_cset() > -1) ||
            (!cur->is_young() && cur->young_index_in_cset() == -1),
            "invariant" );

    if (!cur->evacuation_failed()) {
      // And the region is empty.
      assert(!cur->is_empty(),
             "Should not have empty regions in a CS.");
      free_region(cur);
    } else {
      guarantee( !cur->is_scan_only(), "should not be scan only" );
      cur->uninstall_surv_rate_group();
      if (cur->is_young())
        cur->set_young_index_in_cset(-1);
      cur->set_not_young();
      cur->set_evacuation_failed(false);
    }
    cur = next;
  }

  policy->record_max_rs_lengths(rs_lengths);
  policy->cset_regions_freed();

  double end_sec = os::elapsedTime();
  double elapsed_ms = (end_sec - start_sec) * 1000.0;
  if (non_young)
    non_young_time_ms += elapsed_ms;
  else
    young_time_ms += elapsed_ms;

  policy->record_young_free_cset_time_ms(young_time_ms);
  policy->record_non_young_free_cset_time_ms(non_young_time_ms);
}

HeapRegion*
G1CollectedHeap::alloc_region_from_unclean_list_locked(bool zero_filled) {
  assert(ZF_mon->owned_by_self(), "Precondition");
  HeapRegion* res = pop_unclean_region_list_locked();
  if (res != NULL) {
    assert(!res->continuesHumongous() &&
           res->zero_fill_state() != HeapRegion::Allocated,
           "Only free regions on unclean list.");
    if (zero_filled) {
      res->ensure_zero_filled_locked();
      res->set_zero_fill_allocated();
    }
  }
  return res;
}

HeapRegion* G1CollectedHeap::alloc_region_from_unclean_list(bool zero_filled) {
  MutexLockerEx zx(ZF_mon, Mutex::_no_safepoint_check_flag);
  return alloc_region_from_unclean_list_locked(zero_filled);
}

void G1CollectedHeap::put_region_on_unclean_list(HeapRegion* r) {
  MutexLockerEx x(ZF_mon, Mutex::_no_safepoint_check_flag);
  put_region_on_unclean_list_locked(r);
  if (should_zf()) ZF_mon->notify_all(); // Wake up ZF thread.
}

void G1CollectedHeap::set_unclean_regions_coming(bool b) {
  MutexLockerEx x(Cleanup_mon);
  set_unclean_regions_coming_locked(b);
}

void G1CollectedHeap::set_unclean_regions_coming_locked(bool b) {
  assert(Cleanup_mon->owned_by_self(), "Precondition");
  _unclean_regions_coming = b;
  // Wake up mutator threads that might be waiting for completeCleanup to
  // finish.
  if (!b) Cleanup_mon->notify_all();
}

void G1CollectedHeap::wait_for_cleanup_complete() {
  MutexLockerEx x(Cleanup_mon);
  wait_for_cleanup_complete_locked();
}

void G1CollectedHeap::wait_for_cleanup_complete_locked() {
  assert(Cleanup_mon->owned_by_self(), "precondition");
  while (_unclean_regions_coming) {
    Cleanup_mon->wait();
  }
}

void
G1CollectedHeap::put_region_on_unclean_list_locked(HeapRegion* r) {
  assert(ZF_mon->owned_by_self(), "precondition.");
  _unclean_region_list.insert_before_head(r);
}

void
G1CollectedHeap::prepend_region_list_on_unclean_list(UncleanRegionList* list) {
  MutexLockerEx x(ZF_mon, Mutex::_no_safepoint_check_flag);
  prepend_region_list_on_unclean_list_locked(list);
  if (should_zf()) ZF_mon->notify_all(); // Wake up ZF thread.
}

void
G1CollectedHeap::
prepend_region_list_on_unclean_list_locked(UncleanRegionList* list) {
  assert(ZF_mon->owned_by_self(), "precondition.");
  _unclean_region_list.prepend_list(list);
}

HeapRegion* G1CollectedHeap::pop_unclean_region_list_locked() {
  assert(ZF_mon->owned_by_self(), "precondition.");
  HeapRegion* res = _unclean_region_list.pop();
  if (res != NULL) {
    // Inform ZF thread that there's a new unclean head.
    if (_unclean_region_list.hd() != NULL && should_zf())
      ZF_mon->notify_all();
  }
  return res;
}

HeapRegion* G1CollectedHeap::peek_unclean_region_list_locked() {
  assert(ZF_mon->owned_by_self(), "precondition.");
  return _unclean_region_list.hd();
}


bool G1CollectedHeap::move_cleaned_region_to_free_list_locked() {
  assert(ZF_mon->owned_by_self(), "Precondition");
  HeapRegion* r = peek_unclean_region_list_locked();
  if (r != NULL && r->zero_fill_state() == HeapRegion::ZeroFilled) {
    // Result of below must be equal to "r", since we hold the lock.
    (void)pop_unclean_region_list_locked();
    put_free_region_on_list_locked(r);
    return true;
  } else {
    return false;
  }
}

bool G1CollectedHeap::move_cleaned_region_to_free_list() {
  MutexLockerEx x(ZF_mon, Mutex::_no_safepoint_check_flag);
  return move_cleaned_region_to_free_list_locked();
}


void G1CollectedHeap::put_free_region_on_list_locked(HeapRegion* r) {
  assert(ZF_mon->owned_by_self(), "precondition.");
  assert(_free_region_list_size == free_region_list_length(), "Inv");
  assert(r->zero_fill_state() == HeapRegion::ZeroFilled,
        "Regions on free list must be zero filled");
  assert(!r->isHumongous(), "Must not be humongous.");
  assert(r->is_empty(), "Better be empty");
  assert(!r->is_on_free_list(),
         "Better not already be on free list");
  assert(!r->is_on_unclean_list(),
         "Better not already be on unclean list");
  r->set_on_free_list(true);
  r->set_next_on_free_list(_free_region_list);
  _free_region_list = r;
  _free_region_list_size++;
  assert(_free_region_list_size == free_region_list_length(), "Inv");
}

void G1CollectedHeap::put_free_region_on_list(HeapRegion* r) {
  MutexLockerEx x(ZF_mon, Mutex::_no_safepoint_check_flag);
  put_free_region_on_list_locked(r);
}

HeapRegion* G1CollectedHeap::pop_free_region_list_locked() {
  assert(ZF_mon->owned_by_self(), "precondition.");
  assert(_free_region_list_size == free_region_list_length(), "Inv");
  HeapRegion* res = _free_region_list;
  if (res != NULL) {
    _free_region_list = res->next_from_free_list();
    _free_region_list_size--;
    res->set_on_free_list(false);
    res->set_next_on_free_list(NULL);
    assert(_free_region_list_size == free_region_list_length(), "Inv");
  }
  return res;
}


HeapRegion* G1CollectedHeap::alloc_free_region_from_lists(bool zero_filled) {
  // By self, or on behalf of self.
  assert(Heap_lock->is_locked(), "Precondition");
  HeapRegion* res = NULL;
  bool first = true;
  while (res == NULL) {
    if (zero_filled || !first) {
      MutexLockerEx x(ZF_mon, Mutex::_no_safepoint_check_flag);
      res = pop_free_region_list_locked();
      if (res != NULL) {
        assert(!res->zero_fill_is_allocated(),
               "No allocated regions on free list.");
        res->set_zero_fill_allocated();
      } else if (!first) {
        break;  // We tried both, time to return NULL.
      }
    }

    if (res == NULL) {
      res = alloc_region_from_unclean_list(zero_filled);
    }
    assert(res == NULL ||
           !zero_filled ||
           res->zero_fill_is_allocated(),
           "We must have allocated the region we're returning");
    first = false;
  }
  return res;
}

void G1CollectedHeap::remove_allocated_regions_from_lists() {
  MutexLockerEx x(ZF_mon, Mutex::_no_safepoint_check_flag);
  {
    HeapRegion* prev = NULL;
    HeapRegion* cur = _unclean_region_list.hd();
    while (cur != NULL) {
      HeapRegion* next = cur->next_from_unclean_list();
      if (cur->zero_fill_is_allocated()) {
        // Remove from the list.
        if (prev == NULL) {
          (void)_unclean_region_list.pop();
        } else {
          _unclean_region_list.delete_after(prev);
        }
        cur->set_on_unclean_list(false);
        cur->set_next_on_unclean_list(NULL);
      } else {
        prev = cur;
      }
      cur = next;
    }
    assert(_unclean_region_list.sz() == unclean_region_list_length(),
           "Inv");
  }

  {
    HeapRegion* prev = NULL;
    HeapRegion* cur = _free_region_list;
    while (cur != NULL) {
      HeapRegion* next = cur->next_from_free_list();
      if (cur->zero_fill_is_allocated()) {
        // Remove from the list.
        if (prev == NULL) {
          _free_region_list = cur->next_from_free_list();
        } else {
          prev->set_next_on_free_list(cur->next_from_free_list());
        }
        cur->set_on_free_list(false);
        cur->set_next_on_free_list(NULL);
        _free_region_list_size--;
      } else {
        prev = cur;
      }
      cur = next;
    }
    assert(_free_region_list_size == free_region_list_length(), "Inv");
  }
}

bool G1CollectedHeap::verify_region_lists() {
  MutexLockerEx x(ZF_mon, Mutex::_no_safepoint_check_flag);
  return verify_region_lists_locked();
}

bool G1CollectedHeap::verify_region_lists_locked() {
  HeapRegion* unclean = _unclean_region_list.hd();
  while (unclean != NULL) {
    guarantee(unclean->is_on_unclean_list(), "Well, it is!");
    guarantee(!unclean->is_on_free_list(), "Well, it shouldn't be!");
    guarantee(unclean->zero_fill_state() != HeapRegion::Allocated,
              "Everything else is possible.");
    unclean = unclean->next_from_unclean_list();
  }
  guarantee(_unclean_region_list.sz() == unclean_region_list_length(), "Inv");

  HeapRegion* free_r = _free_region_list;
  while (free_r != NULL) {
    assert(free_r->is_on_free_list(), "Well, it is!");
    assert(!free_r->is_on_unclean_list(), "Well, it shouldn't be!");
    switch (free_r->zero_fill_state()) {
    case HeapRegion::NotZeroFilled:
    case HeapRegion::ZeroFilling:
      guarantee(false, "Should not be on free list.");
      break;
    default:
      // Everything else is possible.
      break;
    }
    free_r = free_r->next_from_free_list();
  }
  guarantee(_free_region_list_size == free_region_list_length(), "Inv");
  // If we didn't do an assertion...
  return true;
}

size_t G1CollectedHeap::free_region_list_length() {
  assert(ZF_mon->owned_by_self(), "precondition.");
  size_t len = 0;
  HeapRegion* cur = _free_region_list;
  while (cur != NULL) {
    len++;
    cur = cur->next_from_free_list();
  }
  return len;
}

size_t G1CollectedHeap::unclean_region_list_length() {
  assert(ZF_mon->owned_by_self(), "precondition.");
  return _unclean_region_list.length();
}

size_t G1CollectedHeap::n_regions() {
  return _hrs->length();
}

size_t G1CollectedHeap::max_regions() {
  return
    (size_t)align_size_up(g1_reserved_obj_bytes(), HeapRegion::GrainBytes) /
    HeapRegion::GrainBytes;
}

size_t G1CollectedHeap::free_regions() {
  /* Possibly-expensive assert.
  assert(_free_regions == count_free_regions(),
         "_free_regions is off.");
  */
  return _free_regions;
}

bool G1CollectedHeap::should_zf() {
  return _free_region_list_size < (size_t) G1ConcZFMaxRegions;
}

class RegionCounter: public HeapRegionClosure {
  size_t _n;
public:
  RegionCounter() : _n(0) {}
  bool doHeapRegion(HeapRegion* r) {
    if (r->is_empty() && !r->popular()) {
      assert(!r->isHumongous(), "H regions should not be empty.");
      _n++;
    }
    return false;
  }
  int res() { return (int) _n; }
};

size_t G1CollectedHeap::count_free_regions() {
  RegionCounter rc;
  heap_region_iterate(&rc);
  size_t n = rc.res();
  if (_cur_alloc_region != NULL && _cur_alloc_region->is_empty())
    n--;
  return n;
}

size_t G1CollectedHeap::count_free_regions_list() {
  size_t n = 0;
  size_t o = 0;
  ZF_mon->lock_without_safepoint_check();
  HeapRegion* cur = _free_region_list;
  while (cur != NULL) {
    cur = cur->next_from_free_list();
    n++;
  }
  size_t m = unclean_region_list_length();
  ZF_mon->unlock();
  return n + m;
}

bool G1CollectedHeap::should_set_young_locked() {
  assert(heap_lock_held_for_gc(),
              "the heap lock should already be held by or for this thread");
  return  (g1_policy()->in_young_gc_mode() &&
           g1_policy()->should_add_next_region_to_young_list());
}

void G1CollectedHeap::set_region_short_lived_locked(HeapRegion* hr) {
  assert(heap_lock_held_for_gc(),
              "the heap lock should already be held by or for this thread");
  _young_list->push_region(hr);
  g1_policy()->set_region_short_lived(hr);
}

class NoYoungRegionsClosure: public HeapRegionClosure {
private:
  bool _success;
public:
  NoYoungRegionsClosure() : _success(true) { }
  bool doHeapRegion(HeapRegion* r) {
    if (r->is_young()) {
      gclog_or_tty->print_cr("Region ["PTR_FORMAT", "PTR_FORMAT") tagged as young",
                             r->bottom(), r->end());
      _success = false;
    }
    return false;
  }
  bool success() { return _success; }
};

bool G1CollectedHeap::check_young_list_empty(bool ignore_scan_only_list,
                                             bool check_sample) {
  bool ret = true;

  ret = _young_list->check_list_empty(ignore_scan_only_list, check_sample);
  if (!ignore_scan_only_list) {
    NoYoungRegionsClosure closure;
    heap_region_iterate(&closure);
    ret = ret && closure.success();
  }

  return ret;
}

void G1CollectedHeap::empty_young_list() {
  assert(heap_lock_held_for_gc(),
              "the heap lock should already be held by or for this thread");
  assert(g1_policy()->in_young_gc_mode(), "should be in young GC mode");

  _young_list->empty_list();
}

bool G1CollectedHeap::all_alloc_regions_no_allocs_since_save_marks() {
  bool no_allocs = true;
  for (int ap = 0; ap < GCAllocPurposeCount && no_allocs; ++ap) {
    HeapRegion* r = _gc_alloc_regions[ap];
    no_allocs = r == NULL || r->saved_mark_at_top();
  }
  return no_allocs;
}

void G1CollectedHeap::all_alloc_regions_note_end_of_copying() {
  for (int ap = 0; ap < GCAllocPurposeCount; ++ap) {
    HeapRegion* r = _gc_alloc_regions[ap];
    if (r != NULL) {
      // Check for aliases.
      bool has_processed_alias = false;
      for (int i = 0; i < ap; ++i) {
        if (_gc_alloc_regions[i] == r) {
          has_processed_alias = true;
          break;
        }
      }
      if (!has_processed_alias) {
        r->note_end_of_copying();
        g1_policy()->record_after_bytes(r->used());
      }
    }
  }
}


// Done at the start of full GC.
void G1CollectedHeap::tear_down_region_lists() {
  MutexLockerEx x(ZF_mon, Mutex::_no_safepoint_check_flag);
  while (pop_unclean_region_list_locked() != NULL) ;
  assert(_unclean_region_list.hd() == NULL && _unclean_region_list.sz() == 0,
         "Postconditions of loop.")
  while (pop_free_region_list_locked() != NULL) ;
  assert(_free_region_list == NULL, "Postcondition of loop.");
  if (_free_region_list_size != 0) {
    gclog_or_tty->print_cr("Size is %d.", _free_region_list_size);
    print();
  }
  assert(_free_region_list_size == 0, "Postconditions of loop.");
}


class RegionResetter: public HeapRegionClosure {
  G1CollectedHeap* _g1;
  int _n;
public:
  RegionResetter() : _g1(G1CollectedHeap::heap()), _n(0) {}
  bool doHeapRegion(HeapRegion* r) {
    if (r->continuesHumongous()) return false;
    if (r->top() > r->bottom()) {
      if (r->top() < r->end()) {
        Copy::fill_to_words(r->top(),
                          pointer_delta(r->end(), r->top()));
      }
      r->set_zero_fill_allocated();
    } else {
      assert(r->is_empty(), "tautology");
      if (r->popular()) {
        if (r->zero_fill_state() != HeapRegion::Allocated) {
          r->ensure_zero_filled_locked();
          r->set_zero_fill_allocated();
        }
      } else {
        _n++;
        switch (r->zero_fill_state()) {
        case HeapRegion::NotZeroFilled:
        case HeapRegion::ZeroFilling:
          _g1->put_region_on_unclean_list_locked(r);
          break;
        case HeapRegion::Allocated:
          r->set_zero_fill_complete();
          // no break; go on to put on free list.
        case HeapRegion::ZeroFilled:
          _g1->put_free_region_on_list_locked(r);
          break;
        }
      }
    }
    return false;
  }

  int getFreeRegionCount() {return _n;}
};

// Done at the end of full GC.
void G1CollectedHeap::rebuild_region_lists() {
  MutexLockerEx x(ZF_mon, Mutex::_no_safepoint_check_flag);
  // This needs to go at the end of the full GC.
  RegionResetter rs;
  heap_region_iterate(&rs);
  _free_regions = rs.getFreeRegionCount();
  // Tell the ZF thread it may have work to do.
  if (should_zf()) ZF_mon->notify_all();
}

class UsedRegionsNeedZeroFillSetter: public HeapRegionClosure {
  G1CollectedHeap* _g1;
  int _n;
public:
  UsedRegionsNeedZeroFillSetter() : _g1(G1CollectedHeap::heap()), _n(0) {}
  bool doHeapRegion(HeapRegion* r) {
    if (r->continuesHumongous()) return false;
    if (r->top() > r->bottom()) {
      // There are assertions in "set_zero_fill_needed()" below that
      // require top() == bottom(), so this is technically illegal.
      // We'll skirt the law here, by making that true temporarily.
      DEBUG_ONLY(HeapWord* save_top = r->top();
                 r->set_top(r->bottom()));
      r->set_zero_fill_needed();
      DEBUG_ONLY(r->set_top(save_top));
    }
    return false;
  }
};

// Done at the start of full GC.
void G1CollectedHeap::set_used_regions_to_need_zero_fill() {
  MutexLockerEx x(ZF_mon, Mutex::_no_safepoint_check_flag);
  // This needs to go at the end of the full GC.
  UsedRegionsNeedZeroFillSetter rs;
  heap_region_iterate(&rs);
}

class CountObjClosure: public ObjectClosure {
  size_t _n;
public:
  CountObjClosure() : _n(0) {}
  void do_object(oop obj) { _n++; }
  size_t n() { return _n; }
};

size_t G1CollectedHeap::pop_object_used_objs() {
  size_t sum_objs = 0;
  for (int i = 0; i < G1NumPopularRegions; i++) {
    CountObjClosure cl;
    _hrs->at(i)->object_iterate(&cl);
    sum_objs += cl.n();
  }
  return sum_objs;
}

size_t G1CollectedHeap::pop_object_used_bytes() {
  size_t sum_bytes = 0;
  for (int i = 0; i < G1NumPopularRegions; i++) {
    sum_bytes += _hrs->at(i)->used();
  }
  return sum_bytes;
}


static int nq = 0;

HeapWord* G1CollectedHeap::allocate_popular_object(size_t word_size) {
  while (_cur_pop_hr_index < G1NumPopularRegions) {
    HeapRegion* cur_pop_region = _hrs->at(_cur_pop_hr_index);
    HeapWord* res = cur_pop_region->allocate(word_size);
    if (res != NULL) {
      // We account for popular objs directly in the used summary:
      _summary_bytes_used += (word_size * HeapWordSize);
      return res;
    }
    // Otherwise, try the next region (first making sure that we remember
    // the last "top" value as the "next_top_at_mark_start", so that
    // objects made popular during markings aren't automatically considered
    // live).
    cur_pop_region->note_end_of_copying();
    // Otherwise, try the next region.
    _cur_pop_hr_index++;
  }
  // XXX: For now !!!
  vm_exit_out_of_memory(word_size,
                        "Not enough pop obj space (To Be Fixed)");
  return NULL;
}

class HeapRegionList: public CHeapObj {
  public:
  HeapRegion* hr;
  HeapRegionList* next;
};

void G1CollectedHeap::schedule_popular_region_evac(HeapRegion* r) {
  // This might happen during parallel GC, so protect by this lock.
  MutexLockerEx x(ParGCRareEvent_lock, Mutex::_no_safepoint_check_flag);
  // We don't schedule regions whose evacuations are already pending, or
  // are already being evacuated.
  if (!r->popular_pending() && !r->in_collection_set()) {
    r->set_popular_pending(true);
    if (G1TracePopularity) {
      gclog_or_tty->print_cr("Scheduling region "PTR_FORMAT" "
                             "["PTR_FORMAT", "PTR_FORMAT") for pop-object evacuation.",
                             r, r->bottom(), r->end());
    }
    HeapRegionList* hrl = new HeapRegionList;
    hrl->hr = r;
    hrl->next = _popular_regions_to_be_evacuated;
    _popular_regions_to_be_evacuated = hrl;
  }
}

HeapRegion* G1CollectedHeap::popular_region_to_evac() {
  MutexLockerEx x(ParGCRareEvent_lock, Mutex::_no_safepoint_check_flag);
  HeapRegion* res = NULL;
  while (_popular_regions_to_be_evacuated != NULL && res == NULL) {
    HeapRegionList* hrl = _popular_regions_to_be_evacuated;
    _popular_regions_to_be_evacuated = hrl->next;
    res = hrl->hr;
    // The G1RSPopLimit may have increased, so recheck here...
    if (res->rem_set()->occupied() < (size_t) G1RSPopLimit) {
      // Hah: don't need to schedule.
      if (G1TracePopularity) {
        gclog_or_tty->print_cr("Unscheduling region "PTR_FORMAT" "
                               "["PTR_FORMAT", "PTR_FORMAT") "
                               "for pop-object evacuation (size %d < limit %d)",
                               res, res->bottom(), res->end(),
                               res->rem_set()->occupied(), G1RSPopLimit);
      }
      res->set_popular_pending(false);
      res = NULL;
    }
    // We do not reset res->popular() here; if we did so, it would allow
    // the region to be "rescheduled" for popularity evacuation.  Instead,
    // this is done in the collection pause, with the world stopped.
    // So the invariant is that the regions in the list have the popularity
    // boolean set, but having the boolean set does not imply membership
    // on the list (though there can at most one such pop-pending region
    // not on the list at any time).
    delete hrl;
  }
  return res;
}

void G1CollectedHeap::evac_popular_region(HeapRegion* hr) {
  while (true) {
    // Don't want to do a GC pause while cleanup is being completed!
    wait_for_cleanup_complete();

    // Read the GC count while holding the Heap_lock
    int gc_count_before = SharedHeap::heap()->total_collections();
    g1_policy()->record_stop_world_start();

    {
      MutexUnlocker mu(Heap_lock);  // give up heap lock, execute gets it back
      VM_G1PopRegionCollectionPause op(gc_count_before, hr);
      VMThread::execute(&op);

      // If the prolog succeeded, we didn't do a GC for this.
      if (op.prologue_succeeded()) break;
    }
    // Otherwise we didn't.  We should recheck the size, though, since
    // the limit may have increased...
    if (hr->rem_set()->occupied() < (size_t) G1RSPopLimit) {
      hr->set_popular_pending(false);
      break;
    }
  }
}

void G1CollectedHeap::atomic_inc_obj_rc(oop obj) {
  Atomic::inc(obj_rc_addr(obj));
}

class CountRCClosure: public OopsInHeapRegionClosure {
  G1CollectedHeap* _g1h;
  bool _parallel;
public:
  CountRCClosure(G1CollectedHeap* g1h) :
    _g1h(g1h), _parallel(ParallelGCThreads > 0)
  {}
  void do_oop(narrowOop* p) {
    guarantee(false, "NYI");
  }
  void do_oop(oop* p) {
    oop obj = *p;
    assert(obj != NULL, "Precondition.");
    if (_parallel) {
      // We go sticky at the limit to avoid excess contention.
      // If we want to track the actual RC's further, we'll need to keep a
      // per-thread hash table or something for the popular objects.
      if (_g1h->obj_rc(obj) < G1ObjPopLimit) {
        _g1h->atomic_inc_obj_rc(obj);
      }
    } else {
      _g1h->inc_obj_rc(obj);
    }
  }
};

class EvacPopObjClosure: public ObjectClosure {
  G1CollectedHeap* _g1h;
  size_t _pop_objs;
  size_t _max_rc;
public:
  EvacPopObjClosure(G1CollectedHeap* g1h) :
    _g1h(g1h), _pop_objs(0), _max_rc(0) {}

  void do_object(oop obj) {
    size_t rc = _g1h->obj_rc(obj);
    _max_rc = MAX2(rc, _max_rc);
    if (rc >= (size_t) G1ObjPopLimit) {
      _g1h->_pop_obj_rc_at_copy.add((double)rc);
      size_t word_sz = obj->size();
      HeapWord* new_pop_loc = _g1h->allocate_popular_object(word_sz);
      oop new_pop_obj = (oop)new_pop_loc;
      Copy::aligned_disjoint_words((HeapWord*)obj, new_pop_loc, word_sz);
      obj->forward_to(new_pop_obj);
      G1ScanAndBalanceClosure scan_and_balance(_g1h);
      new_pop_obj->oop_iterate_backwards(&scan_and_balance);
      // preserve "next" mark bit if marking is in progress.
      if (_g1h->mark_in_progress() && !_g1h->is_obj_ill(obj)) {
        _g1h->concurrent_mark()->markAndGrayObjectIfNecessary(new_pop_obj);
      }

      if (G1TracePopularity) {
        gclog_or_tty->print_cr("Found obj " PTR_FORMAT " of word size " SIZE_FORMAT
                               " pop (%d), move to " PTR_FORMAT,
                               (void*) obj, word_sz,
                               _g1h->obj_rc(obj), (void*) new_pop_obj);
      }
      _pop_objs++;
    }
  }
  size_t pop_objs() { return _pop_objs; }
  size_t max_rc() { return _max_rc; }
};

class G1ParCountRCTask : public AbstractGangTask {
  G1CollectedHeap* _g1h;
  BitMap _bm;

  size_t getNCards() {
    return (_g1h->capacity() + G1BlockOffsetSharedArray::N_bytes - 1)
      / G1BlockOffsetSharedArray::N_bytes;
  }
  CountRCClosure _count_rc_closure;
public:
  G1ParCountRCTask(G1CollectedHeap* g1h) :
    AbstractGangTask("G1 Par RC Count task"),
    _g1h(g1h), _bm(getNCards()), _count_rc_closure(g1h)
  {}

  void work(int i) {
    ResourceMark rm;
    HandleMark   hm;
    _g1h->g1_rem_set()->oops_into_collection_set_do(&_count_rc_closure, i);
  }
};

void G1CollectedHeap::popularity_pause_preamble(HeapRegion* popular_region) {
  // We're evacuating a single region (for popularity).
  if (G1TracePopularity) {
    gclog_or_tty->print_cr("Doing pop region pause for ["PTR_FORMAT", "PTR_FORMAT")",
                           popular_region->bottom(), popular_region->end());
  }
  g1_policy()->set_single_region_collection_set(popular_region);
  size_t max_rc;
  if (!compute_reference_counts_and_evac_popular(popular_region,
                                                 &max_rc)) {
    // We didn't evacuate any popular objects.
    // We increase the RS popularity limit, to prevent this from
    // happening in the future.
    if (G1RSPopLimit < (1 << 30)) {
      G1RSPopLimit *= 2;
    }
    // For now, interesting enough for a message:
#if 1
    gclog_or_tty->print_cr("In pop region pause for ["PTR_FORMAT", "PTR_FORMAT"), "
                           "failed to find a pop object (max = %d).",
                           popular_region->bottom(), popular_region->end(),
                           max_rc);
    gclog_or_tty->print_cr("Increased G1RSPopLimit to %d.", G1RSPopLimit);
#endif // 0
    // Also, we reset the collection set to NULL, to make the rest of
    // the collection do nothing.
    assert(popular_region->next_in_collection_set() == NULL,
           "should be single-region.");
    popular_region->set_in_collection_set(false);
    popular_region->set_popular_pending(false);
    g1_policy()->clear_collection_set();
  }
}

bool G1CollectedHeap::
compute_reference_counts_and_evac_popular(HeapRegion* popular_region,
                                          size_t* max_rc) {
  HeapWord* rc_region_bot;
  HeapWord* rc_region_end;

  // Set up the reference count region.
  HeapRegion* rc_region = newAllocRegion(HeapRegion::GrainWords);
  if (rc_region != NULL) {
    rc_region_bot = rc_region->bottom();
    rc_region_end = rc_region->end();
  } else {
    rc_region_bot = NEW_C_HEAP_ARRAY(HeapWord, HeapRegion::GrainWords);
    if (rc_region_bot == NULL) {
      vm_exit_out_of_memory(HeapRegion::GrainWords,
                            "No space for RC region.");
    }
    rc_region_end = rc_region_bot + HeapRegion::GrainWords;
  }

  if (G1TracePopularity)
    gclog_or_tty->print_cr("RC region is ["PTR_FORMAT", "PTR_FORMAT")",
                           rc_region_bot, rc_region_end);
  if (rc_region_bot > popular_region->bottom()) {
    _rc_region_above = true;
    _rc_region_diff =
      pointer_delta(rc_region_bot, popular_region->bottom(), 1);
  } else {
    assert(rc_region_bot < popular_region->bottom(), "Can't be equal.");
    _rc_region_above = false;
    _rc_region_diff =
      pointer_delta(popular_region->bottom(), rc_region_bot, 1);
  }
  g1_policy()->record_pop_compute_rc_start();
  // Count external references.
  g1_rem_set()->prepare_for_oops_into_collection_set_do();
  if (ParallelGCThreads > 0) {

    set_par_threads(workers()->total_workers());
    G1ParCountRCTask par_count_rc_task(this);
    workers()->run_task(&par_count_rc_task);
    set_par_threads(0);

  } else {
    CountRCClosure count_rc_closure(this);
    g1_rem_set()->oops_into_collection_set_do(&count_rc_closure, 0);
  }
  g1_rem_set()->cleanup_after_oops_into_collection_set_do();
  g1_policy()->record_pop_compute_rc_end();

  // Now evacuate popular objects.
  g1_policy()->record_pop_evac_start();
  EvacPopObjClosure evac_pop_obj_cl(this);
  popular_region->object_iterate(&evac_pop_obj_cl);
  *max_rc = evac_pop_obj_cl.max_rc();

  // Make sure the last "top" value of the current popular region is copied
  // as the "next_top_at_mark_start", so that objects made popular during
  // markings aren't automatically considered live.
  HeapRegion* cur_pop_region = _hrs->at(_cur_pop_hr_index);
  cur_pop_region->note_end_of_copying();

  if (rc_region != NULL) {
    free_region(rc_region);
  } else {
    FREE_C_HEAP_ARRAY(HeapWord, rc_region_bot);
  }
  g1_policy()->record_pop_evac_end();

  return evac_pop_obj_cl.pop_objs() > 0;
}

class CountPopObjInfoClosure: public HeapRegionClosure {
  size_t _objs;
  size_t _bytes;

  class CountObjClosure: public ObjectClosure {
    int _n;
  public:
    CountObjClosure() : _n(0) {}
    void do_object(oop obj) { _n++; }
    size_t n() { return _n; }
  };

public:
  CountPopObjInfoClosure() : _objs(0), _bytes(0) {}
  bool doHeapRegion(HeapRegion* r) {
    _bytes += r->used();
    CountObjClosure blk;
    r->object_iterate(&blk);
    _objs += blk.n();
    return false;
  }
  size_t objs() { return _objs; }
  size_t bytes() { return _bytes; }
};


void G1CollectedHeap::print_popularity_summary_info() const {
  CountPopObjInfoClosure blk;
  for (int i = 0; i <= _cur_pop_hr_index; i++) {
    blk.doHeapRegion(_hrs->at(i));
  }
  gclog_or_tty->print_cr("\nPopular objects: %d objs, %d bytes.",
                         blk.objs(), blk.bytes());
  gclog_or_tty->print_cr("   RC at copy = [avg = %5.2f, max = %5.2f, sd = %5.2f].",
                _pop_obj_rc_at_copy.avg(),
                _pop_obj_rc_at_copy.maximum(),
                _pop_obj_rc_at_copy.sd());
}

void G1CollectedHeap::set_refine_cte_cl_concurrency(bool concurrent) {
  _refine_cte_cl->set_concurrent(concurrent);
}

#ifndef PRODUCT

class PrintHeapRegionClosure: public HeapRegionClosure {
public:
  bool doHeapRegion(HeapRegion *r) {
    gclog_or_tty->print("Region: "PTR_FORMAT":", r);
    if (r != NULL) {
      if (r->is_on_free_list())
        gclog_or_tty->print("Free ");
      if (r->is_young())
        gclog_or_tty->print("Young ");
      if (r->isHumongous())
        gclog_or_tty->print("Is Humongous ");
      r->print();
    }
    return false;
  }
};

class SortHeapRegionClosure : public HeapRegionClosure {
  size_t young_regions,free_regions, unclean_regions;
  size_t hum_regions, count;
  size_t unaccounted, cur_unclean, cur_alloc;
  size_t total_free;
  HeapRegion* cur;
public:
  SortHeapRegionClosure(HeapRegion *_cur) : cur(_cur), young_regions(0),
    free_regions(0), unclean_regions(0),
    hum_regions(0),
    count(0), unaccounted(0),
    cur_alloc(0), total_free(0)
  {}
  bool doHeapRegion(HeapRegion *r) {
    count++;
    if (r->is_on_free_list()) free_regions++;
    else if (r->is_on_unclean_list()) unclean_regions++;
    else if (r->isHumongous())  hum_regions++;
    else if (r->is_young()) young_regions++;
    else if (r == cur) cur_alloc++;
    else unaccounted++;
    return false;
  }
  void print() {
    total_free = free_regions + unclean_regions;
    gclog_or_tty->print("%d regions\n", count);
    gclog_or_tty->print("%d free: free_list = %d unclean = %d\n",
                        total_free, free_regions, unclean_regions);
    gclog_or_tty->print("%d humongous %d young\n",
                        hum_regions, young_regions);
    gclog_or_tty->print("%d cur_alloc\n", cur_alloc);
    gclog_or_tty->print("UHOH unaccounted = %d\n", unaccounted);
  }
};

void G1CollectedHeap::print_region_counts() {
  SortHeapRegionClosure sc(_cur_alloc_region);
  PrintHeapRegionClosure cl;
  heap_region_iterate(&cl);
  heap_region_iterate(&sc);
  sc.print();
  print_region_accounting_info();
};

bool G1CollectedHeap::regions_accounted_for() {
  // TODO: regions accounting for young/survivor/tenured
  return true;
}

bool G1CollectedHeap::print_region_accounting_info() {
  gclog_or_tty->print_cr("P regions: %d.", G1NumPopularRegions);
  gclog_or_tty->print_cr("Free regions: %d (count: %d count list %d) (clean: %d unclean: %d).",
                         free_regions(),
                         count_free_regions(), count_free_regions_list(),
                         _free_region_list_size, _unclean_region_list.sz());
  gclog_or_tty->print_cr("cur_alloc: %d.",
                         (_cur_alloc_region == NULL ? 0 : 1));
  gclog_or_tty->print_cr("H regions: %d.", _num_humongous_regions);

  // TODO: check regions accounting for young/survivor/tenured
  return true;
}

bool G1CollectedHeap::is_in_closed_subset(const void* p) const {
  HeapRegion* hr = heap_region_containing(p);
  if (hr == NULL) {
    return is_in_permanent(p);
  } else {
    return hr->is_in(p);
  }
}
#endif // PRODUCT

void G1CollectedHeap::g1_unimplemented() {
  // Unimplemented();
}


// Local Variables: ***
// c-indentation-style: gnu ***
// End: ***
