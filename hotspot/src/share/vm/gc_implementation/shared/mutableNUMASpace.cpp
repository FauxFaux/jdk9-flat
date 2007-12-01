
/*
 * Copyright 2006-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
# include "incls/_mutableNUMASpace.cpp.incl"


MutableNUMASpace::MutableNUMASpace() {
  _lgrp_spaces = new (ResourceObj::C_HEAP) GrowableArray<LGRPSpace*>(0, true);
  _page_size = os::vm_page_size();
  _adaptation_cycles = 0;
  _samples_count = 0;
  update_layout(true);
}

MutableNUMASpace::~MutableNUMASpace() {
  for (int i = 0; i < lgrp_spaces()->length(); i++) {
    delete lgrp_spaces()->at(i);
  }
  delete lgrp_spaces();
}

void MutableNUMASpace::mangle_unused_area() {
  for (int i = 0; i < lgrp_spaces()->length(); i++) {
    LGRPSpace *ls = lgrp_spaces()->at(i);
    MutableSpace *s = ls->space();
    HeapWord *top = MAX2((HeapWord*)round_down((intptr_t)s->top(), page_size()), s->bottom());
    if (top < s->end()) {
      ls->add_invalid_region(MemRegion(top, s->end()));
    }
    s->mangle_unused_area();
  }
}

// There may be unallocated holes in the middle chunks
// that should be filled with dead objects to ensure parseability.
void MutableNUMASpace::ensure_parsability() {
  for (int i = 0; i < lgrp_spaces()->length(); i++) {
    LGRPSpace *ls = lgrp_spaces()->at(i);
    MutableSpace *s = ls->space();
    if (!s->contains(top())) {
      if (s->free_in_words() > 0) {
        SharedHeap::fill_region_with_object(MemRegion(s->top(), s->end()));
        size_t area_touched_words = pointer_delta(s->end(), s->top(), sizeof(HeapWordSize));
#ifndef ASSERT
        if (!ZapUnusedHeapArea) {
          area_touched_words = MIN2((size_t)align_object_size(typeArrayOopDesc::header_size(T_INT)),
                                    area_touched_words);
        }
#endif
        MemRegion invalid;
        HeapWord *crossing_start = (HeapWord*)round_to((intptr_t)s->top(), os::vm_page_size());
        HeapWord *crossing_end = (HeapWord*)round_to((intptr_t)(s->top() + area_touched_words),
                                                     os::vm_page_size());
        if (crossing_start != crossing_end) {
          // If object header crossed a small page boundary we mark the area
          // as invalid rounding it to a page_size().
          HeapWord *start = MAX2((HeapWord*)round_down((intptr_t)s->top(), page_size()), s->bottom());
          HeapWord *end = MIN2((HeapWord*)round_to((intptr_t)(s->top() + area_touched_words), page_size()),
                               s->end());
          invalid = MemRegion(start, end);
        }

        ls->add_invalid_region(invalid);
        s->set_top(s->end());
      }
    } else {
#ifdef ASSERT
      MemRegion invalid(s->top(), s->end());
      ls->add_invalid_region(invalid);
#else
      if (ZapUnusedHeapArea) {
        MemRegion invalid(s->top(), s->end());
        ls->add_invalid_region(invalid);
      } else break;
#endif
    }
  }
}

size_t MutableNUMASpace::used_in_words() const {
  size_t s = 0;
  for (int i = 0; i < lgrp_spaces()->length(); i++) {
    s += lgrp_spaces()->at(i)->space()->used_in_words();
  }
  return s;
}

size_t MutableNUMASpace::free_in_words() const {
  size_t s = 0;
  for (int i = 0; i < lgrp_spaces()->length(); i++) {
    s += lgrp_spaces()->at(i)->space()->free_in_words();
  }
  return s;
}


size_t MutableNUMASpace::tlab_capacity(Thread *thr) const {
  guarantee(thr != NULL, "No thread");
  int lgrp_id = thr->lgrp_id();
  assert(lgrp_id != -1, "No lgrp_id set");
  int i = lgrp_spaces()->find(&lgrp_id, LGRPSpace::equals);
  if (i == -1) {
    return 0;
  }
  return lgrp_spaces()->at(i)->space()->capacity_in_bytes();
}

size_t MutableNUMASpace::unsafe_max_tlab_alloc(Thread *thr) const {
  guarantee(thr != NULL, "No thread");
  int lgrp_id = thr->lgrp_id();
  assert(lgrp_id != -1, "No lgrp_id set");
  int i = lgrp_spaces()->find(&lgrp_id, LGRPSpace::equals);
  if (i == -1) {
    return 0;
  }
  return lgrp_spaces()->at(i)->space()->free_in_bytes();
}

// Check if the NUMA topology has changed. Add and remove spaces if needed.
// The update can be forced by setting the force parameter equal to true.
bool MutableNUMASpace::update_layout(bool force) {
  // Check if the topology had changed.
  bool changed = os::numa_topology_changed();
  if (force || changed) {
    // Compute lgrp intersection. Add/remove spaces.
    int lgrp_limit = (int)os::numa_get_groups_num();
    int *lgrp_ids = NEW_C_HEAP_ARRAY(int, lgrp_limit);
    int lgrp_num = (int)os::numa_get_leaf_groups(lgrp_ids, lgrp_limit);
    assert(lgrp_num > 0, "There should be at least one locality group");
    // Add new spaces for the new nodes
    for (int i = 0; i < lgrp_num; i++) {
      bool found = false;
      for (int j = 0; j < lgrp_spaces()->length(); j++) {
        if (lgrp_spaces()->at(j)->lgrp_id() == lgrp_ids[i]) {
          found = true;
          break;
        }
      }
      if (!found) {
        lgrp_spaces()->append(new LGRPSpace(lgrp_ids[i]));
      }
    }

    // Remove spaces for the removed nodes.
    for (int i = 0; i < lgrp_spaces()->length();) {
      bool found = false;
      for (int j = 0; j < lgrp_num; j++) {
        if (lgrp_spaces()->at(i)->lgrp_id() == lgrp_ids[j]) {
          found = true;
          break;
        }
      }
      if (!found) {
        delete lgrp_spaces()->at(i);
        lgrp_spaces()->remove_at(i);
      } else {
        i++;
      }
    }

    FREE_C_HEAP_ARRAY(int, lgrp_ids);

    if (changed) {
      for (JavaThread *thread = Threads::first(); thread; thread = thread->next()) {
        thread->set_lgrp_id(-1);
      }
    }
    return true;
  }
  return false;
}

// Bias region towards the first-touching lgrp. Set the right page sizes.
void MutableNUMASpace::bias_region(MemRegion mr) {
  HeapWord *start = (HeapWord*)round_to((intptr_t)mr.start(), page_size());
  HeapWord *end = (HeapWord*)round_down((intptr_t)mr.end(), page_size());
  if (end > start) {
    MemRegion aligned_region(start, end);
    assert((intptr_t)aligned_region.start()     % page_size() == 0 &&
           (intptr_t)aligned_region.byte_size() % page_size() == 0, "Bad alignment");
    assert(region().contains(aligned_region), "Sanity");
    os::free_memory((char*)aligned_region.start(), aligned_region.byte_size());
    os::realign_memory((char*)aligned_region.start(), aligned_region.byte_size(), page_size());
    os::numa_make_local((char*)aligned_region.start(), aligned_region.byte_size());
  }
}

// Free all pages in the region.
void MutableNUMASpace::free_region(MemRegion mr) {
  HeapWord *start = (HeapWord*)round_to((intptr_t)mr.start(), page_size());
  HeapWord *end = (HeapWord*)round_down((intptr_t)mr.end(), page_size());
  if (end > start) {
    MemRegion aligned_region(start, end);
    assert((intptr_t)aligned_region.start()     % page_size() == 0 &&
           (intptr_t)aligned_region.byte_size() % page_size() == 0, "Bad alignment");
    assert(region().contains(aligned_region), "Sanity");
    os::free_memory((char*)aligned_region.start(), aligned_region.byte_size());
  }
}

// Update space layout. Perform adaptation.
void MutableNUMASpace::update() {
  if (update_layout(false)) {
    // If the topology has changed, make all chunks zero-sized.
    for (int i = 0; i < lgrp_spaces()->length(); i++) {
      MutableSpace *s = lgrp_spaces()->at(i)->space();
      s->set_end(s->bottom());
      s->set_top(s->bottom());
    }
    initialize(region(), true);
  } else {
    bool should_initialize = false;
    for (int i = 0; i < lgrp_spaces()->length(); i++) {
      if (!lgrp_spaces()->at(i)->invalid_region().is_empty()) {
        should_initialize = true;
        break;
      }
    }

    if (should_initialize ||
        (UseAdaptiveNUMAChunkSizing && adaptation_cycles() < samples_count())) {
      initialize(region(), true);
    }
  }

  if (NUMAStats) {
    for (int i = 0; i < lgrp_spaces()->length(); i++) {
      lgrp_spaces()->at(i)->accumulate_statistics(page_size());
    }
  }

  scan_pages(NUMAPageScanRate);
}

// Scan pages. Free pages that have smaller size or wrong placement.
void MutableNUMASpace::scan_pages(size_t page_count)
{
  size_t pages_per_chunk = page_count / lgrp_spaces()->length();
  if (pages_per_chunk > 0) {
    for (int i = 0; i < lgrp_spaces()->length(); i++) {
      LGRPSpace *ls = lgrp_spaces()->at(i);
      ls->scan_pages(page_size(), pages_per_chunk);
    }
  }
}

// Accumulate statistics about the allocation rate of each lgrp.
void MutableNUMASpace::accumulate_statistics() {
  if (UseAdaptiveNUMAChunkSizing) {
    for (int i = 0; i < lgrp_spaces()->length(); i++) {
      lgrp_spaces()->at(i)->sample();
    }
    increment_samples_count();
  }

  if (NUMAStats) {
    for (int i = 0; i < lgrp_spaces()->length(); i++) {
      lgrp_spaces()->at(i)->accumulate_statistics(page_size());
    }
  }
}

// Get the current size of a chunk.
// This function computes the size of the chunk based on the
// difference between chunk ends. This allows it to work correctly in
// case the whole space is resized and during the process of adaptive
// chunk resizing.
size_t MutableNUMASpace::current_chunk_size(int i) {
  HeapWord *cur_end, *prev_end;
  if (i == 0) {
    prev_end = bottom();
  } else {
    prev_end = lgrp_spaces()->at(i - 1)->space()->end();
  }
  if (i == lgrp_spaces()->length() - 1) {
    cur_end = end();
  } else {
    cur_end = lgrp_spaces()->at(i)->space()->end();
  }
  if (cur_end > prev_end) {
    return pointer_delta(cur_end, prev_end, sizeof(char));
  }
  return 0;
}

// Return the default chunk size by equally diving the space.
// page_size() aligned.
size_t MutableNUMASpace::default_chunk_size() {
  return base_space_size() / lgrp_spaces()->length() * page_size();
}

// Produce a new chunk size. page_size() aligned.
size_t MutableNUMASpace::adaptive_chunk_size(int i, size_t limit) {
  size_t pages_available = base_space_size();
  for (int j = 0; j < i; j++) {
    pages_available -= round_down(current_chunk_size(j), page_size()) / page_size();
  }
  pages_available -= lgrp_spaces()->length() - i - 1;
  assert(pages_available > 0, "No pages left");
  float alloc_rate = 0;
  for (int j = i; j < lgrp_spaces()->length(); j++) {
    alloc_rate += lgrp_spaces()->at(j)->alloc_rate()->average();
  }
  size_t chunk_size = 0;
  if (alloc_rate > 0) {
    LGRPSpace *ls = lgrp_spaces()->at(i);
    chunk_size = (size_t)(ls->alloc_rate()->average() * pages_available / alloc_rate) * page_size();
  }
  chunk_size = MAX2(chunk_size, page_size());

  if (limit > 0) {
    limit = round_down(limit, page_size());
    if (chunk_size > current_chunk_size(i)) {
      chunk_size = MIN2((off_t)chunk_size, (off_t)current_chunk_size(i) + (off_t)limit);
    } else {
      chunk_size = MAX2((off_t)chunk_size, (off_t)current_chunk_size(i) - (off_t)limit);
    }
  }
  assert(chunk_size <= pages_available * page_size(), "Chunk size out of range");
  return chunk_size;
}


// Return the bottom_region and the top_region. Align them to page_size() boundary.
// |------------------new_region---------------------------------|
// |----bottom_region--|---intersection---|------top_region------|
void MutableNUMASpace::select_tails(MemRegion new_region, MemRegion intersection,
                                    MemRegion* bottom_region, MemRegion *top_region) {
  // Is there bottom?
  if (new_region.start() < intersection.start()) { // Yes
    // Try to coalesce small pages into a large one.
    if (UseLargePages && page_size() >= os::large_page_size()) {
      HeapWord* p = (HeapWord*)round_to((intptr_t) intersection.start(), os::large_page_size());
      if (new_region.contains(p)
          && pointer_delta(p, new_region.start(), sizeof(char)) >= os::large_page_size()) {
        if (intersection.contains(p)) {
          intersection = MemRegion(p, intersection.end());
        } else {
          intersection = MemRegion(p, p);
        }
      }
    }
    *bottom_region = MemRegion(new_region.start(), intersection.start());
  } else {
    *bottom_region = MemRegion();
  }

  // Is there top?
  if (intersection.end() < new_region.end()) { // Yes
    // Try to coalesce small pages into a large one.
    if (UseLargePages && page_size() >= os::large_page_size()) {
      HeapWord* p = (HeapWord*)round_down((intptr_t) intersection.end(), os::large_page_size());
      if (new_region.contains(p)
          && pointer_delta(new_region.end(), p, sizeof(char)) >= os::large_page_size()) {
        if (intersection.contains(p)) {
          intersection = MemRegion(intersection.start(), p);
        } else {
          intersection = MemRegion(p, p);
        }
      }
    }
    *top_region = MemRegion(intersection.end(), new_region.end());
  } else {
    *top_region = MemRegion();
  }
}

// Try to merge the invalid region with the bottom or top region by decreasing
// the intersection area. Return the invalid_region aligned to the page_size()
// boundary if it's inside the intersection. Return non-empty invalid_region
// if it lies inside the intersection (also page-aligned).
// |------------------new_region---------------------------------|
// |----------------|-------invalid---|--------------------------|
// |----bottom_region--|---intersection---|------top_region------|
void MutableNUMASpace::merge_regions(MemRegion new_region, MemRegion* intersection,
                                     MemRegion *invalid_region) {
  if (intersection->start() >= invalid_region->start() && intersection->contains(invalid_region->end())) {
    *intersection = MemRegion(invalid_region->end(), intersection->end());
    *invalid_region = MemRegion();
  } else
    if (intersection->end() <= invalid_region->end() && intersection->contains(invalid_region->start())) {
      *intersection = MemRegion(intersection->start(), invalid_region->start());
      *invalid_region = MemRegion();
    } else
      if (intersection->equals(*invalid_region) || invalid_region->contains(*intersection)) {
        *intersection = MemRegion(new_region.start(), new_region.start());
        *invalid_region = MemRegion();
      } else
        if (intersection->contains(invalid_region)) {
            // That's the only case we have to make an additional bias_region() call.
            HeapWord* start = invalid_region->start();
            HeapWord* end = invalid_region->end();
            if (UseLargePages && page_size() >= os::large_page_size()) {
              HeapWord *p = (HeapWord*)round_down((intptr_t) start, os::large_page_size());
              if (new_region.contains(p)) {
                start = p;
              }
              p = (HeapWord*)round_to((intptr_t) end, os::large_page_size());
              if (new_region.contains(end)) {
                end = p;
              }
            }
            if (intersection->start() > start) {
              *intersection = MemRegion(start, intersection->end());
            }
            if (intersection->end() < end) {
              *intersection = MemRegion(intersection->start(), end);
            }
            *invalid_region = MemRegion(start, end);
        }
}

void MutableNUMASpace::initialize(MemRegion mr, bool clear_space) {
  assert(clear_space, "Reallocation will destory data!");
  assert(lgrp_spaces()->length() > 0, "There should be at least one space");

  MemRegion old_region = region(), new_region;
  set_bottom(mr.start());
  set_end(mr.end());
  MutableSpace::set_top(bottom());

  // Compute chunk sizes
  size_t prev_page_size = page_size();
  set_page_size(UseLargePages ? os::large_page_size() : os::vm_page_size());
  HeapWord* rounded_bottom = (HeapWord*)round_to((intptr_t) bottom(), page_size());
  HeapWord* rounded_end = (HeapWord*)round_down((intptr_t) end(), page_size());
  size_t base_space_size_pages = pointer_delta(rounded_end, rounded_bottom, sizeof(char)) / page_size();

  // Try small pages if the chunk size is too small
  if (base_space_size_pages / lgrp_spaces()->length() == 0
      && page_size() > (size_t)os::vm_page_size()) {
    set_page_size(os::vm_page_size());
    rounded_bottom = (HeapWord*)round_to((intptr_t) bottom(), page_size());
    rounded_end = (HeapWord*)round_down((intptr_t) end(), page_size());
    base_space_size_pages = pointer_delta(rounded_end, rounded_bottom, sizeof(char)) / page_size();
  }
  guarantee(base_space_size_pages / lgrp_spaces()->length() > 0, "Space too small");
  set_base_space_size(base_space_size_pages);

  // Handle space resize
  MemRegion top_region, bottom_region;
  if (!old_region.equals(region())) {
    new_region = MemRegion(rounded_bottom, rounded_end);
    MemRegion intersection = new_region.intersection(old_region);
    if (intersection.start() == NULL ||
        intersection.end() == NULL   ||
        prev_page_size > page_size()) { // If the page size got smaller we have to change
                                        // the page size preference for the whole space.
      intersection = MemRegion(new_region.start(), new_region.start());
    }
    select_tails(new_region, intersection, &bottom_region, &top_region);
    bias_region(bottom_region);
    bias_region(top_region);
  }

  // Check if the space layout has changed significantly?
  // This happens when the space has been resized so that either head or tail
  // chunk became less than a page.
  bool layout_valid = UseAdaptiveNUMAChunkSizing          &&
                      current_chunk_size(0) > page_size() &&
                      current_chunk_size(lgrp_spaces()->length() - 1) > page_size();


  for (int i = 0; i < lgrp_spaces()->length(); i++) {
    LGRPSpace *ls = lgrp_spaces()->at(i);
    MutableSpace *s = ls->space();
    old_region = s->region();

    size_t chunk_byte_size = 0, old_chunk_byte_size = 0;
    if (i < lgrp_spaces()->length() - 1) {
      if (!UseAdaptiveNUMAChunkSizing                                ||
          (UseAdaptiveNUMAChunkSizing && NUMAChunkResizeWeight == 0) ||
           samples_count() < AdaptiveSizePolicyReadyThreshold) {
        // No adaptation. Divide the space equally.
        chunk_byte_size = default_chunk_size();
      } else
        if (!layout_valid || NUMASpaceResizeRate == 0) {
          // Fast adaptation. If no space resize rate is set, resize
          // the chunks instantly.
          chunk_byte_size = adaptive_chunk_size(i, 0);
        } else {
          // Slow adaptation. Resize the chunks moving no more than
          // NUMASpaceResizeRate bytes per collection.
          size_t limit = NUMASpaceResizeRate /
                         (lgrp_spaces()->length() * (lgrp_spaces()->length() + 1) / 2);
          chunk_byte_size = adaptive_chunk_size(i, MAX2(limit * (i + 1), page_size()));
        }

      assert(chunk_byte_size >= page_size(), "Chunk size too small");
      assert(chunk_byte_size <= capacity_in_bytes(), "Sanity check");
    }

    if (i == 0) { // Bottom chunk
      if (i != lgrp_spaces()->length() - 1) {
        new_region = MemRegion(bottom(), rounded_bottom + (chunk_byte_size >> LogHeapWordSize));
      } else {
        new_region = MemRegion(bottom(), end());
      }
    } else
      if (i < lgrp_spaces()->length() - 1) { // Middle chunks
        MutableSpace *ps = lgrp_spaces()->at(i - 1)->space();
        new_region = MemRegion(ps->end(),
                               ps->end() + (chunk_byte_size >> LogHeapWordSize));
      } else { // Top chunk
        MutableSpace *ps = lgrp_spaces()->at(i - 1)->space();
        new_region = MemRegion(ps->end(), end());
      }
    guarantee(region().contains(new_region), "Region invariant");


    // The general case:
    // |---------------------|--invalid---|--------------------------|
    // |------------------new_region---------------------------------|
    // |----bottom_region--|---intersection---|------top_region------|
    //                     |----old_region----|
    // The intersection part has all pages in place we don't need to migrate them.
    // Pages for the top and bottom part should be freed and then reallocated.

    MemRegion intersection = old_region.intersection(new_region);

    if (intersection.start() == NULL || intersection.end() == NULL) {
      intersection = MemRegion(new_region.start(), new_region.start());
    }

    MemRegion invalid_region = ls->invalid_region().intersection(new_region);
    if (!invalid_region.is_empty()) {
      merge_regions(new_region, &intersection, &invalid_region);
      free_region(invalid_region);
    }
    select_tails(new_region, intersection, &bottom_region, &top_region);
    free_region(bottom_region);
    free_region(top_region);

    // If we clear the region, we would mangle it in debug. That would cause page
    // allocation in a different place. Hence setting the top directly.
    s->initialize(new_region, false);
    s->set_top(s->bottom());

    ls->set_invalid_region(MemRegion());

    set_adaptation_cycles(samples_count());
  }
}

// Set the top of the whole space.
// Mark the the holes in chunks below the top() as invalid.
void MutableNUMASpace::set_top(HeapWord* value) {
  bool found_top = false;
  for (int i = 0; i < lgrp_spaces()->length(); i++) {
    LGRPSpace *ls = lgrp_spaces()->at(i);
    MutableSpace *s = ls->space();
    HeapWord *top = MAX2((HeapWord*)round_down((intptr_t)s->top(), page_size()), s->bottom());

    if (s->contains(value)) {
      if (top < value && top < s->end()) {
        ls->add_invalid_region(MemRegion(top, value));
      }
      s->set_top(value);
      found_top = true;
    } else {
        if (found_top) {
            s->set_top(s->bottom());
        } else {
            if (top < s->end()) {
              ls->add_invalid_region(MemRegion(top, s->end()));
            }
            s->set_top(s->end());
        }
    }
  }
  MutableSpace::set_top(value);
}

void MutableNUMASpace::clear() {
  MutableSpace::set_top(bottom());
  for (int i = 0; i < lgrp_spaces()->length(); i++) {
    lgrp_spaces()->at(i)->space()->clear();
  }
}

HeapWord* MutableNUMASpace::allocate(size_t size) {
  int lgrp_id = Thread::current()->lgrp_id();
  if (lgrp_id == -1) {
    lgrp_id = os::numa_get_group_id();
    Thread::current()->set_lgrp_id(lgrp_id);
  }

  int i = lgrp_spaces()->find(&lgrp_id, LGRPSpace::equals);

  // It is possible that a new CPU has been hotplugged and
  // we haven't reshaped the space accordingly.
  if (i == -1) {
    i = os::random() % lgrp_spaces()->length();
  }

  MutableSpace *s = lgrp_spaces()->at(i)->space();
  HeapWord *p = s->allocate(size);

  if (p != NULL && s->free_in_words() < (size_t)oopDesc::header_size()) {
    s->set_top(s->top() - size);
    p = NULL;
  }
  if (p != NULL) {
    if (top() < s->top()) { // Keep _top updated.
      MutableSpace::set_top(s->top());
    }
  }
  // Make the page allocation happen here.
  if (p != NULL) {
    for (HeapWord *i = p; i < p + size; i += os::vm_page_size() >> LogHeapWordSize) {
      *(int*)i = 0;
    }
  }

  return p;
}

// This version is lock-free.
HeapWord* MutableNUMASpace::cas_allocate(size_t size) {
  int lgrp_id = Thread::current()->lgrp_id();
  if (lgrp_id == -1) {
    lgrp_id = os::numa_get_group_id();
    Thread::current()->set_lgrp_id(lgrp_id);
  }

  int i = lgrp_spaces()->find(&lgrp_id, LGRPSpace::equals);
  // It is possible that a new CPU has been hotplugged and
  // we haven't reshaped the space accordingly.
  if (i == -1) {
    i = os::random() % lgrp_spaces()->length();
  }
  MutableSpace *s = lgrp_spaces()->at(i)->space();
  HeapWord *p = s->cas_allocate(size);
  if (p != NULL && s->free_in_words() < (size_t)oopDesc::header_size()) {
    if (s->cas_deallocate(p, size)) {
      // We were the last to allocate and created a fragment less than
      // a minimal object.
      p = NULL;
    }
  }
  if (p != NULL) {
    HeapWord* cur_top, *cur_chunk_top = p + size;
    while ((cur_top = top()) < cur_chunk_top) { // Keep _top updated.
      if (Atomic::cmpxchg_ptr(cur_chunk_top, top_addr(), cur_top) == cur_top) {
        break;
      }
    }
  }

  // Make the page allocation happen here.
  if (p != NULL) {
    for (HeapWord *i = p; i < p + size; i += os::vm_page_size() >> LogHeapWordSize) {
      *(int*)i = 0;
    }
  }
  return p;
}

void MutableNUMASpace::print_short_on(outputStream* st) const {
  MutableSpace::print_short_on(st);
  st->print(" (");
  for (int i = 0; i < lgrp_spaces()->length(); i++) {
    st->print("lgrp %d: ", lgrp_spaces()->at(i)->lgrp_id());
    lgrp_spaces()->at(i)->space()->print_short_on(st);
    if (i < lgrp_spaces()->length() - 1) {
      st->print(", ");
    }
  }
  st->print(")");
}

void MutableNUMASpace::print_on(outputStream* st) const {
  MutableSpace::print_on(st);
  for (int i = 0; i < lgrp_spaces()->length(); i++) {
    LGRPSpace *ls = lgrp_spaces()->at(i);
    st->print("    lgrp %d", ls->lgrp_id());
    ls->space()->print_on(st);
    if (NUMAStats) {
      st->print("    local/remote/unbiased/uncommitted: %dK/%dK/%dK/%dK, large/small pages: %d/%d\n",
                ls->space_stats()->_local_space / K,
                ls->space_stats()->_remote_space / K,
                ls->space_stats()->_unbiased_space / K,
                ls->space_stats()->_uncommited_space / K,
                ls->space_stats()->_large_pages,
                ls->space_stats()->_small_pages);
    }
  }
}

void MutableNUMASpace::verify(bool allow_dirty) const {
 for (int i = 0; i < lgrp_spaces()->length(); i++) {
    lgrp_spaces()->at(i)->space()->verify(allow_dirty);
  }
}

// Scan pages and gather stats about page placement and size.
void MutableNUMASpace::LGRPSpace::accumulate_statistics(size_t page_size) {
  clear_space_stats();
  char *start = (char*)round_to((intptr_t) space()->bottom(), page_size);
  char* end = (char*)round_down((intptr_t) space()->end(), page_size);
  if (start < end) {
    for (char *p = start; p < end;) {
      os::page_info info;
      if (os::get_page_info(p, &info)) {
        if (info.size > 0) {
          if (info.size > (size_t)os::vm_page_size()) {
            space_stats()->_large_pages++;
          } else {
            space_stats()->_small_pages++;
          }
          if (info.lgrp_id == lgrp_id()) {
            space_stats()->_local_space += info.size;
          } else {
            space_stats()->_remote_space += info.size;
          }
          p += info.size;
        } else {
          p += os::vm_page_size();
          space_stats()->_uncommited_space += os::vm_page_size();
        }
      } else {
        return;
      }
    }
  }
  space_stats()->_unbiased_space = pointer_delta(start, space()->bottom(), sizeof(char)) +
                                   pointer_delta(space()->end(), end, sizeof(char));

}

// Scan page_count pages and verify if they have the right size and right placement.
// If invalid pages are found they are freed in hope that subsequent reallocation
// will be more successful.
void MutableNUMASpace::LGRPSpace::scan_pages(size_t page_size, size_t page_count)
{
  char* range_start = (char*)round_to((intptr_t) space()->bottom(), page_size);
  char* range_end = (char*)round_down((intptr_t) space()->end(), page_size);

  if (range_start > last_page_scanned() || last_page_scanned() >= range_end) {
    set_last_page_scanned(range_start);
  }

  char *scan_start = last_page_scanned();
  char* scan_end = MIN2(scan_start + page_size * page_count, range_end);

  os::page_info page_expected, page_found;
  page_expected.size = page_size;
  page_expected.lgrp_id = lgrp_id();

  char *s = scan_start;
  while (s < scan_end) {
    char *e = os::scan_pages(s, (char*)scan_end, &page_expected, &page_found);
    if (e == NULL) {
      break;
    }
    if (e != scan_end) {
      if ((page_expected.size != page_size || page_expected.lgrp_id != lgrp_id())
          && page_expected.size != 0) {
        os::free_memory(s, pointer_delta(e, s, sizeof(char)));
      }
      page_expected = page_found;
    }
    s = e;
  }

  set_last_page_scanned(scan_end);
}
