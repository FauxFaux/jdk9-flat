/*
 * Copyright 2005-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "incls/_psParallelCompact.cpp.incl"

#include <math.h>

// All sizes are in HeapWords.
const size_t ParallelCompactData::Log2ChunkSize  = 9; // 512 words
const size_t ParallelCompactData::ChunkSize      = (size_t)1 << Log2ChunkSize;
const size_t ParallelCompactData::ChunkSizeBytes = ChunkSize << LogHeapWordSize;
const size_t ParallelCompactData::ChunkSizeOffsetMask = ChunkSize - 1;
const size_t ParallelCompactData::ChunkAddrOffsetMask = ChunkSizeBytes - 1;
const size_t ParallelCompactData::ChunkAddrMask  = ~ChunkAddrOffsetMask;

// 32-bit:  128 words covers 4 bitmap words
// 64-bit:  128 words covers 2 bitmap words
const size_t ParallelCompactData::Log2BlockSize   = 7; // 128 words
const size_t ParallelCompactData::BlockSize       = (size_t)1 << Log2BlockSize;
const size_t ParallelCompactData::BlockOffsetMask = BlockSize - 1;
const size_t ParallelCompactData::BlockMask       = ~BlockOffsetMask;

const size_t ParallelCompactData::BlocksPerChunk = ChunkSize / BlockSize;

const ParallelCompactData::ChunkData::chunk_sz_t
ParallelCompactData::ChunkData::dc_shift = 27;

const ParallelCompactData::ChunkData::chunk_sz_t
ParallelCompactData::ChunkData::dc_mask = ~0U << dc_shift;

const ParallelCompactData::ChunkData::chunk_sz_t
ParallelCompactData::ChunkData::dc_one = 0x1U << dc_shift;

const ParallelCompactData::ChunkData::chunk_sz_t
ParallelCompactData::ChunkData::los_mask = ~dc_mask;

const ParallelCompactData::ChunkData::chunk_sz_t
ParallelCompactData::ChunkData::dc_claimed = 0x8U << dc_shift;

const ParallelCompactData::ChunkData::chunk_sz_t
ParallelCompactData::ChunkData::dc_completed = 0xcU << dc_shift;

#ifdef ASSERT
short   ParallelCompactData::BlockData::_cur_phase = 0;
#endif

SpaceInfo PSParallelCompact::_space_info[PSParallelCompact::last_space_id];
bool      PSParallelCompact::_print_phases = false;

ReferenceProcessor* PSParallelCompact::_ref_processor = NULL;
klassOop            PSParallelCompact::_updated_int_array_klass_obj = NULL;

double PSParallelCompact::_dwl_mean;
double PSParallelCompact::_dwl_std_dev;
double PSParallelCompact::_dwl_first_term;
double PSParallelCompact::_dwl_adjustment;
#ifdef  ASSERT
bool   PSParallelCompact::_dwl_initialized = false;
#endif  // #ifdef ASSERT

#ifdef VALIDATE_MARK_SWEEP
GrowableArray<oop*>*    PSParallelCompact::_root_refs_stack = NULL;
GrowableArray<oop> *    PSParallelCompact::_live_oops = NULL;
GrowableArray<oop> *    PSParallelCompact::_live_oops_moved_to = NULL;
GrowableArray<size_t>*  PSParallelCompact::_live_oops_size = NULL;
size_t                  PSParallelCompact::_live_oops_index = 0;
size_t                  PSParallelCompact::_live_oops_index_at_perm = 0;
GrowableArray<oop*>*    PSParallelCompact::_other_refs_stack = NULL;
GrowableArray<oop*>*    PSParallelCompact::_adjusted_pointers = NULL;
bool                    PSParallelCompact::_pointer_tracking = false;
bool                    PSParallelCompact::_root_tracking = true;

GrowableArray<HeapWord*>* PSParallelCompact::_cur_gc_live_oops = NULL;
GrowableArray<HeapWord*>* PSParallelCompact::_cur_gc_live_oops_moved_to = NULL;
GrowableArray<size_t>   * PSParallelCompact::_cur_gc_live_oops_size = NULL;
GrowableArray<HeapWord*>* PSParallelCompact::_last_gc_live_oops = NULL;
GrowableArray<HeapWord*>* PSParallelCompact::_last_gc_live_oops_moved_to = NULL;
GrowableArray<size_t>   * PSParallelCompact::_last_gc_live_oops_size = NULL;
#endif

// XXX beg - verification code; only works while we also mark in object headers
static void
verify_mark_bitmap(ParMarkBitMap& _mark_bitmap)
{
  ParallelScavengeHeap* heap = PSParallelCompact::gc_heap();

  PSPermGen* perm_gen = heap->perm_gen();
  PSOldGen* old_gen = heap->old_gen();
  PSYoungGen* young_gen = heap->young_gen();

  MutableSpace* perm_space = perm_gen->object_space();
  MutableSpace* old_space = old_gen->object_space();
  MutableSpace* eden_space = young_gen->eden_space();
  MutableSpace* from_space = young_gen->from_space();
  MutableSpace* to_space = young_gen->to_space();

  // 'from_space' here is the survivor space at the lower address.
  if (to_space->bottom() < from_space->bottom()) {
    from_space = to_space;
    to_space = young_gen->from_space();
  }

  HeapWord* boundaries[12];
  unsigned int bidx = 0;
  const unsigned int bidx_max = sizeof(boundaries) / sizeof(boundaries[0]);

  boundaries[0] = perm_space->bottom();
  boundaries[1] = perm_space->top();
  boundaries[2] = old_space->bottom();
  boundaries[3] = old_space->top();
  boundaries[4] = eden_space->bottom();
  boundaries[5] = eden_space->top();
  boundaries[6] = from_space->bottom();
  boundaries[7] = from_space->top();
  boundaries[8] = to_space->bottom();
  boundaries[9] = to_space->top();
  boundaries[10] = to_space->end();
  boundaries[11] = to_space->end();

  BitMap::idx_t beg_bit = 0;
  BitMap::idx_t end_bit;
  BitMap::idx_t tmp_bit;
  const BitMap::idx_t last_bit = _mark_bitmap.size();
  do {
    HeapWord* addr = _mark_bitmap.bit_to_addr(beg_bit);
    if (_mark_bitmap.is_marked(beg_bit)) {
      oop obj = (oop)addr;
      assert(obj->is_gc_marked(), "obj header is not marked");
      end_bit = _mark_bitmap.find_obj_end(beg_bit, last_bit);
      const size_t size = _mark_bitmap.obj_size(beg_bit, end_bit);
      assert(size == (size_t)obj->size(), "end bit wrong?");
      beg_bit = _mark_bitmap.find_obj_beg(beg_bit + 1, last_bit);
      assert(beg_bit > end_bit, "bit set in middle of an obj");
    } else {
      if (addr >= boundaries[bidx] && addr < boundaries[bidx + 1]) {
        // a dead object in the current space.
        oop obj = (oop)addr;
        end_bit = _mark_bitmap.addr_to_bit(addr + obj->size());
        assert(!obj->is_gc_marked(), "obj marked in header, not in bitmap");
        tmp_bit = beg_bit + 1;
        beg_bit = _mark_bitmap.find_obj_beg(tmp_bit, end_bit);
        assert(beg_bit == end_bit, "beg bit set in unmarked obj");
        beg_bit = _mark_bitmap.find_obj_end(tmp_bit, end_bit);
        assert(beg_bit == end_bit, "end bit set in unmarked obj");
      } else if (addr < boundaries[bidx + 2]) {
        // addr is between top in the current space and bottom in the next.
        end_bit = beg_bit + pointer_delta(boundaries[bidx + 2], addr);
        tmp_bit = beg_bit;
        beg_bit = _mark_bitmap.find_obj_beg(tmp_bit, end_bit);
        assert(beg_bit == end_bit, "beg bit set above top");
        beg_bit = _mark_bitmap.find_obj_end(tmp_bit, end_bit);
        assert(beg_bit == end_bit, "end bit set above top");
        bidx += 2;
      } else if (bidx < bidx_max - 2) {
        bidx += 2; // ???
      } else {
        tmp_bit = beg_bit;
        beg_bit = _mark_bitmap.find_obj_beg(tmp_bit, last_bit);
        assert(beg_bit == last_bit, "beg bit set outside heap");
        beg_bit = _mark_bitmap.find_obj_end(tmp_bit, last_bit);
        assert(beg_bit == last_bit, "end bit set outside heap");
      }
    }
  } while (beg_bit < last_bit);
}
// XXX end - verification code; only works while we also mark in object headers

#ifndef PRODUCT
const char* PSParallelCompact::space_names[] = {
  "perm", "old ", "eden", "from", "to  "
};

void PSParallelCompact::print_chunk_ranges()
{
  tty->print_cr("space  bottom     top        end        new_top");
  tty->print_cr("------ ---------- ---------- ---------- ----------");

  for (unsigned int id = 0; id < last_space_id; ++id) {
    const MutableSpace* space = _space_info[id].space();
    tty->print_cr("%u %s "
                  SIZE_FORMAT_W("10") " " SIZE_FORMAT_W("10") " "
                  SIZE_FORMAT_W("10") " " SIZE_FORMAT_W("10") " ",
                  id, space_names[id],
                  summary_data().addr_to_chunk_idx(space->bottom()),
                  summary_data().addr_to_chunk_idx(space->top()),
                  summary_data().addr_to_chunk_idx(space->end()),
                  summary_data().addr_to_chunk_idx(_space_info[id].new_top()));
  }
}

void
print_generic_summary_chunk(size_t i, const ParallelCompactData::ChunkData* c)
{
#define CHUNK_IDX_FORMAT        SIZE_FORMAT_W("7")
#define CHUNK_DATA_FORMAT       SIZE_FORMAT_W("5")

  ParallelCompactData& sd = PSParallelCompact::summary_data();
  size_t dci = c->destination() ? sd.addr_to_chunk_idx(c->destination()) : 0;
  tty->print_cr(CHUNK_IDX_FORMAT " " PTR_FORMAT " "
                CHUNK_IDX_FORMAT " " PTR_FORMAT " "
                CHUNK_DATA_FORMAT " " CHUNK_DATA_FORMAT " "
                CHUNK_DATA_FORMAT " " CHUNK_IDX_FORMAT " %d",
                i, c->data_location(), dci, c->destination(),
                c->partial_obj_size(), c->live_obj_size(),
                c->data_size(), c->source_chunk(), c->destination_count());

#undef  CHUNK_IDX_FORMAT
#undef  CHUNK_DATA_FORMAT
}

void
print_generic_summary_data(ParallelCompactData& summary_data,
                           HeapWord* const beg_addr,
                           HeapWord* const end_addr)
{
  size_t total_words = 0;
  size_t i = summary_data.addr_to_chunk_idx(beg_addr);
  const size_t last = summary_data.addr_to_chunk_idx(end_addr);
  HeapWord* pdest = 0;

  while (i <= last) {
    ParallelCompactData::ChunkData* c = summary_data.chunk(i);
    if (c->data_size() != 0 || c->destination() != pdest) {
      print_generic_summary_chunk(i, c);
      total_words += c->data_size();
      pdest = c->destination();
    }
    ++i;
  }

  tty->print_cr("summary_data_bytes=" SIZE_FORMAT, total_words * HeapWordSize);
}

void
print_generic_summary_data(ParallelCompactData& summary_data,
                           SpaceInfo* space_info)
{
  for (unsigned int id = 0; id < PSParallelCompact::last_space_id; ++id) {
    const MutableSpace* space = space_info[id].space();
    print_generic_summary_data(summary_data, space->bottom(),
                               MAX2(space->top(), space_info[id].new_top()));
  }
}

void
print_initial_summary_chunk(size_t i,
                            const ParallelCompactData::ChunkData* c,
                            bool newline = true)
{
  tty->print(SIZE_FORMAT_W("5") " " PTR_FORMAT " "
             SIZE_FORMAT_W("5") " " SIZE_FORMAT_W("5") " "
             SIZE_FORMAT_W("5") " " SIZE_FORMAT_W("5") " %d",
             i, c->destination(),
             c->partial_obj_size(), c->live_obj_size(),
             c->data_size(), c->source_chunk(), c->destination_count());
  if (newline) tty->cr();
}

void
print_initial_summary_data(ParallelCompactData& summary_data,
                           const MutableSpace* space) {
  if (space->top() == space->bottom()) {
    return;
  }

  const size_t chunk_size = ParallelCompactData::ChunkSize;
  HeapWord* const top_aligned_up = summary_data.chunk_align_up(space->top());
  const size_t end_chunk = summary_data.addr_to_chunk_idx(top_aligned_up);
  const ParallelCompactData::ChunkData* c = summary_data.chunk(end_chunk - 1);
  HeapWord* end_addr = c->destination() + c->data_size();
  const size_t live_in_space = pointer_delta(end_addr, space->bottom());

  // Print (and count) the full chunks at the beginning of the space.
  size_t full_chunk_count = 0;
  size_t i = summary_data.addr_to_chunk_idx(space->bottom());
  while (i < end_chunk && summary_data.chunk(i)->data_size() == chunk_size) {
    print_initial_summary_chunk(i, summary_data.chunk(i));
    ++full_chunk_count;
    ++i;
  }

  size_t live_to_right = live_in_space - full_chunk_count * chunk_size;

  double max_reclaimed_ratio = 0.0;
  size_t max_reclaimed_ratio_chunk = 0;
  size_t max_dead_to_right = 0;
  size_t max_live_to_right = 0;

  // Print the 'reclaimed ratio' for chunks while there is something live in the
  // chunk or to the right of it.  The remaining chunks are empty (and
  // uninteresting), and computing the ratio will result in division by 0.
  while (i < end_chunk && live_to_right > 0) {
    c = summary_data.chunk(i);
    HeapWord* const chunk_addr = summary_data.chunk_to_addr(i);
    const size_t used_to_right = pointer_delta(space->top(), chunk_addr);
    const size_t dead_to_right = used_to_right - live_to_right;
    const double reclaimed_ratio = double(dead_to_right) / live_to_right;

    if (reclaimed_ratio > max_reclaimed_ratio) {
            max_reclaimed_ratio = reclaimed_ratio;
            max_reclaimed_ratio_chunk = i;
            max_dead_to_right = dead_to_right;
            max_live_to_right = live_to_right;
    }

    print_initial_summary_chunk(i, c, false);
    tty->print_cr(" %12.10f " SIZE_FORMAT_W("10") " " SIZE_FORMAT_W("10"),
                  reclaimed_ratio, dead_to_right, live_to_right);

    live_to_right -= c->data_size();
    ++i;
  }

  // Any remaining chunks are empty.  Print one more if there is one.
  if (i < end_chunk) {
    print_initial_summary_chunk(i, summary_data.chunk(i));
  }

  tty->print_cr("max:  " SIZE_FORMAT_W("4") " d2r=" SIZE_FORMAT_W("10") " "
                "l2r=" SIZE_FORMAT_W("10") " max_ratio=%14.12f",
                max_reclaimed_ratio_chunk, max_dead_to_right,
                max_live_to_right, max_reclaimed_ratio);
}

void
print_initial_summary_data(ParallelCompactData& summary_data,
                           SpaceInfo* space_info) {
  unsigned int id = PSParallelCompact::perm_space_id;
  const MutableSpace* space;
  do {
    space = space_info[id].space();
    print_initial_summary_data(summary_data, space);
  } while (++id < PSParallelCompact::eden_space_id);

  do {
    space = space_info[id].space();
    print_generic_summary_data(summary_data, space->bottom(), space->top());
  } while (++id < PSParallelCompact::last_space_id);
}
#endif  // #ifndef PRODUCT

#ifdef  ASSERT
size_t add_obj_count;
size_t add_obj_size;
size_t mark_bitmap_count;
size_t mark_bitmap_size;
#endif  // #ifdef ASSERT

ParallelCompactData::ParallelCompactData()
{
  _region_start = 0;

  _chunk_vspace = 0;
  _chunk_data = 0;
  _chunk_count = 0;

  _block_vspace = 0;
  _block_data = 0;
  _block_count = 0;
}

bool ParallelCompactData::initialize(MemRegion covered_region)
{
  _region_start = covered_region.start();
  const size_t region_size = covered_region.word_size();
  DEBUG_ONLY(_region_end = _region_start + region_size;)

  assert(chunk_align_down(_region_start) == _region_start,
         "region start not aligned");
  assert((region_size & ChunkSizeOffsetMask) == 0,
         "region size not a multiple of ChunkSize");

  bool result = initialize_chunk_data(region_size);

  // Initialize the block data if it will be used for updating pointers, or if
  // this is a debug build.
  if (!UseParallelOldGCChunkPointerCalc || trueInDebug) {
    result = result && initialize_block_data(region_size);
  }

  return result;
}

PSVirtualSpace*
ParallelCompactData::create_vspace(size_t count, size_t element_size)
{
  const size_t raw_bytes = count * element_size;
  const size_t page_sz = os::page_size_for_region(raw_bytes, raw_bytes, 10);
  const size_t granularity = os::vm_allocation_granularity();
  const size_t bytes = align_size_up(raw_bytes, MAX2(page_sz, granularity));

  const size_t rs_align = page_sz == (size_t) os::vm_page_size() ? 0 :
    MAX2(page_sz, granularity);
  ReservedSpace rs(bytes, rs_align, false);
  os::trace_page_sizes("par compact", raw_bytes, raw_bytes, page_sz, rs.base(),
                       rs.size());
  PSVirtualSpace* vspace = new PSVirtualSpace(rs, page_sz);
  if (vspace != 0) {
    if (vspace->expand_by(bytes)) {
      return vspace;
    }
    delete vspace;
  }

  return 0;
}

bool ParallelCompactData::initialize_chunk_data(size_t region_size)
{
  const size_t count = (region_size + ChunkSizeOffsetMask) >> Log2ChunkSize;
  _chunk_vspace = create_vspace(count, sizeof(ChunkData));
  if (_chunk_vspace != 0) {
    _chunk_data = (ChunkData*)_chunk_vspace->reserved_low_addr();
    _chunk_count = count;
    return true;
  }
  return false;
}

bool ParallelCompactData::initialize_block_data(size_t region_size)
{
  const size_t count = (region_size + BlockOffsetMask) >> Log2BlockSize;
  _block_vspace = create_vspace(count, sizeof(BlockData));
  if (_block_vspace != 0) {
    _block_data = (BlockData*)_block_vspace->reserved_low_addr();
    _block_count = count;
    return true;
  }
  return false;
}

void ParallelCompactData::clear()
{
  if (_block_data) {
    memset(_block_data, 0, _block_vspace->committed_size());
  }
  memset(_chunk_data, 0, _chunk_vspace->committed_size());
}

void ParallelCompactData::clear_range(size_t beg_chunk, size_t end_chunk) {
  assert(beg_chunk <= _chunk_count, "beg_chunk out of range");
  assert(end_chunk <= _chunk_count, "end_chunk out of range");
  assert(ChunkSize % BlockSize == 0, "ChunkSize not a multiple of BlockSize");

  const size_t chunk_cnt = end_chunk - beg_chunk;

  if (_block_data) {
    const size_t blocks_per_chunk = ChunkSize / BlockSize;
    const size_t beg_block = beg_chunk * blocks_per_chunk;
    const size_t block_cnt = chunk_cnt * blocks_per_chunk;
    memset(_block_data + beg_block, 0, block_cnt * sizeof(BlockData));
  }
  memset(_chunk_data + beg_chunk, 0, chunk_cnt * sizeof(ChunkData));
}

HeapWord* ParallelCompactData::partial_obj_end(size_t chunk_idx) const
{
  const ChunkData* cur_cp = chunk(chunk_idx);
  const ChunkData* const end_cp = chunk(chunk_count() - 1);

  HeapWord* result = chunk_to_addr(chunk_idx);
  if (cur_cp < end_cp) {
    do {
      result += cur_cp->partial_obj_size();
    } while (cur_cp->partial_obj_size() == ChunkSize && ++cur_cp < end_cp);
  }
  return result;
}

void ParallelCompactData::add_obj(HeapWord* addr, size_t len)
{
  const size_t obj_ofs = pointer_delta(addr, _region_start);
  const size_t beg_chunk = obj_ofs >> Log2ChunkSize;
  const size_t end_chunk = (obj_ofs + len - 1) >> Log2ChunkSize;

  DEBUG_ONLY(Atomic::inc_ptr(&add_obj_count);)
  DEBUG_ONLY(Atomic::add_ptr(len, &add_obj_size);)

  if (beg_chunk == end_chunk) {
    // All in one chunk.
    _chunk_data[beg_chunk].add_live_obj(len);
    return;
  }

  // First chunk.
  const size_t beg_ofs = chunk_offset(addr);
  _chunk_data[beg_chunk].add_live_obj(ChunkSize - beg_ofs);

  klassOop klass = ((oop)addr)->klass();
  // Middle chunks--completely spanned by this object.
  for (size_t chunk = beg_chunk + 1; chunk < end_chunk; ++chunk) {
    _chunk_data[chunk].set_partial_obj_size(ChunkSize);
    _chunk_data[chunk].set_partial_obj_addr(addr);
  }

  // Last chunk.
  const size_t end_ofs = chunk_offset(addr + len - 1);
  _chunk_data[end_chunk].set_partial_obj_size(end_ofs + 1);
  _chunk_data[end_chunk].set_partial_obj_addr(addr);
}

void
ParallelCompactData::summarize_dense_prefix(HeapWord* beg, HeapWord* end)
{
  assert(chunk_offset(beg) == 0, "not ChunkSize aligned");
  assert(chunk_offset(end) == 0, "not ChunkSize aligned");

  size_t cur_chunk = addr_to_chunk_idx(beg);
  const size_t end_chunk = addr_to_chunk_idx(end);
  HeapWord* addr = beg;
  while (cur_chunk < end_chunk) {
    _chunk_data[cur_chunk].set_destination(addr);
    _chunk_data[cur_chunk].set_destination_count(0);
    _chunk_data[cur_chunk].set_source_chunk(cur_chunk);
    _chunk_data[cur_chunk].set_data_location(addr);

    // Update live_obj_size so the chunk appears completely full.
    size_t live_size = ChunkSize - _chunk_data[cur_chunk].partial_obj_size();
    _chunk_data[cur_chunk].set_live_obj_size(live_size);

    ++cur_chunk;
    addr += ChunkSize;
  }
}

bool ParallelCompactData::summarize(HeapWord* target_beg, HeapWord* target_end,
                                    HeapWord* source_beg, HeapWord* source_end,
                                    HeapWord** target_next,
                                    HeapWord** source_next) {
  // This is too strict.
  // assert(chunk_offset(source_beg) == 0, "not ChunkSize aligned");

  if (TraceParallelOldGCSummaryPhase) {
    tty->print_cr("tb=" PTR_FORMAT " te=" PTR_FORMAT " "
                  "sb=" PTR_FORMAT " se=" PTR_FORMAT " "
                  "tn=" PTR_FORMAT " sn=" PTR_FORMAT,
                  target_beg, target_end,
                  source_beg, source_end,
                  target_next != 0 ? *target_next : (HeapWord*) 0,
                  source_next != 0 ? *source_next : (HeapWord*) 0);
  }

  size_t cur_chunk = addr_to_chunk_idx(source_beg);
  const size_t end_chunk = addr_to_chunk_idx(chunk_align_up(source_end));

  HeapWord *dest_addr = target_beg;
  while (cur_chunk < end_chunk) {
    size_t words = _chunk_data[cur_chunk].data_size();

#if     1
    assert(pointer_delta(target_end, dest_addr) >= words,
           "source region does not fit into target region");
#else
    // XXX - need some work on the corner cases here.  If the chunk does not
    // fit, then must either make sure any partial_obj from the chunk fits, or
    // 'undo' the initial part of the partial_obj that is in the previous chunk.
    if (dest_addr + words >= target_end) {
      // Let the caller know where to continue.
      *target_next = dest_addr;
      *source_next = chunk_to_addr(cur_chunk);
      return false;
    }
#endif  // #if 1

    _chunk_data[cur_chunk].set_destination(dest_addr);

    // Set the destination_count for cur_chunk, and if necessary, update
    // source_chunk for a destination chunk.  The source_chunk field is updated
    // if cur_chunk is the first (left-most) chunk to be copied to a destination
    // chunk.
    //
    // The destination_count calculation is a bit subtle.  A chunk that has data
    // that compacts into itself does not count itself as a destination.  This
    // maintains the invariant that a zero count means the chunk is available
    // and can be claimed and then filled.
    if (words > 0) {
      HeapWord* const last_addr = dest_addr + words - 1;
      const size_t dest_chunk_1 = addr_to_chunk_idx(dest_addr);
      const size_t dest_chunk_2 = addr_to_chunk_idx(last_addr);
#if     0
      // Initially assume that the destination chunks will be the same and
      // adjust the value below if necessary.  Under this assumption, if
      // cur_chunk == dest_chunk_2, then cur_chunk will be compacted completely
      // into itself.
      uint destination_count = cur_chunk == dest_chunk_2 ? 0 : 1;
      if (dest_chunk_1 != dest_chunk_2) {
        // Destination chunks differ; adjust destination_count.
        destination_count += 1;
        // Data from cur_chunk will be copied to the start of dest_chunk_2.
        _chunk_data[dest_chunk_2].set_source_chunk(cur_chunk);
      } else if (chunk_offset(dest_addr) == 0) {
        // Data from cur_chunk will be copied to the start of the destination
        // chunk.
        _chunk_data[dest_chunk_1].set_source_chunk(cur_chunk);
      }
#else
      // Initially assume that the destination chunks will be different and
      // adjust the value below if necessary.  Under this assumption, if
      // cur_chunk == dest_chunk2, then cur_chunk will be compacted partially
      // into dest_chunk_1 and partially into itself.
      uint destination_count = cur_chunk == dest_chunk_2 ? 1 : 2;
      if (dest_chunk_1 != dest_chunk_2) {
        // Data from cur_chunk will be copied to the start of dest_chunk_2.
        _chunk_data[dest_chunk_2].set_source_chunk(cur_chunk);
      } else {
        // Destination chunks are the same; adjust destination_count.
        destination_count -= 1;
        if (chunk_offset(dest_addr) == 0) {
          // Data from cur_chunk will be copied to the start of the destination
          // chunk.
          _chunk_data[dest_chunk_1].set_source_chunk(cur_chunk);
        }
      }
#endif  // #if 0

      _chunk_data[cur_chunk].set_destination_count(destination_count);
      _chunk_data[cur_chunk].set_data_location(chunk_to_addr(cur_chunk));
      dest_addr += words;
    }

    ++cur_chunk;
  }

  *target_next = dest_addr;
  return true;
}

bool ParallelCompactData::partial_obj_ends_in_block(size_t block_index) {
  HeapWord* block_addr = block_to_addr(block_index);
  HeapWord* block_end_addr = block_addr + BlockSize;
  size_t chunk_index = addr_to_chunk_idx(block_addr);
  HeapWord* partial_obj_end_addr = partial_obj_end(chunk_index);

  // An object that ends at the end of the block, ends
  // in the block (the last word of the object is to
  // the left of the end).
  if ((block_addr < partial_obj_end_addr) &&
      (partial_obj_end_addr <= block_end_addr)) {
    return true;
  }

  return false;
}

HeapWord* ParallelCompactData::calc_new_pointer(HeapWord* addr) {
  HeapWord* result = NULL;
  if (UseParallelOldGCChunkPointerCalc) {
    result = chunk_calc_new_pointer(addr);
  } else {
    result = block_calc_new_pointer(addr);
  }
  return result;
}

// This method is overly complicated (expensive) to be called
// for every reference.
// Try to restructure this so that a NULL is returned if
// the object is dead.  But don't wast the cycles to explicitly check
// that it is dead since only live objects should be passed in.

HeapWord* ParallelCompactData::chunk_calc_new_pointer(HeapWord* addr) {
  assert(addr != NULL, "Should detect NULL oop earlier");
  assert(PSParallelCompact::gc_heap()->is_in(addr), "addr not in heap");
#ifdef ASSERT
  if (PSParallelCompact::mark_bitmap()->is_unmarked(addr)) {
    gclog_or_tty->print_cr("calc_new_pointer:: addr " PTR_FORMAT, addr);
  }
#endif
  assert(PSParallelCompact::mark_bitmap()->is_marked(addr), "obj not marked");

  // Chunk covering the object.
  size_t chunk_index = addr_to_chunk_idx(addr);
  const ChunkData* const chunk_ptr = chunk(chunk_index);
  HeapWord* const chunk_addr = chunk_align_down(addr);

  assert(addr < chunk_addr + ChunkSize, "Chunk does not cover object");
  assert(addr_to_chunk_ptr(chunk_addr) == chunk_ptr, "sanity check");

  HeapWord* result = chunk_ptr->destination();

  // If all the data in the chunk is live, then the new location of the object
  // can be calculated from the destination of the chunk plus the offset of the
  // object in the chunk.
  if (chunk_ptr->data_size() == ChunkSize) {
    result += pointer_delta(addr, chunk_addr);
    return result;
  }

  // The new location of the object is
  //    chunk destination +
  //    size of the partial object extending onto the chunk +
  //    sizes of the live objects in the Chunk that are to the left of addr
  const size_t partial_obj_size = chunk_ptr->partial_obj_size();
  HeapWord* const search_start = chunk_addr + partial_obj_size;

  const ParMarkBitMap* bitmap = PSParallelCompact::mark_bitmap();
  size_t live_to_left = bitmap->live_words_in_range(search_start, oop(addr));

  result += partial_obj_size + live_to_left;
  assert(result <= addr, "object cannot move to the right");
  return result;
}

HeapWord* ParallelCompactData::block_calc_new_pointer(HeapWord* addr) {
  assert(addr != NULL, "Should detect NULL oop earlier");
  assert(PSParallelCompact::gc_heap()->is_in(addr), "addr not in heap");
#ifdef ASSERT
  if (PSParallelCompact::mark_bitmap()->is_unmarked(addr)) {
    gclog_or_tty->print_cr("calc_new_pointer:: addr " PTR_FORMAT, addr);
  }
#endif
  assert(PSParallelCompact::mark_bitmap()->is_marked(addr), "obj not marked");

  // Chunk covering the object.
  size_t chunk_index = addr_to_chunk_idx(addr);
  const ChunkData* const chunk_ptr = chunk(chunk_index);
  HeapWord* const chunk_addr = chunk_align_down(addr);

  assert(addr < chunk_addr + ChunkSize, "Chunk does not cover object");
  assert(addr_to_chunk_ptr(chunk_addr) == chunk_ptr, "sanity check");

  HeapWord* result = chunk_ptr->destination();

  // If all the data in the chunk is live, then the new location of the object
  // can be calculated from the destination of the chunk plus the offset of the
  // object in the chunk.
  if (chunk_ptr->data_size() == ChunkSize) {
    result += pointer_delta(addr, chunk_addr);
    return result;
  }

  // The new location of the object is
  //    chunk destination +
  //    block offset +
  //    sizes of the live objects in the Block that are to the left of addr
  const size_t block_offset = addr_to_block_ptr(addr)->offset();
  HeapWord* const search_start = chunk_addr + block_offset;

  const ParMarkBitMap* bitmap = PSParallelCompact::mark_bitmap();
  size_t live_to_left = bitmap->live_words_in_range(search_start, oop(addr));

  result += block_offset + live_to_left;
  assert(result <= addr, "object cannot move to the right");
  assert(result == chunk_calc_new_pointer(addr), "Should match");
  return result;
}

klassOop ParallelCompactData::calc_new_klass(klassOop old_klass) {
  klassOop updated_klass;
  if (PSParallelCompact::should_update_klass(old_klass)) {
    updated_klass = (klassOop) calc_new_pointer(old_klass);
  } else {
    updated_klass = old_klass;
  }

  return updated_klass;
}

#ifdef  ASSERT
void ParallelCompactData::verify_clear(const PSVirtualSpace* vspace)
{
  const size_t* const beg = (const size_t*)vspace->committed_low_addr();
  const size_t* const end = (const size_t*)vspace->committed_high_addr();
  for (const size_t* p = beg; p < end; ++p) {
    assert(*p == 0, "not zero");
  }
}

void ParallelCompactData::verify_clear()
{
  verify_clear(_chunk_vspace);
  verify_clear(_block_vspace);
}
#endif  // #ifdef ASSERT

#ifdef NOT_PRODUCT
ParallelCompactData::ChunkData* debug_chunk(size_t chunk_index) {
  ParallelCompactData& sd = PSParallelCompact::summary_data();
  return sd.chunk(chunk_index);
}
#endif

elapsedTimer        PSParallelCompact::_accumulated_time;
unsigned int        PSParallelCompact::_total_invocations = 0;
unsigned int        PSParallelCompact::_maximum_compaction_gc_num = 0;
jlong               PSParallelCompact::_time_of_last_gc = 0;
CollectorCounters*  PSParallelCompact::_counters = NULL;
ParMarkBitMap       PSParallelCompact::_mark_bitmap;
ParallelCompactData PSParallelCompact::_summary_data;

PSParallelCompact::IsAliveClosure PSParallelCompact::_is_alive_closure;
PSParallelCompact::AdjustPointerClosure PSParallelCompact::_adjust_root_pointer_closure(true);
PSParallelCompact::AdjustPointerClosure PSParallelCompact::_adjust_pointer_closure(false);

void PSParallelCompact::KeepAliveClosure::do_oop(oop* p) {
#ifdef VALIDATE_MARK_SWEEP
  if (ValidateMarkSweep) {
    if (!Universe::heap()->is_in_reserved(p)) {
      _root_refs_stack->push(p);
    } else {
      _other_refs_stack->push(p);
    }
  }
#endif
  mark_and_push(_compaction_manager, p);
}

void PSParallelCompact::mark_and_follow(ParCompactionManager* cm,
                                        oop* p) {
  assert(Universe::heap()->is_in_reserved(p),
         "we should only be traversing objects here");
  oop m = *p;
  if (m != NULL && mark_bitmap()->is_unmarked(m)) {
    if (mark_obj(m)) {
      m->follow_contents(cm);  // Follow contents of the marked object
    }
  }
}

// Anything associated with this variable is temporary.

void PSParallelCompact::mark_and_push_internal(ParCompactionManager* cm,
                                               oop* p) {
  // Push marked object, contents will be followed later
  oop m = *p;
  if (mark_obj(m)) {
    // This thread marked the object and
    // owns the subsequent processing of it.
    cm->save_for_scanning(m);
  }
}

void PSParallelCompact::post_initialize() {
  ParallelScavengeHeap* heap = gc_heap();
  assert(heap->kind() == CollectedHeap::ParallelScavengeHeap, "Sanity");

  MemRegion mr = heap->reserved_region();
  _ref_processor = ReferenceProcessor::create_ref_processor(
    mr,                         // span
    true,                       // atomic_discovery
    true,                       // mt_discovery
    &_is_alive_closure,
    ParallelGCThreads,
    ParallelRefProcEnabled);
  _counters = new CollectorCounters("PSParallelCompact", 1);

  // Initialize static fields in ParCompactionManager.
  ParCompactionManager::initialize(mark_bitmap());
}

bool PSParallelCompact::initialize() {
  ParallelScavengeHeap* heap = gc_heap();
  assert(heap->kind() == CollectedHeap::ParallelScavengeHeap, "Sanity");
  MemRegion mr = heap->reserved_region();

  // Was the old gen get allocated successfully?
  if (!heap->old_gen()->is_allocated()) {
    return false;
  }

  initialize_space_info();
  initialize_dead_wood_limiter();

  if (!_mark_bitmap.initialize(mr)) {
    vm_shutdown_during_initialization("Unable to allocate bit map for "
      "parallel garbage collection for the requested heap size.");
    return false;
  }

  if (!_summary_data.initialize(mr)) {
    vm_shutdown_during_initialization("Unable to allocate tables for "
      "parallel garbage collection for the requested heap size.");
    return false;
  }

  return true;
}

void PSParallelCompact::initialize_space_info()
{
  memset(&_space_info, 0, sizeof(_space_info));

  ParallelScavengeHeap* heap = gc_heap();
  PSYoungGen* young_gen = heap->young_gen();
  MutableSpace* perm_space = heap->perm_gen()->object_space();

  _space_info[perm_space_id].set_space(perm_space);
  _space_info[old_space_id].set_space(heap->old_gen()->object_space());
  _space_info[eden_space_id].set_space(young_gen->eden_space());
  _space_info[from_space_id].set_space(young_gen->from_space());
  _space_info[to_space_id].set_space(young_gen->to_space());

  _space_info[perm_space_id].set_start_array(heap->perm_gen()->start_array());
  _space_info[old_space_id].set_start_array(heap->old_gen()->start_array());

  _space_info[perm_space_id].set_min_dense_prefix(perm_space->top());
  if (TraceParallelOldGCDensePrefix) {
    tty->print_cr("perm min_dense_prefix=" PTR_FORMAT,
                  _space_info[perm_space_id].min_dense_prefix());
  }
}

void PSParallelCompact::initialize_dead_wood_limiter()
{
  const size_t max = 100;
  _dwl_mean = double(MIN2(ParallelOldDeadWoodLimiterMean, max)) / 100.0;
  _dwl_std_dev = double(MIN2(ParallelOldDeadWoodLimiterStdDev, max)) / 100.0;
  _dwl_first_term = 1.0 / (sqrt(2.0 * M_PI) * _dwl_std_dev);
  DEBUG_ONLY(_dwl_initialized = true;)
  _dwl_adjustment = normal_distribution(1.0);
}

// Simple class for storing info about the heap at the start of GC, to be used
// after GC for comparison/printing.
class PreGCValues {
public:
  PreGCValues() { }
  PreGCValues(ParallelScavengeHeap* heap) { fill(heap); }

  void fill(ParallelScavengeHeap* heap) {
    _heap_used      = heap->used();
    _young_gen_used = heap->young_gen()->used_in_bytes();
    _old_gen_used   = heap->old_gen()->used_in_bytes();
    _perm_gen_used  = heap->perm_gen()->used_in_bytes();
  };

  size_t heap_used() const      { return _heap_used; }
  size_t young_gen_used() const { return _young_gen_used; }
  size_t old_gen_used() const   { return _old_gen_used; }
  size_t perm_gen_used() const  { return _perm_gen_used; }

private:
  size_t _heap_used;
  size_t _young_gen_used;
  size_t _old_gen_used;
  size_t _perm_gen_used;
};

void
PSParallelCompact::clear_data_covering_space(SpaceId id)
{
  // At this point, top is the value before GC, new_top() is the value that will
  // be set at the end of GC.  The marking bitmap is cleared to top; nothing
  // should be marked above top.  The summary data is cleared to the larger of
  // top & new_top.
  MutableSpace* const space = _space_info[id].space();
  HeapWord* const bot = space->bottom();
  HeapWord* const top = space->top();
  HeapWord* const max_top = MAX2(top, _space_info[id].new_top());

  const idx_t beg_bit = _mark_bitmap.addr_to_bit(bot);
  const idx_t end_bit = BitMap::word_align_up(_mark_bitmap.addr_to_bit(top));
  _mark_bitmap.clear_range(beg_bit, end_bit);

  const size_t beg_chunk = _summary_data.addr_to_chunk_idx(bot);
  const size_t end_chunk =
    _summary_data.addr_to_chunk_idx(_summary_data.chunk_align_up(max_top));
  _summary_data.clear_range(beg_chunk, end_chunk);
}

void PSParallelCompact::pre_compact(PreGCValues* pre_gc_values)
{
  // Update the from & to space pointers in space_info, since they are swapped
  // at each young gen gc.  Do the update unconditionally (even though a
  // promotion failure does not swap spaces) because an unknown number of minor
  // collections will have swapped the spaces an unknown number of times.
  TraceTime tm("pre compact", print_phases(), true, gclog_or_tty);
  ParallelScavengeHeap* heap = gc_heap();
  _space_info[from_space_id].set_space(heap->young_gen()->from_space());
  _space_info[to_space_id].set_space(heap->young_gen()->to_space());

  pre_gc_values->fill(heap);

  ParCompactionManager::reset();
  NOT_PRODUCT(_mark_bitmap.reset_counters());
  DEBUG_ONLY(add_obj_count = add_obj_size = 0;)
  DEBUG_ONLY(mark_bitmap_count = mark_bitmap_size = 0;)

  // Increment the invocation count
  heap->increment_total_collections();

  // We need to track unique mark sweep invocations as well.
  _total_invocations++;

  if (PrintHeapAtGC) {
    Universe::print_heap_before_gc();
  }

  // Fill in TLABs
  heap->accumulate_statistics_all_tlabs();
  heap->ensure_parsability(true);  // retire TLABs

  if (VerifyBeforeGC && heap->total_collections() >= VerifyGCStartAt) {
    HandleMark hm;  // Discard invalid handles created during verification
    gclog_or_tty->print(" VerifyBeforeGC:");
    Universe::verify(true);
  }

  // Verify object start arrays
  if (VerifyObjectStartArray &&
      VerifyBeforeGC) {
    heap->old_gen()->verify_object_start_array();
    heap->perm_gen()->verify_object_start_array();
  }

  DEBUG_ONLY(mark_bitmap()->verify_clear();)
  DEBUG_ONLY(summary_data().verify_clear();)
}

void PSParallelCompact::post_compact()
{
  TraceTime tm("post compact", print_phases(), true, gclog_or_tty);

  // Clear the marking bitmap and summary data and update top() in each space.
  for (unsigned int id = perm_space_id; id < last_space_id; ++id) {
    clear_data_covering_space(SpaceId(id));
    _space_info[id].space()->set_top(_space_info[id].new_top());
  }

  MutableSpace* const eden_space = _space_info[eden_space_id].space();
  MutableSpace* const from_space = _space_info[from_space_id].space();
  MutableSpace* const to_space   = _space_info[to_space_id].space();

  ParallelScavengeHeap* heap = gc_heap();
  bool eden_empty = eden_space->is_empty();
  if (!eden_empty) {
    eden_empty = absorb_live_data_from_eden(heap->size_policy(),
                                            heap->young_gen(), heap->old_gen());
  }

  // Update heap occupancy information which is used as input to the soft ref
  // clearing policy at the next gc.
  Universe::update_heap_info_at_gc();

  bool young_gen_empty = eden_empty && from_space->is_empty() &&
    to_space->is_empty();

  BarrierSet* bs = heap->barrier_set();
  if (bs->is_a(BarrierSet::ModRef)) {
    ModRefBarrierSet* modBS = (ModRefBarrierSet*)bs;
    MemRegion old_mr = heap->old_gen()->reserved();
    MemRegion perm_mr = heap->perm_gen()->reserved();
    assert(perm_mr.end() <= old_mr.start(), "Generations out of order");

    if (young_gen_empty) {
      modBS->clear(MemRegion(perm_mr.start(), old_mr.end()));
    } else {
      modBS->invalidate(MemRegion(perm_mr.start(), old_mr.end()));
    }
  }

  Threads::gc_epilogue();
  CodeCache::gc_epilogue();

  COMPILER2_PRESENT(DerivedPointerTable::update_pointers());

  ref_processor()->enqueue_discovered_references(NULL);

  // Update time of last GC
  reset_millis_since_last_gc();
}

HeapWord*
PSParallelCompact::compute_dense_prefix_via_density(const SpaceId id,
                                                    bool maximum_compaction)
{
  const size_t chunk_size = ParallelCompactData::ChunkSize;
  const ParallelCompactData& sd = summary_data();

  const MutableSpace* const space = _space_info[id].space();
  HeapWord* const top_aligned_up = sd.chunk_align_up(space->top());
  const ChunkData* const beg_cp = sd.addr_to_chunk_ptr(space->bottom());
  const ChunkData* const end_cp = sd.addr_to_chunk_ptr(top_aligned_up);

  // Skip full chunks at the beginning of the space--they are necessarily part
  // of the dense prefix.
  size_t full_count = 0;
  const ChunkData* cp;
  for (cp = beg_cp; cp < end_cp && cp->data_size() == chunk_size; ++cp) {
    ++full_count;
  }

  assert(total_invocations() >= _maximum_compaction_gc_num, "sanity");
  const size_t gcs_since_max = total_invocations() - _maximum_compaction_gc_num;
  const bool interval_ended = gcs_since_max > HeapMaximumCompactionInterval;
  if (maximum_compaction || cp == end_cp || interval_ended) {
    _maximum_compaction_gc_num = total_invocations();
    return sd.chunk_to_addr(cp);
  }

  HeapWord* const new_top = _space_info[id].new_top();
  const size_t space_live = pointer_delta(new_top, space->bottom());
  const size_t space_used = space->used_in_words();
  const size_t space_capacity = space->capacity_in_words();

  const double cur_density = double(space_live) / space_capacity;
  const double deadwood_density =
    (1.0 - cur_density) * (1.0 - cur_density) * cur_density * cur_density;
  const size_t deadwood_goal = size_t(space_capacity * deadwood_density);

  if (TraceParallelOldGCDensePrefix) {
    tty->print_cr("cur_dens=%5.3f dw_dens=%5.3f dw_goal=" SIZE_FORMAT,
                  cur_density, deadwood_density, deadwood_goal);
    tty->print_cr("space_live=" SIZE_FORMAT " " "space_used=" SIZE_FORMAT " "
                  "space_cap=" SIZE_FORMAT,
                  space_live, space_used,
                  space_capacity);
  }

  // XXX - Use binary search?
  HeapWord* dense_prefix = sd.chunk_to_addr(cp);
  const ChunkData* full_cp = cp;
  const ChunkData* const top_cp = sd.addr_to_chunk_ptr(space->top() - 1);
  while (cp < end_cp) {
    HeapWord* chunk_destination = cp->destination();
    const size_t cur_deadwood = pointer_delta(dense_prefix, chunk_destination);
    if (TraceParallelOldGCDensePrefix && Verbose) {
      tty->print_cr("c#=" SIZE_FORMAT_W("04") " dst=" PTR_FORMAT " "
                    "dp=" SIZE_FORMAT_W("08") " " "cdw=" SIZE_FORMAT_W("08"),
                    sd.chunk(cp), chunk_destination,
                    dense_prefix, cur_deadwood);
    }

    if (cur_deadwood >= deadwood_goal) {
      // Found the chunk that has the correct amount of deadwood to the left.
      // This typically occurs after crossing a fairly sparse set of chunks, so
      // iterate backwards over those sparse chunks, looking for the chunk that
      // has the lowest density of live objects 'to the right.'
      size_t space_to_left = sd.chunk(cp) * chunk_size;
      size_t live_to_left = space_to_left - cur_deadwood;
      size_t space_to_right = space_capacity - space_to_left;
      size_t live_to_right = space_live - live_to_left;
      double density_to_right = double(live_to_right) / space_to_right;
      while (cp > full_cp) {
        --cp;
        const size_t prev_chunk_live_to_right = live_to_right - cp->data_size();
        const size_t prev_chunk_space_to_right = space_to_right + chunk_size;
        double prev_chunk_density_to_right =
          double(prev_chunk_live_to_right) / prev_chunk_space_to_right;
        if (density_to_right <= prev_chunk_density_to_right) {
          return dense_prefix;
        }
        if (TraceParallelOldGCDensePrefix && Verbose) {
          tty->print_cr("backing up from c=" SIZE_FORMAT_W("4") " d2r=%10.8f "
                        "pc_d2r=%10.8f", sd.chunk(cp), density_to_right,
                        prev_chunk_density_to_right);
        }
        dense_prefix -= chunk_size;
        live_to_right = prev_chunk_live_to_right;
        space_to_right = prev_chunk_space_to_right;
        density_to_right = prev_chunk_density_to_right;
      }
      return dense_prefix;
    }

    dense_prefix += chunk_size;
    ++cp;
  }

  return dense_prefix;
}

#ifndef PRODUCT
void PSParallelCompact::print_dense_prefix_stats(const char* const algorithm,
                                                 const SpaceId id,
                                                 const bool maximum_compaction,
                                                 HeapWord* const addr)
{
  const size_t chunk_idx = summary_data().addr_to_chunk_idx(addr);
  ChunkData* const cp = summary_data().chunk(chunk_idx);
  const MutableSpace* const space = _space_info[id].space();
  HeapWord* const new_top = _space_info[id].new_top();

  const size_t space_live = pointer_delta(new_top, space->bottom());
  const size_t dead_to_left = pointer_delta(addr, cp->destination());
  const size_t space_cap = space->capacity_in_words();
  const double dead_to_left_pct = double(dead_to_left) / space_cap;
  const size_t live_to_right = new_top - cp->destination();
  const size_t dead_to_right = space->top() - addr - live_to_right;

  tty->print_cr("%s=" PTR_FORMAT " dpc=" SIZE_FORMAT_W("05") " "
                "spl=" SIZE_FORMAT " "
                "d2l=" SIZE_FORMAT " d2l%%=%6.4f "
                "d2r=" SIZE_FORMAT " l2r=" SIZE_FORMAT
                " ratio=%10.8f",
                algorithm, addr, chunk_idx,
                space_live,
                dead_to_left, dead_to_left_pct,
                dead_to_right, live_to_right,
                double(dead_to_right) / live_to_right);
}
#endif  // #ifndef PRODUCT

// Return a fraction indicating how much of the generation can be treated as
// "dead wood" (i.e., not reclaimed).  The function uses a normal distribution
// based on the density of live objects in the generation to determine a limit,
// which is then adjusted so the return value is min_percent when the density is
// 1.
//
// The following table shows some return values for a different values of the
// standard deviation (ParallelOldDeadWoodLimiterStdDev); the mean is 0.5 and
// min_percent is 1.
//
//                          fraction allowed as dead wood
//         -----------------------------------------------------------------
// density std_dev=70 std_dev=75 std_dev=80 std_dev=85 std_dev=90 std_dev=95
// ------- ---------- ---------- ---------- ---------- ---------- ----------
// 0.00000 0.01000000 0.01000000 0.01000000 0.01000000 0.01000000 0.01000000
// 0.05000 0.03193096 0.02836880 0.02550828 0.02319280 0.02130337 0.01974941
// 0.10000 0.05247504 0.04547452 0.03988045 0.03537016 0.03170171 0.02869272
// 0.15000 0.07135702 0.06111390 0.05296419 0.04641639 0.04110601 0.03676066
// 0.20000 0.08831616 0.07509618 0.06461766 0.05622444 0.04943437 0.04388975
// 0.25000 0.10311208 0.08724696 0.07471205 0.06469760 0.05661313 0.05002313
// 0.30000 0.11553050 0.09741183 0.08313394 0.07175114 0.06257797 0.05511132
// 0.35000 0.12538832 0.10545958 0.08978741 0.07731366 0.06727491 0.05911289
// 0.40000 0.13253818 0.11128511 0.09459590 0.08132834 0.07066107 0.06199500
// 0.45000 0.13687208 0.11481163 0.09750361 0.08375387 0.07270534 0.06373386
// 0.50000 0.13832410 0.11599237 0.09847664 0.08456518 0.07338887 0.06431510
// 0.55000 0.13687208 0.11481163 0.09750361 0.08375387 0.07270534 0.06373386
// 0.60000 0.13253818 0.11128511 0.09459590 0.08132834 0.07066107 0.06199500
// 0.65000 0.12538832 0.10545958 0.08978741 0.07731366 0.06727491 0.05911289
// 0.70000 0.11553050 0.09741183 0.08313394 0.07175114 0.06257797 0.05511132
// 0.75000 0.10311208 0.08724696 0.07471205 0.06469760 0.05661313 0.05002313
// 0.80000 0.08831616 0.07509618 0.06461766 0.05622444 0.04943437 0.04388975
// 0.85000 0.07135702 0.06111390 0.05296419 0.04641639 0.04110601 0.03676066
// 0.90000 0.05247504 0.04547452 0.03988045 0.03537016 0.03170171 0.02869272
// 0.95000 0.03193096 0.02836880 0.02550828 0.02319280 0.02130337 0.01974941
// 1.00000 0.01000000 0.01000000 0.01000000 0.01000000 0.01000000 0.01000000

double PSParallelCompact::dead_wood_limiter(double density, size_t min_percent)
{
  assert(_dwl_initialized, "uninitialized");

  // The raw limit is the value of the normal distribution at x = density.
  const double raw_limit = normal_distribution(density);

  // Adjust the raw limit so it becomes the minimum when the density is 1.
  //
  // First subtract the adjustment value (which is simply the precomputed value
  // normal_distribution(1.0)); this yields a value of 0 when the density is 1.
  // Then add the minimum value, so the minimum is returned when the density is
  // 1.  Finally, prevent negative values, which occur when the mean is not 0.5.
  const double min = double(min_percent) / 100.0;
  const double limit = raw_limit - _dwl_adjustment + min;
  return MAX2(limit, 0.0);
}

ParallelCompactData::ChunkData*
PSParallelCompact::first_dead_space_chunk(const ChunkData* beg,
                                          const ChunkData* end)
{
  const size_t chunk_size = ParallelCompactData::ChunkSize;
  ParallelCompactData& sd = summary_data();
  size_t left = sd.chunk(beg);
  size_t right = end > beg ? sd.chunk(end) - 1 : left;

  // Binary search.
  while (left < right) {
    // Equivalent to (left + right) / 2, but does not overflow.
    const size_t middle = left + (right - left) / 2;
    ChunkData* const middle_ptr = sd.chunk(middle);
    HeapWord* const dest = middle_ptr->destination();
    HeapWord* const addr = sd.chunk_to_addr(middle);
    assert(dest != NULL, "sanity");
    assert(dest <= addr, "must move left");

    if (middle > left && dest < addr) {
      right = middle - 1;
    } else if (middle < right && middle_ptr->data_size() == chunk_size) {
      left = middle + 1;
    } else {
      return middle_ptr;
    }
  }
  return sd.chunk(left);
}

ParallelCompactData::ChunkData*
PSParallelCompact::dead_wood_limit_chunk(const ChunkData* beg,
                                         const ChunkData* end,
                                         size_t dead_words)
{
  ParallelCompactData& sd = summary_data();
  size_t left = sd.chunk(beg);
  size_t right = end > beg ? sd.chunk(end) - 1 : left;

  // Binary search.
  while (left < right) {
    // Equivalent to (left + right) / 2, but does not overflow.
    const size_t middle = left + (right - left) / 2;
    ChunkData* const middle_ptr = sd.chunk(middle);
    HeapWord* const dest = middle_ptr->destination();
    HeapWord* const addr = sd.chunk_to_addr(middle);
    assert(dest != NULL, "sanity");
    assert(dest <= addr, "must move left");

    const size_t dead_to_left = pointer_delta(addr, dest);
    if (middle > left && dead_to_left > dead_words) {
      right = middle - 1;
    } else if (middle < right && dead_to_left < dead_words) {
      left = middle + 1;
    } else {
      return middle_ptr;
    }
  }
  return sd.chunk(left);
}

// The result is valid during the summary phase, after the initial summarization
// of each space into itself, and before final summarization.
inline double
PSParallelCompact::reclaimed_ratio(const ChunkData* const cp,
                                   HeapWord* const bottom,
                                   HeapWord* const top,
                                   HeapWord* const new_top)
{
  ParallelCompactData& sd = summary_data();

  assert(cp != NULL, "sanity");
  assert(bottom != NULL, "sanity");
  assert(top != NULL, "sanity");
  assert(new_top != NULL, "sanity");
  assert(top >= new_top, "summary data problem?");
  assert(new_top > bottom, "space is empty; should not be here");
  assert(new_top >= cp->destination(), "sanity");
  assert(top >= sd.chunk_to_addr(cp), "sanity");

  HeapWord* const destination = cp->destination();
  const size_t dense_prefix_live  = pointer_delta(destination, bottom);
  const size_t compacted_region_live = pointer_delta(new_top, destination);
  const size_t compacted_region_used = pointer_delta(top, sd.chunk_to_addr(cp));
  const size_t reclaimable = compacted_region_used - compacted_region_live;

  const double divisor = dense_prefix_live + 1.25 * compacted_region_live;
  return double(reclaimable) / divisor;
}

// Return the address of the end of the dense prefix, a.k.a. the start of the
// compacted region.  The address is always on a chunk boundary.
//
// Completely full chunks at the left are skipped, since no compaction can occur
// in those chunks.  Then the maximum amount of dead wood to allow is computed,
// based on the density (amount live / capacity) of the generation; the chunk
// with approximately that amount of dead space to the left is identified as the
// limit chunk.  Chunks between the last completely full chunk and the limit
// chunk are scanned and the one that has the best (maximum) reclaimed_ratio()
// is selected.
HeapWord*
PSParallelCompact::compute_dense_prefix(const SpaceId id,
                                        bool maximum_compaction)
{
  const size_t chunk_size = ParallelCompactData::ChunkSize;
  const ParallelCompactData& sd = summary_data();

  const MutableSpace* const space = _space_info[id].space();
  HeapWord* const top = space->top();
  HeapWord* const top_aligned_up = sd.chunk_align_up(top);
  HeapWord* const new_top = _space_info[id].new_top();
  HeapWord* const new_top_aligned_up = sd.chunk_align_up(new_top);
  HeapWord* const bottom = space->bottom();
  const ChunkData* const beg_cp = sd.addr_to_chunk_ptr(bottom);
  const ChunkData* const top_cp = sd.addr_to_chunk_ptr(top_aligned_up);
  const ChunkData* const new_top_cp = sd.addr_to_chunk_ptr(new_top_aligned_up);

  // Skip full chunks at the beginning of the space--they are necessarily part
  // of the dense prefix.
  const ChunkData* const full_cp = first_dead_space_chunk(beg_cp, new_top_cp);
  assert(full_cp->destination() == sd.chunk_to_addr(full_cp) ||
         space->is_empty(), "no dead space allowed to the left");
  assert(full_cp->data_size() < chunk_size || full_cp == new_top_cp - 1,
         "chunk must have dead space");

  // The gc number is saved whenever a maximum compaction is done, and used to
  // determine when the maximum compaction interval has expired.  This avoids
  // successive max compactions for different reasons.
  assert(total_invocations() >= _maximum_compaction_gc_num, "sanity");
  const size_t gcs_since_max = total_invocations() - _maximum_compaction_gc_num;
  const bool interval_ended = gcs_since_max > HeapMaximumCompactionInterval ||
    total_invocations() == HeapFirstMaximumCompactionCount;
  if (maximum_compaction || full_cp == top_cp || interval_ended) {
    _maximum_compaction_gc_num = total_invocations();
    return sd.chunk_to_addr(full_cp);
  }

  const size_t space_live = pointer_delta(new_top, bottom);
  const size_t space_used = space->used_in_words();
  const size_t space_capacity = space->capacity_in_words();

  const double density = double(space_live) / double(space_capacity);
  const size_t min_percent_free =
          id == perm_space_id ? PermMarkSweepDeadRatio : MarkSweepDeadRatio;
  const double limiter = dead_wood_limiter(density, min_percent_free);
  const size_t dead_wood_max = space_used - space_live;
  const size_t dead_wood_limit = MIN2(size_t(space_capacity * limiter),
                                      dead_wood_max);

  if (TraceParallelOldGCDensePrefix) {
    tty->print_cr("space_live=" SIZE_FORMAT " " "space_used=" SIZE_FORMAT " "
                  "space_cap=" SIZE_FORMAT,
                  space_live, space_used,
                  space_capacity);
    tty->print_cr("dead_wood_limiter(%6.4f, %d)=%6.4f "
                  "dead_wood_max=" SIZE_FORMAT " dead_wood_limit=" SIZE_FORMAT,
                  density, min_percent_free, limiter,
                  dead_wood_max, dead_wood_limit);
  }

  // Locate the chunk with the desired amount of dead space to the left.
  const ChunkData* const limit_cp =
    dead_wood_limit_chunk(full_cp, top_cp, dead_wood_limit);

  // Scan from the first chunk with dead space to the limit chunk and find the
  // one with the best (largest) reclaimed ratio.
  double best_ratio = 0.0;
  const ChunkData* best_cp = full_cp;
  for (const ChunkData* cp = full_cp; cp < limit_cp; ++cp) {
    double tmp_ratio = reclaimed_ratio(cp, bottom, top, new_top);
    if (tmp_ratio > best_ratio) {
      best_cp = cp;
      best_ratio = tmp_ratio;
    }
  }

#if     0
  // Something to consider:  if the chunk with the best ratio is 'close to' the
  // first chunk w/free space, choose the first chunk with free space
  // ("first-free").  The first-free chunk is usually near the start of the
  // heap, which means we are copying most of the heap already, so copy a bit
  // more to get complete compaction.
  if (pointer_delta(best_cp, full_cp, sizeof(ChunkData)) < 4) {
    _maximum_compaction_gc_num = total_invocations();
    best_cp = full_cp;
  }
#endif  // #if 0

  return sd.chunk_to_addr(best_cp);
}

void PSParallelCompact::summarize_spaces_quick()
{
  for (unsigned int i = 0; i < last_space_id; ++i) {
    const MutableSpace* space = _space_info[i].space();
    bool result = _summary_data.summarize(space->bottom(), space->end(),
                                          space->bottom(), space->top(),
                                          _space_info[i].new_top_addr());
    assert(result, "should never fail");
    _space_info[i].set_dense_prefix(space->bottom());
  }
}

void PSParallelCompact::fill_dense_prefix_end(SpaceId id)
{
  HeapWord* const dense_prefix_end = dense_prefix(id);
  const ChunkData* chunk = _summary_data.addr_to_chunk_ptr(dense_prefix_end);
  const idx_t dense_prefix_bit = _mark_bitmap.addr_to_bit(dense_prefix_end);
  if (dead_space_crosses_boundary(chunk, dense_prefix_bit)) {
    // Only enough dead space is filled so that any remaining dead space to the
    // left is larger than the minimum filler object.  (The remainder is filled
    // during the copy/update phase.)
    //
    // The size of the dead space to the right of the boundary is not a
    // concern, since compaction will be able to use whatever space is
    // available.
    //
    // Here '||' is the boundary, 'x' represents a don't care bit and a box
    // surrounds the space to be filled with an object.
    //
    // In the 32-bit VM, each bit represents two 32-bit words:
    //                              +---+
    // a) beg_bits:  ...  x   x   x | 0 | ||   0   x  x  ...
    //    end_bits:  ...  x   x   x | 0 | ||   0   x  x  ...
    //                              +---+
    //
    // In the 64-bit VM, each bit represents one 64-bit word:
    //                              +------------+
    // b) beg_bits:  ...  x   x   x | 0   ||   0 | x  x  ...
    //    end_bits:  ...  x   x   1 | 0   ||   0 | x  x  ...
    //                              +------------+
    //                          +-------+
    // c) beg_bits:  ...  x   x | 0   0 | ||   0   x  x  ...
    //    end_bits:  ...  x   1 | 0   0 | ||   0   x  x  ...
    //                          +-------+
    //                      +-----------+
    // d) beg_bits:  ...  x | 0   0   0 | ||   0   x  x  ...
    //    end_bits:  ...  1 | 0   0   0 | ||   0   x  x  ...
    //                      +-----------+
    //                          +-------+
    // e) beg_bits:  ...  0   0 | 0   0 | ||   0   x  x  ...
    //    end_bits:  ...  0   0 | 0   0 | ||   0   x  x  ...
    //                          +-------+

    // Initially assume case a, c or e will apply.
    size_t obj_len = (size_t)oopDesc::header_size();
    HeapWord* obj_beg = dense_prefix_end - obj_len;

#ifdef  _LP64
    if (_mark_bitmap.is_obj_end(dense_prefix_bit - 2)) {
      // Case b above.
      obj_beg = dense_prefix_end - 1;
    } else if (!_mark_bitmap.is_obj_end(dense_prefix_bit - 3) &&
               _mark_bitmap.is_obj_end(dense_prefix_bit - 4)) {
      // Case d above.
      obj_beg = dense_prefix_end - 3;
      obj_len = 3;
    }
#endif  // #ifdef _LP64

    MemRegion region(obj_beg, obj_len);
    SharedHeap::fill_region_with_object(region);
    _mark_bitmap.mark_obj(obj_beg, obj_len);
    _summary_data.add_obj(obj_beg, obj_len);
    assert(start_array(id) != NULL, "sanity");
    start_array(id)->allocate_block(obj_beg);
  }
}

void
PSParallelCompact::summarize_space(SpaceId id, bool maximum_compaction)
{
  assert(id < last_space_id, "id out of range");

  const MutableSpace* space = _space_info[id].space();
  HeapWord** new_top_addr = _space_info[id].new_top_addr();

  HeapWord* dense_prefix_end = compute_dense_prefix(id, maximum_compaction);
  _space_info[id].set_dense_prefix(dense_prefix_end);

#ifndef PRODUCT
  if (TraceParallelOldGCDensePrefix) {
    print_dense_prefix_stats("ratio", id, maximum_compaction, dense_prefix_end);
    HeapWord* addr = compute_dense_prefix_via_density(id, maximum_compaction);
    print_dense_prefix_stats("density", id, maximum_compaction, addr);
  }
#endif  // #ifndef PRODUCT

  // If dead space crosses the dense prefix boundary, it is (at least partially)
  // filled with a dummy object, marked live and added to the summary data.
  // This simplifies the copy/update phase and must be done before the final
  // locations of objects are determined, to prevent leaving a fragment of dead
  // space that is too small to fill with an object.
  if (!maximum_compaction && dense_prefix_end != space->bottom()) {
    fill_dense_prefix_end(id);
  }

  // Compute the destination of each Chunk, and thus each object.
  _summary_data.summarize_dense_prefix(space->bottom(), dense_prefix_end);
  _summary_data.summarize(dense_prefix_end, space->end(),
                          dense_prefix_end, space->top(),
                          new_top_addr);

  if (TraceParallelOldGCSummaryPhase) {
    const size_t chunk_size = ParallelCompactData::ChunkSize;
    const size_t dp_chunk = _summary_data.addr_to_chunk_idx(dense_prefix_end);
    const size_t dp_words = pointer_delta(dense_prefix_end, space->bottom());
    const HeapWord* nt_aligned_up = _summary_data.chunk_align_up(*new_top_addr);
    const size_t cr_words = pointer_delta(nt_aligned_up, dense_prefix_end);
    tty->print_cr("id=%d cap=" SIZE_FORMAT " dp=" PTR_FORMAT " "
                  "dp_chunk=" SIZE_FORMAT " " "dp_count=" SIZE_FORMAT " "
                  "cr_count=" SIZE_FORMAT " " "nt=" PTR_FORMAT,
                  id, space->capacity_in_words(), dense_prefix_end,
                  dp_chunk, dp_words / chunk_size,
                  cr_words / chunk_size, *new_top_addr);
  }
}

void PSParallelCompact::summary_phase(ParCompactionManager* cm,
                                      bool maximum_compaction)
{
  EventMark m("2 summarize");
  TraceTime tm("summary phase", print_phases(), true, gclog_or_tty);
  // trace("2");

#ifdef  ASSERT
  if (VerifyParallelOldWithMarkSweep  &&
      (PSParallelCompact::total_invocations() %
         VerifyParallelOldWithMarkSweepInterval) == 0) {
    verify_mark_bitmap(_mark_bitmap);
  }
  if (TraceParallelOldGCMarkingPhase) {
    tty->print_cr("add_obj_count=" SIZE_FORMAT " "
                  "add_obj_bytes=" SIZE_FORMAT,
                  add_obj_count, add_obj_size * HeapWordSize);
    tty->print_cr("mark_bitmap_count=" SIZE_FORMAT " "
                  "mark_bitmap_bytes=" SIZE_FORMAT,
                  mark_bitmap_count, mark_bitmap_size * HeapWordSize);
  }
#endif  // #ifdef ASSERT

  // Quick summarization of each space into itself, to see how much is live.
  summarize_spaces_quick();

  if (TraceParallelOldGCSummaryPhase) {
    tty->print_cr("summary_phase:  after summarizing each space to self");
    Universe::print();
    NOT_PRODUCT(print_chunk_ranges());
    if (Verbose) {
      NOT_PRODUCT(print_initial_summary_data(_summary_data, _space_info));
    }
  }

  // The amount of live data that will end up in old space (assuming it fits).
  size_t old_space_total_live = 0;
  unsigned int id;
  for (id = old_space_id; id < last_space_id; ++id) {
    old_space_total_live += pointer_delta(_space_info[id].new_top(),
                                          _space_info[id].space()->bottom());
  }

  const MutableSpace* old_space = _space_info[old_space_id].space();
  if (old_space_total_live > old_space->capacity_in_words()) {
    // XXX - should also try to expand
    maximum_compaction = true;
  } else if (!UseParallelOldGCDensePrefix) {
    maximum_compaction = true;
  }

  // Permanent and Old generations.
  summarize_space(perm_space_id, maximum_compaction);
  summarize_space(old_space_id, maximum_compaction);

  // Summarize the remaining spaces (those in the young gen) into old space.  If
  // the live data from a space doesn't fit, the existing summarization is left
  // intact, so the data is compacted down within the space itself.
  HeapWord** new_top_addr = _space_info[old_space_id].new_top_addr();
  HeapWord* const target_space_end = old_space->end();
  for (id = eden_space_id; id < last_space_id; ++id) {
    const MutableSpace* space = _space_info[id].space();
    const size_t live = pointer_delta(_space_info[id].new_top(),
                                      space->bottom());
    const size_t available = pointer_delta(target_space_end, *new_top_addr);
    if (live <= available) {
      // All the live data will fit.
      if (TraceParallelOldGCSummaryPhase) {
        tty->print_cr("summarizing %d into old_space @ " PTR_FORMAT,
                      id, *new_top_addr);
      }
      _summary_data.summarize(*new_top_addr, target_space_end,
                              space->bottom(), space->top(),
                              new_top_addr);

      // Reset the new_top value for the space.
      _space_info[id].set_new_top(space->bottom());

      // Clear the source_chunk field for each chunk in the space.
      ChunkData* beg_chunk = _summary_data.addr_to_chunk_ptr(space->bottom());
      ChunkData* end_chunk = _summary_data.addr_to_chunk_ptr(space->top() - 1);
      while (beg_chunk <= end_chunk) {
        beg_chunk->set_source_chunk(0);
        ++beg_chunk;
      }
    }
  }

  // Fill in the block data after any changes to the chunks have
  // been made.
#ifdef  ASSERT
  summarize_blocks(cm, perm_space_id);
  summarize_blocks(cm, old_space_id);
#else
  if (!UseParallelOldGCChunkPointerCalc) {
    summarize_blocks(cm, perm_space_id);
    summarize_blocks(cm, old_space_id);
  }
#endif

  if (TraceParallelOldGCSummaryPhase) {
    tty->print_cr("summary_phase:  after final summarization");
    Universe::print();
    NOT_PRODUCT(print_chunk_ranges());
    if (Verbose) {
      NOT_PRODUCT(print_generic_summary_data(_summary_data, _space_info));
    }
  }
}

// Fill in the BlockData.
// Iterate over the spaces and within each space iterate over
// the chunks and fill in the BlockData for each chunk.

void PSParallelCompact::summarize_blocks(ParCompactionManager* cm,
                                         SpaceId first_compaction_space_id) {
#if     0
  DEBUG_ONLY(ParallelCompactData::BlockData::set_cur_phase(1);)
  for (SpaceId cur_space_id = first_compaction_space_id;
       cur_space_id != last_space_id;
       cur_space_id = next_compaction_space_id(cur_space_id)) {
    // Iterate over the chunks in the space
    size_t start_chunk_index =
      _summary_data.addr_to_chunk_idx(space(cur_space_id)->bottom());
    BitBlockUpdateClosure bbu(mark_bitmap(),
                              cm,
                              start_chunk_index);
    // Iterate over blocks.
    for (size_t chunk_index =  start_chunk_index;
         chunk_index < _summary_data.chunk_count() &&
         _summary_data.chunk_to_addr(chunk_index) < space(cur_space_id)->top();
         chunk_index++) {

      // Reset the closure for the new chunk.  Note that the closure
      // maintains some data that does not get reset for each chunk
      // so a new instance of the closure is no appropriate.
      bbu.reset_chunk(chunk_index);

      // Start the iteration with the first live object.  This
      // may return the end of the chunk.  That is acceptable since
      // it will properly limit the iterations.
      ParMarkBitMap::idx_t left_offset = mark_bitmap()->addr_to_bit(
        _summary_data.first_live_or_end_in_chunk(chunk_index));

      // End the iteration at the end of the chunk.
      HeapWord* chunk_addr = _summary_data.chunk_to_addr(chunk_index);
      HeapWord* chunk_end = chunk_addr + ParallelCompactData::ChunkSize;
      ParMarkBitMap::idx_t right_offset =
        mark_bitmap()->addr_to_bit(chunk_end);

      // Blocks that have not objects starting in them can be
      // skipped because their data will never be used.
      if (left_offset < right_offset) {

        // Iterate through the objects in the chunk.
        ParMarkBitMap::idx_t last_offset =
          mark_bitmap()->pair_iterate(&bbu, left_offset, right_offset);

        // If last_offset is less than right_offset, then the iterations
        // terminated while it was looking for an end bit.  "last_offset"
        // is then the offset for the last start bit.  In this situation
        // the "offset" field for the next block to the right (_cur_block + 1)
        // will not have been update although there may be live data
        // to the left of the chunk.

        size_t cur_block_plus_1 = bbu.cur_block() + 1;
        HeapWord* cur_block_plus_1_addr =
        _summary_data.block_to_addr(bbu.cur_block()) +
        ParallelCompactData::BlockSize;
        HeapWord* last_offset_addr = mark_bitmap()->bit_to_addr(last_offset);
 #if 1  // This code works.  The else doesn't but should.  Why does it?
        // The current block (cur_block()) has already been updated.
        // The last block that may need to be updated is either the
        // next block (current block + 1) or the block where the
        // last object starts (which can be greater than the
        // next block if there were no objects found in intervening
        // blocks).
        size_t last_block =
          MAX2(bbu.cur_block() + 1,
               _summary_data.addr_to_block_idx(last_offset_addr));
 #else
        // The current block has already been updated.  The only block
        // that remains to be updated is the block where the last
        // object in the chunk starts.
        size_t last_block = _summary_data.addr_to_block_idx(last_offset_addr);
 #endif
        assert_bit_is_start(last_offset);
        assert((last_block == _summary_data.block_count()) ||
             (_summary_data.block(last_block)->raw_offset() == 0),
          "Should not have been set");
        // Is the last block still in the current chunk?  If still
        // in this chunk, update the last block (the counting that
        // included the current block is meant for the offset of the last
        // block).  If not in this chunk, do nothing.  Should not
        // update a block in the next chunk.
        if (ParallelCompactData::chunk_contains_block(bbu.chunk_index(),
                                                      last_block)) {
          if (last_offset < right_offset) {
            // The last object started in this chunk but ends beyond
            // this chunk.  Update the block for this last object.
            assert(mark_bitmap()->is_marked(last_offset), "Should be marked");
            // No end bit was found.  The closure takes care of
            // the cases where
            //   an objects crosses over into the next block
            //   an objects starts and ends in the next block
            // It does not handle the case where an object is
            // the first object in a later block and extends
            // past the end of the chunk (i.e., the closure
            // only handles complete objects that are in the range
            // it is given).  That object is handed back here
            // for any special consideration necessary.
            //
            // Is the first bit in the last block a start or end bit?
            //
            // If the partial object ends in the last block L,
            // then the 1st bit in L may be an end bit.
            //
            // Else does the last object start in a block after the current
            // block? A block AA will already have been updated if an
            // object ends in the next block AA+1.  An object found to end in
            // the AA+1 is the trigger that updates AA.  Objects are being
            // counted in the current block for updaing a following
            // block.  An object may start in later block
            // block but may extend beyond the last block in the chunk.
            // Updates are only done when the end of an object has been
            // found. If the last object (covered by block L) starts
            // beyond the current block, then no object ends in L (otherwise
            // L would be the current block).  So the first bit in L is
            // a start bit.
            //
            // Else the last objects start in the current block and ends
            // beyond the chunk.  The current block has already been
            // updated and there is no later block (with an object
            // starting in it) that needs to be updated.
            //
            if (_summary_data.partial_obj_ends_in_block(last_block)) {
              _summary_data.block(last_block)->set_end_bit_offset(
                bbu.live_data_left());
            } else if (last_offset_addr >= cur_block_plus_1_addr) {
              //   The start of the object is on a later block
              // (to the right of the current block and there are no
              // complete live objects to the left of this last object
              // within the chunk.
              //   The first bit in the block is for the start of the
              // last object.
              _summary_data.block(last_block)->set_start_bit_offset(
                bbu.live_data_left());
            } else {
              //   The start of the last object was found in
              // the current chunk (which has already
              // been updated).
              assert(bbu.cur_block() ==
                      _summary_data.addr_to_block_idx(last_offset_addr),
                "Should be a block already processed");
            }
#ifdef ASSERT
            // Is there enough block information to find this object?
            // The destination of the chunk has not been set so the
            // values returned by calc_new_pointer() and
            // block_calc_new_pointer() will only be
            // offsets.  But they should agree.
            HeapWord* moved_obj_with_chunks =
              _summary_data.chunk_calc_new_pointer(last_offset_addr);
            HeapWord* moved_obj_with_blocks =
              _summary_data.calc_new_pointer(last_offset_addr);
            assert(moved_obj_with_chunks == moved_obj_with_blocks,
              "Block calculation is wrong");
#endif
          } else if (last_block < _summary_data.block_count()) {
            // Iterations ended looking for a start bit (but
            // did not run off the end of the block table).
            _summary_data.block(last_block)->set_start_bit_offset(
              bbu.live_data_left());
          }
        }
#ifdef ASSERT
        // Is there enough block information to find this object?
          HeapWord* left_offset_addr = mark_bitmap()->bit_to_addr(left_offset);
        HeapWord* moved_obj_with_chunks =
          _summary_data.calc_new_pointer(left_offset_addr);
        HeapWord* moved_obj_with_blocks =
          _summary_data.calc_new_pointer(left_offset_addr);
          assert(moved_obj_with_chunks == moved_obj_with_blocks,
          "Block calculation is wrong");
#endif

        // Is there another block after the end of this chunk?
#ifdef ASSERT
        if (last_block < _summary_data.block_count()) {
        // No object may have been found in a block.  If that
        // block is at the end of the chunk, the iteration will
        // terminate without incrementing the current block so
        // that the current block is not the last block in the
        // chunk.  That situation precludes asserting that the
        // current block is the last block in the chunk.  Assert
        // the lesser condition that the current block does not
        // exceed the chunk.
          assert(_summary_data.block_to_addr(last_block) <=
               (_summary_data.chunk_to_addr(chunk_index) +
                 ParallelCompactData::ChunkSize),
              "Chunk and block inconsistency");
          assert(last_offset <= right_offset, "Iteration over ran end");
        }
#endif
      }
#ifdef ASSERT
      if (PrintGCDetails && Verbose) {
        if (_summary_data.chunk(chunk_index)->partial_obj_size() == 1) {
          size_t first_block =
            chunk_index / ParallelCompactData::BlocksPerChunk;
          gclog_or_tty->print_cr("first_block " PTR_FORMAT
            " _offset " PTR_FORMAT
            "_first_is_start_bit %d",
            first_block,
            _summary_data.block(first_block)->raw_offset(),
            _summary_data.block(first_block)->first_is_start_bit());
        }
      }
#endif
    }
  }
  DEBUG_ONLY(ParallelCompactData::BlockData::set_cur_phase(16);)
#endif  // #if 0
}

// This method should contain all heap-specific policy for invoking a full
// collection.  invoke_no_policy() will only attempt to compact the heap; it
// will do nothing further.  If we need to bail out for policy reasons, scavenge
// before full gc, or any other specialized behavior, it needs to be added here.
//
// Note that this method should only be called from the vm_thread while at a
// safepoint.
void PSParallelCompact::invoke(bool maximum_heap_compaction) {
  assert(SafepointSynchronize::is_at_safepoint(), "should be at safepoint");
  assert(Thread::current() == (Thread*)VMThread::vm_thread(),
         "should be in vm thread");
  ParallelScavengeHeap* heap = gc_heap();
  GCCause::Cause gc_cause = heap->gc_cause();
  assert(!heap->is_gc_active(), "not reentrant");

  PSAdaptiveSizePolicy* policy = heap->size_policy();

  // Before each allocation/collection attempt, find out from the
  // policy object if GCs are, on the whole, taking too long. If so,
  // bail out without attempting a collection.  The exceptions are
  // for explicitly requested GC's.
  if (!policy->gc_time_limit_exceeded() ||
      GCCause::is_user_requested_gc(gc_cause) ||
      GCCause::is_serviceability_requested_gc(gc_cause)) {
    IsGCActiveMark mark;

    if (ScavengeBeforeFullGC) {
      PSScavenge::invoke_no_policy();
    }

    PSParallelCompact::invoke_no_policy(maximum_heap_compaction);
  }
}

bool ParallelCompactData::chunk_contains(size_t chunk_index, HeapWord* addr) {
  size_t addr_chunk_index = addr_to_chunk_idx(addr);
  return chunk_index == addr_chunk_index;
}

bool ParallelCompactData::chunk_contains_block(size_t chunk_index,
                                               size_t block_index) {
  size_t first_block_in_chunk = chunk_index * BlocksPerChunk;
  size_t last_block_in_chunk = (chunk_index + 1) * BlocksPerChunk - 1;

  return (first_block_in_chunk <= block_index) &&
         (block_index <= last_block_in_chunk);
}

// This method contains no policy. You should probably
// be calling invoke() instead.
void PSParallelCompact::invoke_no_policy(bool maximum_heap_compaction) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at a safepoint");
  assert(ref_processor() != NULL, "Sanity");

  if (GC_locker::is_active()) {
    return;
  }

  TimeStamp marking_start;
  TimeStamp compaction_start;
  TimeStamp collection_exit;

  // "serial_CM" is needed until the parallel implementation
  // of the move and update is done.
  ParCompactionManager* serial_CM = new ParCompactionManager();
  // Don't initialize more than once.
  // serial_CM->initialize(&summary_data(), mark_bitmap());

  ParallelScavengeHeap* heap = gc_heap();
  GCCause::Cause gc_cause = heap->gc_cause();
  PSYoungGen* young_gen = heap->young_gen();
  PSOldGen* old_gen = heap->old_gen();
  PSPermGen* perm_gen = heap->perm_gen();
  PSAdaptiveSizePolicy* size_policy = heap->size_policy();

  _print_phases = PrintGCDetails && PrintParallelOldGCPhaseTimes;

  // Make sure data structures are sane, make the heap parsable, and do other
  // miscellaneous bookkeeping.
  PreGCValues pre_gc_values;
  pre_compact(&pre_gc_values);

  // Place after pre_compact() where the number of invocations is incremented.
  AdaptiveSizePolicyOutput(size_policy, heap->total_collections());

  {
    ResourceMark rm;
    HandleMark hm;

    const bool is_system_gc = gc_cause == GCCause::_java_lang_system_gc;

    // This is useful for debugging but don't change the output the
    // the customer sees.
    const char* gc_cause_str = "Full GC";
    if (is_system_gc && PrintGCDetails) {
      gc_cause_str = "Full GC (System)";
    }
    gclog_or_tty->date_stamp(PrintGC && PrintGCDateStamps);
    TraceCPUTime tcpu(PrintGCDetails, true, gclog_or_tty);
    TraceTime t1(gc_cause_str, PrintGC, !PrintGCDetails, gclog_or_tty);
    TraceCollectorStats tcs(counters());
    TraceMemoryManagerStats tms(true /* Full GC */);

    if (TraceGen1Time) accumulated_time()->start();

    // Let the size policy know we're starting
    size_policy->major_collection_begin();

    // When collecting the permanent generation methodOops may be moving,
    // so we either have to flush all bcp data or convert it into bci.
    CodeCache::gc_prologue();
    Threads::gc_prologue();

    NOT_PRODUCT(ref_processor()->verify_no_references_recorded());
    COMPILER2_PRESENT(DerivedPointerTable::clear());

    ref_processor()->enable_discovery();

    bool marked_for_unloading = false;

    marking_start.update();
    marking_phase(serial_CM, maximum_heap_compaction);

#ifndef PRODUCT
    if (TraceParallelOldGCMarkingPhase) {
      gclog_or_tty->print_cr("marking_phase: cas_tries %d  cas_retries %d "
        "cas_by_another %d",
        mark_bitmap()->cas_tries(), mark_bitmap()->cas_retries(),
        mark_bitmap()->cas_by_another());
    }
#endif  // #ifndef PRODUCT

#ifdef ASSERT
    if (VerifyParallelOldWithMarkSweep &&
        (PSParallelCompact::total_invocations() %
           VerifyParallelOldWithMarkSweepInterval) == 0) {
      gclog_or_tty->print_cr("Verify marking with mark_sweep_phase1()");
      if (PrintGCDetails && Verbose) {
        gclog_or_tty->print_cr("mark_sweep_phase1:");
      }
      // Clear the discovered lists so that discovered objects
      // don't look like they have been discovered twice.
      ref_processor()->clear_discovered_references();

      PSMarkSweep::allocate_stacks();
      MemRegion mr = Universe::heap()->reserved_region();
      PSMarkSweep::ref_processor()->enable_discovery();
      PSMarkSweep::mark_sweep_phase1(maximum_heap_compaction);
    }
#endif

    bool max_on_system_gc = UseMaximumCompactionOnSystemGC && is_system_gc;
    summary_phase(serial_CM, maximum_heap_compaction || max_on_system_gc);

#ifdef ASSERT
    if (VerifyParallelOldWithMarkSweep &&
        (PSParallelCompact::total_invocations() %
           VerifyParallelOldWithMarkSweepInterval) == 0) {
      if (PrintGCDetails && Verbose) {
        gclog_or_tty->print_cr("mark_sweep_phase2:");
      }
      PSMarkSweep::mark_sweep_phase2();
    }
#endif

    COMPILER2_PRESENT(assert(DerivedPointerTable::is_active(), "Sanity"));
    COMPILER2_PRESENT(DerivedPointerTable::set_active(false));

    // adjust_roots() updates Universe::_intArrayKlassObj which is
    // needed by the compaction for filling holes in the dense prefix.
    adjust_roots();

#ifdef ASSERT
    if (VerifyParallelOldWithMarkSweep &&
        (PSParallelCompact::total_invocations() %
           VerifyParallelOldWithMarkSweepInterval) == 0) {
      // Do a separate verify phase so that the verify
      // code can use the the forwarding pointers to
      // check the new pointer calculation.  The restore_marks()
      // has to be done before the real compact.
      serial_CM->set_action(ParCompactionManager::VerifyUpdate);
      compact_perm(serial_CM);
      compact_serial(serial_CM);
      serial_CM->set_action(ParCompactionManager::ResetObjects);
      compact_perm(serial_CM);
      compact_serial(serial_CM);
      serial_CM->set_action(ParCompactionManager::UpdateAndCopy);

      // For debugging only
      PSMarkSweep::restore_marks();
      PSMarkSweep::deallocate_stacks();
    }
#endif

    compaction_start.update();
    // Does the perm gen always have to be done serially because
    // klasses are used in the update of an object?
    compact_perm(serial_CM);

    if (UseParallelOldGCCompacting) {
      compact();
    } else {
      compact_serial(serial_CM);
    }

    delete serial_CM;

    // Reset the mark bitmap, summary data, and do other bookkeeping.  Must be
    // done before resizing.
    post_compact();

    // Let the size policy know we're done
    size_policy->major_collection_end(old_gen->used_in_bytes(), gc_cause);

    if (UseAdaptiveSizePolicy) {
      if (PrintAdaptiveSizePolicy) {
        gclog_or_tty->print("AdaptiveSizeStart: ");
        gclog_or_tty->stamp();
        gclog_or_tty->print_cr(" collection: %d ",
                       heap->total_collections());
        if (Verbose) {
          gclog_or_tty->print("old_gen_capacity: %d young_gen_capacity: %d"
            " perm_gen_capacity: %d ",
            old_gen->capacity_in_bytes(), young_gen->capacity_in_bytes(),
            perm_gen->capacity_in_bytes());
        }
      }

      // Don't check if the size_policy is ready here.  Let
      // the size_policy check that internally.
      if (UseAdaptiveGenerationSizePolicyAtMajorCollection &&
          ((gc_cause != GCCause::_java_lang_system_gc) ||
            UseAdaptiveSizePolicyWithSystemGC)) {
        // Calculate optimal free space amounts
        assert(young_gen->max_size() >
          young_gen->from_space()->capacity_in_bytes() +
          young_gen->to_space()->capacity_in_bytes(),
          "Sizes of space in young gen are out-of-bounds");
        size_t max_eden_size = young_gen->max_size() -
          young_gen->from_space()->capacity_in_bytes() -
          young_gen->to_space()->capacity_in_bytes();
        size_policy->compute_generation_free_space(young_gen->used_in_bytes(),
                                 young_gen->eden_space()->used_in_bytes(),
                                 old_gen->used_in_bytes(),
                                 perm_gen->used_in_bytes(),
                                 young_gen->eden_space()->capacity_in_bytes(),
                                 old_gen->max_gen_size(),
                                 max_eden_size,
                                 true /* full gc*/,
                                 gc_cause);

        heap->resize_old_gen(size_policy->calculated_old_free_size_in_bytes());

        // Don't resize the young generation at an major collection.  A
        // desired young generation size may have been calculated but
        // resizing the young generation complicates the code because the
        // resizing of the old generation may have moved the boundary
        // between the young generation and the old generation.  Let the
        // young generation resizing happen at the minor collections.
      }
      if (PrintAdaptiveSizePolicy) {
        gclog_or_tty->print_cr("AdaptiveSizeStop: collection: %d ",
                       heap->total_collections());
      }
    }

    if (UsePerfData) {
      PSGCAdaptivePolicyCounters* const counters = heap->gc_policy_counters();
      counters->update_counters();
      counters->update_old_capacity(old_gen->capacity_in_bytes());
      counters->update_young_capacity(young_gen->capacity_in_bytes());
    }

    heap->resize_all_tlabs();

    // We collected the perm gen, so we'll resize it here.
    perm_gen->compute_new_size(pre_gc_values.perm_gen_used());

    if (TraceGen1Time) accumulated_time()->stop();

    if (PrintGC) {
      if (PrintGCDetails) {
        // No GC timestamp here.  This is after GC so it would be confusing.
        young_gen->print_used_change(pre_gc_values.young_gen_used());
        old_gen->print_used_change(pre_gc_values.old_gen_used());
        heap->print_heap_change(pre_gc_values.heap_used());
        // Print perm gen last (print_heap_change() excludes the perm gen).
        perm_gen->print_used_change(pre_gc_values.perm_gen_used());
      } else {
        heap->print_heap_change(pre_gc_values.heap_used());
      }
    }

    // Track memory usage and detect low memory
    MemoryService::track_memory_usage();
    heap->update_counters();

    if (PrintGCDetails) {
      if (size_policy->print_gc_time_limit_would_be_exceeded()) {
        if (size_policy->gc_time_limit_exceeded()) {
          gclog_or_tty->print_cr("      GC time is exceeding GCTimeLimit "
            "of %d%%", GCTimeLimit);
        } else {
          gclog_or_tty->print_cr("      GC time would exceed GCTimeLimit "
            "of %d%%", GCTimeLimit);
        }
      }
      size_policy->set_print_gc_time_limit_would_be_exceeded(false);
    }
  }

  if (VerifyAfterGC && heap->total_collections() >= VerifyGCStartAt) {
    HandleMark hm;  // Discard invalid handles created during verification
    gclog_or_tty->print(" VerifyAfterGC:");
    Universe::verify(false);
  }

  // Re-verify object start arrays
  if (VerifyObjectStartArray &&
      VerifyAfterGC) {
    old_gen->verify_object_start_array();
    perm_gen->verify_object_start_array();
  }

  NOT_PRODUCT(ref_processor()->verify_no_references_recorded());

  collection_exit.update();

  if (PrintHeapAtGC) {
    Universe::print_heap_after_gc();
  }
  if (PrintGCTaskTimeStamps) {
    gclog_or_tty->print_cr("VM-Thread " INT64_FORMAT " " INT64_FORMAT " "
                           INT64_FORMAT,
                           marking_start.ticks(), compaction_start.ticks(),
                           collection_exit.ticks());
    gc_task_manager()->print_task_time_stamps();
  }
}

bool PSParallelCompact::absorb_live_data_from_eden(PSAdaptiveSizePolicy* size_policy,
                                             PSYoungGen* young_gen,
                                             PSOldGen* old_gen) {
  MutableSpace* const eden_space = young_gen->eden_space();
  assert(!eden_space->is_empty(), "eden must be non-empty");
  assert(young_gen->virtual_space()->alignment() ==
         old_gen->virtual_space()->alignment(), "alignments do not match");

  if (!(UseAdaptiveSizePolicy && UseAdaptiveGCBoundary)) {
    return false;
  }

  // Both generations must be completely committed.
  if (young_gen->virtual_space()->uncommitted_size() != 0) {
    return false;
  }
  if (old_gen->virtual_space()->uncommitted_size() != 0) {
    return false;
  }

  // Figure out how much to take from eden.  Include the average amount promoted
  // in the total; otherwise the next young gen GC will simply bail out to a
  // full GC.
  const size_t alignment = old_gen->virtual_space()->alignment();
  const size_t eden_used = eden_space->used_in_bytes();
  const size_t promoted = (size_t)size_policy->avg_promoted()->padded_average();
  const size_t absorb_size = align_size_up(eden_used + promoted, alignment);
  const size_t eden_capacity = eden_space->capacity_in_bytes();

  if (absorb_size >= eden_capacity) {
    return false; // Must leave some space in eden.
  }

  const size_t new_young_size = young_gen->capacity_in_bytes() - absorb_size;
  if (new_young_size < young_gen->min_gen_size()) {
    return false; // Respect young gen minimum size.
  }

  if (TraceAdaptiveGCBoundary && Verbose) {
    gclog_or_tty->print(" absorbing " SIZE_FORMAT "K:  "
                        "eden " SIZE_FORMAT "K->" SIZE_FORMAT "K "
                        "from " SIZE_FORMAT "K, to " SIZE_FORMAT "K "
                        "young_gen " SIZE_FORMAT "K->" SIZE_FORMAT "K ",
                        absorb_size / K,
                        eden_capacity / K, (eden_capacity - absorb_size) / K,
                        young_gen->from_space()->used_in_bytes() / K,
                        young_gen->to_space()->used_in_bytes() / K,
                        young_gen->capacity_in_bytes() / K, new_young_size / K);
  }

  // Fill the unused part of the old gen.
  MutableSpace* const old_space = old_gen->object_space();
  MemRegion old_gen_unused(old_space->top(), old_space->end());
  if (!old_gen_unused.is_empty()) {
    SharedHeap::fill_region_with_object(old_gen_unused);
  }

  // Take the live data from eden and set both top and end in the old gen to
  // eden top.  (Need to set end because reset_after_change() mangles the region
  // from end to virtual_space->high() in debug builds).
  HeapWord* const new_top = eden_space->top();
  old_gen->virtual_space()->expand_into(young_gen->virtual_space(),
                                        absorb_size);
  young_gen->reset_after_change();
  old_space->set_top(new_top);
  old_space->set_end(new_top);
  old_gen->reset_after_change();

  // Update the object start array for the filler object and the data from eden.
  ObjectStartArray* const start_array = old_gen->start_array();
  HeapWord* const start = old_gen_unused.start();
  for (HeapWord* addr = start; addr < new_top; addr += oop(addr)->size()) {
    start_array->allocate_block(addr);
  }

  // Could update the promoted average here, but it is not typically updated at
  // full GCs and the value to use is unclear.  Something like
  //
  // cur_promoted_avg + absorb_size / number_of_scavenges_since_last_full_gc.

  size_policy->set_bytes_absorbed_from_eden(absorb_size);
  return true;
}

GCTaskManager* const PSParallelCompact::gc_task_manager() {
  assert(ParallelScavengeHeap::gc_task_manager() != NULL,
    "shouldn't return NULL");
  return ParallelScavengeHeap::gc_task_manager();
}

void PSParallelCompact::marking_phase(ParCompactionManager* cm,
                                      bool maximum_heap_compaction) {
  // Recursively traverse all live objects and mark them
  EventMark m("1 mark object");
  TraceTime tm("marking phase", print_phases(), true, gclog_or_tty);

  ParallelScavengeHeap* heap = gc_heap();
  uint parallel_gc_threads = heap->gc_task_manager()->workers();
  TaskQueueSetSuper* qset = ParCompactionManager::chunk_array();
  ParallelTaskTerminator terminator(parallel_gc_threads, qset);

  PSParallelCompact::MarkAndPushClosure mark_and_push_closure(cm);
  PSParallelCompact::FollowStackClosure follow_stack_closure(cm);

  {
    TraceTime tm_m("par mark", print_phases(), true, gclog_or_tty);

    GCTaskQueue* q = GCTaskQueue::create();

    q->enqueue(new MarkFromRootsTask(MarkFromRootsTask::universe));
    q->enqueue(new MarkFromRootsTask(MarkFromRootsTask::jni_handles));
    // We scan the thread roots in parallel
    Threads::create_thread_roots_marking_tasks(q);
    q->enqueue(new MarkFromRootsTask(MarkFromRootsTask::object_synchronizer));
    q->enqueue(new MarkFromRootsTask(MarkFromRootsTask::flat_profiler));
    q->enqueue(new MarkFromRootsTask(MarkFromRootsTask::management));
    q->enqueue(new MarkFromRootsTask(MarkFromRootsTask::system_dictionary));
    q->enqueue(new MarkFromRootsTask(MarkFromRootsTask::jvmti));
    q->enqueue(new MarkFromRootsTask(MarkFromRootsTask::vm_symbols));

    if (parallel_gc_threads > 1) {
      for (uint j = 0; j < parallel_gc_threads; j++) {
        q->enqueue(new StealMarkingTask(&terminator));
      }
    }

    WaitForBarrierGCTask* fin = WaitForBarrierGCTask::create();
    q->enqueue(fin);

    gc_task_manager()->add_list(q);

    fin->wait_for();

    // We have to release the barrier tasks!
    WaitForBarrierGCTask::destroy(fin);
  }

  // Process reference objects found during marking
  {
    TraceTime tm_r("reference processing", print_phases(), true, gclog_or_tty);
    ReferencePolicy *soft_ref_policy;
    if (maximum_heap_compaction) {
      soft_ref_policy = new AlwaysClearPolicy();
    } else {
#ifdef COMPILER2
      soft_ref_policy = new LRUMaxHeapPolicy();
#else
      soft_ref_policy = new LRUCurrentHeapPolicy();
#endif // COMPILER2
    }
    assert(soft_ref_policy != NULL, "No soft reference policy");
    if (ref_processor()->processing_is_mt()) {
      RefProcTaskExecutor task_executor;
      ref_processor()->process_discovered_references(
        soft_ref_policy, is_alive_closure(), &mark_and_push_closure,
        &follow_stack_closure, &task_executor);
    } else {
      ref_processor()->process_discovered_references(
        soft_ref_policy, is_alive_closure(), &mark_and_push_closure,
        &follow_stack_closure, NULL);
    }
  }

  TraceTime tm_c("class unloading", print_phases(), true, gclog_or_tty);
  // Follow system dictionary roots and unload classes.
  bool purged_class = SystemDictionary::do_unloading(is_alive_closure());

  // Follow code cache roots.
  CodeCache::do_unloading(is_alive_closure(), &mark_and_push_closure,
                          purged_class);
  follow_stack(cm); // Flush marking stack.

  // Update subklass/sibling/implementor links of live klasses
  // revisit_klass_stack is used in follow_weak_klass_links().
  follow_weak_klass_links(cm);

  // Visit symbol and interned string tables and delete unmarked oops
  SymbolTable::unlink(is_alive_closure());
  StringTable::unlink(is_alive_closure());

  assert(cm->marking_stack()->size() == 0, "stack should be empty by now");
  assert(cm->overflow_stack()->is_empty(), "stack should be empty by now");
}

// This should be moved to the shared markSweep code!
class PSAlwaysTrueClosure: public BoolObjectClosure {
public:
  void do_object(oop p) { ShouldNotReachHere(); }
  bool do_object_b(oop p) { return true; }
};
static PSAlwaysTrueClosure always_true;

void PSParallelCompact::adjust_roots() {
  // Adjust the pointers to reflect the new locations
  EventMark m("3 adjust roots");
  TraceTime tm("adjust roots", print_phases(), true, gclog_or_tty);

  // General strong roots.
  Universe::oops_do(adjust_root_pointer_closure());
  ReferenceProcessor::oops_do(adjust_root_pointer_closure());
  JNIHandles::oops_do(adjust_root_pointer_closure());   // Global (strong) JNI handles
  Threads::oops_do(adjust_root_pointer_closure());
  ObjectSynchronizer::oops_do(adjust_root_pointer_closure());
  FlatProfiler::oops_do(adjust_root_pointer_closure());
  Management::oops_do(adjust_root_pointer_closure());
  JvmtiExport::oops_do(adjust_root_pointer_closure());
  // SO_AllClasses
  SystemDictionary::oops_do(adjust_root_pointer_closure());
  vmSymbols::oops_do(adjust_root_pointer_closure());

  // Now adjust pointers in remaining weak roots.  (All of which should
  // have been cleared if they pointed to non-surviving objects.)
  // Global (weak) JNI handles
  JNIHandles::weak_oops_do(&always_true, adjust_root_pointer_closure());

  CodeCache::oops_do(adjust_pointer_closure());
  SymbolTable::oops_do(adjust_root_pointer_closure());
  StringTable::oops_do(adjust_root_pointer_closure());
  ref_processor()->weak_oops_do(adjust_root_pointer_closure());
  // Roots were visited so references into the young gen in roots
  // may have been scanned.  Process them also.
  // Should the reference processor have a span that excludes
  // young gen objects?
  PSScavenge::reference_processor()->weak_oops_do(
                                              adjust_root_pointer_closure());
}

void PSParallelCompact::compact_perm(ParCompactionManager* cm) {
  EventMark m("4 compact perm");
  TraceTime tm("compact perm gen", print_phases(), true, gclog_or_tty);
  // trace("4");

  gc_heap()->perm_gen()->start_array()->reset();
  move_and_update(cm, perm_space_id);
}

void PSParallelCompact::enqueue_chunk_draining_tasks(GCTaskQueue* q,
                                                     uint parallel_gc_threads) {
  TraceTime tm("drain task setup", print_phases(), true, gclog_or_tty);

  const unsigned int task_count = MAX2(parallel_gc_threads, 1U);
  for (unsigned int j = 0; j < task_count; j++) {
    q->enqueue(new DrainStacksCompactionTask());
  }

  // Find all chunks that are available (can be filled immediately) and
  // distribute them to the thread stacks.  The iteration is done in reverse
  // order (high to low) so the chunks will be removed in ascending order.

  const ParallelCompactData& sd = PSParallelCompact::summary_data();

  size_t fillable_chunks = 0;   // A count for diagnostic purposes.
  unsigned int which = 0;       // The worker thread number.

  for (unsigned int id = to_space_id; id > perm_space_id; --id) {
    SpaceInfo* const space_info = _space_info + id;
    MutableSpace* const space = space_info->space();
    HeapWord* const new_top = space_info->new_top();

    const size_t beg_chunk = sd.addr_to_chunk_idx(space_info->dense_prefix());
    const size_t end_chunk = sd.addr_to_chunk_idx(sd.chunk_align_up(new_top));
    assert(end_chunk > 0, "perm gen cannot be empty");

    for (size_t cur = end_chunk - 1; cur >= beg_chunk; --cur) {
      if (sd.chunk(cur)->claim_unsafe()) {
        ParCompactionManager* cm = ParCompactionManager::manager_array(which);
        cm->save_for_processing(cur);

        if (TraceParallelOldGCCompactionPhase && Verbose) {
          const size_t count_mod_8 = fillable_chunks & 7;
          if (count_mod_8 == 0) gclog_or_tty->print("fillable: ");
          gclog_or_tty->print(" " SIZE_FORMAT_W("7"), cur);
          if (count_mod_8 == 7) gclog_or_tty->cr();
        }

        NOT_PRODUCT(++fillable_chunks;)

        // Assign chunks to threads in round-robin fashion.
        if (++which == task_count) {
          which = 0;
        }
      }
    }
  }

  if (TraceParallelOldGCCompactionPhase) {
    if (Verbose && (fillable_chunks & 7) != 0) gclog_or_tty->cr();
    gclog_or_tty->print_cr("%u initially fillable chunks", fillable_chunks);
  }
}

#define PAR_OLD_DENSE_PREFIX_OVER_PARTITIONING 4

void PSParallelCompact::enqueue_dense_prefix_tasks(GCTaskQueue* q,
                                                    uint parallel_gc_threads) {
  TraceTime tm("dense prefix task setup", print_phases(), true, gclog_or_tty);

  ParallelCompactData& sd = PSParallelCompact::summary_data();

  // Iterate over all the spaces adding tasks for updating
  // chunks in the dense prefix.  Assume that 1 gc thread
  // will work on opening the gaps and the remaining gc threads
  // will work on the dense prefix.
  SpaceId space_id = old_space_id;
  while (space_id != last_space_id) {
    HeapWord* const dense_prefix_end = _space_info[space_id].dense_prefix();
    const MutableSpace* const space = _space_info[space_id].space();

    if (dense_prefix_end == space->bottom()) {
      // There is no dense prefix for this space.
      space_id = next_compaction_space_id(space_id);
      continue;
    }

    // The dense prefix is before this chunk.
    size_t chunk_index_end_dense_prefix =
        sd.addr_to_chunk_idx(dense_prefix_end);
    ChunkData* const dense_prefix_cp = sd.chunk(chunk_index_end_dense_prefix);
    assert(dense_prefix_end == space->end() ||
           dense_prefix_cp->available() ||
           dense_prefix_cp->claimed(),
           "The chunk after the dense prefix should always be ready to fill");

    size_t chunk_index_start = sd.addr_to_chunk_idx(space->bottom());

    // Is there dense prefix work?
    size_t total_dense_prefix_chunks =
      chunk_index_end_dense_prefix - chunk_index_start;
    // How many chunks of the dense prefix should be given to
    // each thread?
    if (total_dense_prefix_chunks > 0) {
      uint tasks_for_dense_prefix = 1;
      if (UseParallelDensePrefixUpdate) {
        if (total_dense_prefix_chunks <=
            (parallel_gc_threads * PAR_OLD_DENSE_PREFIX_OVER_PARTITIONING)) {
          // Don't over partition.  This assumes that
          // PAR_OLD_DENSE_PREFIX_OVER_PARTITIONING is a small integer value
          // so there are not many chunks to process.
          tasks_for_dense_prefix = parallel_gc_threads;
        } else {
          // Over partition
          tasks_for_dense_prefix = parallel_gc_threads *
            PAR_OLD_DENSE_PREFIX_OVER_PARTITIONING;
        }
      }
      size_t chunks_per_thread = total_dense_prefix_chunks /
        tasks_for_dense_prefix;
      // Give each thread at least 1 chunk.
      if (chunks_per_thread == 0) {
        chunks_per_thread = 1;
      }

      for (uint k = 0; k < tasks_for_dense_prefix; k++) {
        if (chunk_index_start >= chunk_index_end_dense_prefix) {
          break;
        }
        // chunk_index_end is not processed
        size_t chunk_index_end = MIN2(chunk_index_start + chunks_per_thread,
                                      chunk_index_end_dense_prefix);
        q->enqueue(new UpdateDensePrefixTask(
                                 space_id,
                                 chunk_index_start,
                                 chunk_index_end));
        chunk_index_start = chunk_index_end;
      }
    }
    // This gets any part of the dense prefix that did not
    // fit evenly.
    if (chunk_index_start < chunk_index_end_dense_prefix) {
      q->enqueue(new UpdateDensePrefixTask(
                                 space_id,
                                 chunk_index_start,
                                 chunk_index_end_dense_prefix));
    }
    space_id = next_compaction_space_id(space_id);
  }  // End tasks for dense prefix
}

void PSParallelCompact::enqueue_chunk_stealing_tasks(
                                     GCTaskQueue* q,
                                     ParallelTaskTerminator* terminator_ptr,
                                     uint parallel_gc_threads) {
  TraceTime tm("steal task setup", print_phases(), true, gclog_or_tty);

  // Once a thread has drained it's stack, it should try to steal chunks from
  // other threads.
  if (parallel_gc_threads > 1) {
    for (uint j = 0; j < parallel_gc_threads; j++) {
      q->enqueue(new StealChunkCompactionTask(terminator_ptr));
    }
  }
}

void PSParallelCompact::compact() {
  EventMark m("5 compact");
  // trace("5");
  TraceTime tm("compaction phase", print_phases(), true, gclog_or_tty);

  ParallelScavengeHeap* heap = (ParallelScavengeHeap*)Universe::heap();
  assert(heap->kind() == CollectedHeap::ParallelScavengeHeap, "Sanity");
  PSOldGen* old_gen = heap->old_gen();
  old_gen->start_array()->reset();
  uint parallel_gc_threads = heap->gc_task_manager()->workers();
  TaskQueueSetSuper* qset = ParCompactionManager::chunk_array();
  ParallelTaskTerminator terminator(parallel_gc_threads, qset);

  GCTaskQueue* q = GCTaskQueue::create();
  enqueue_chunk_draining_tasks(q, parallel_gc_threads);
  enqueue_dense_prefix_tasks(q, parallel_gc_threads);
  enqueue_chunk_stealing_tasks(q, &terminator, parallel_gc_threads);

  {
    TraceTime tm_pc("par compact", print_phases(), true, gclog_or_tty);

    WaitForBarrierGCTask* fin = WaitForBarrierGCTask::create();
    q->enqueue(fin);

    gc_task_manager()->add_list(q);

    fin->wait_for();

    // We have to release the barrier tasks!
    WaitForBarrierGCTask::destroy(fin);

#ifdef  ASSERT
    // Verify that all chunks have been processed before the deferred updates.
    // Note that perm_space_id is skipped; this type of verification is not
    // valid until the perm gen is compacted by chunks.
    for (unsigned int id = old_space_id; id < last_space_id; ++id) {
      verify_complete(SpaceId(id));
    }
#endif
  }

  {
    // Update the deferred objects, if any.  Any compaction manager can be used.
    TraceTime tm_du("deferred updates", print_phases(), true, gclog_or_tty);
    ParCompactionManager* cm = ParCompactionManager::manager_array(0);
    for (unsigned int id = old_space_id; id < last_space_id; ++id) {
      update_deferred_objects(cm, SpaceId(id));
    }
  }
}

#ifdef  ASSERT
void PSParallelCompact::verify_complete(SpaceId space_id) {
  // All Chunks between space bottom() to new_top() should be marked as filled
  // and all Chunks between new_top() and top() should be available (i.e.,
  // should have been emptied).
  ParallelCompactData& sd = summary_data();
  SpaceInfo si = _space_info[space_id];
  HeapWord* new_top_addr = sd.chunk_align_up(si.new_top());
  HeapWord* old_top_addr = sd.chunk_align_up(si.space()->top());
  const size_t beg_chunk = sd.addr_to_chunk_idx(si.space()->bottom());
  const size_t new_top_chunk = sd.addr_to_chunk_idx(new_top_addr);
  const size_t old_top_chunk = sd.addr_to_chunk_idx(old_top_addr);

  bool issued_a_warning = false;

  size_t cur_chunk;
  for (cur_chunk = beg_chunk; cur_chunk < new_top_chunk; ++cur_chunk) {
    const ChunkData* const c = sd.chunk(cur_chunk);
    if (!c->completed()) {
      warning("chunk " SIZE_FORMAT " not filled:  "
              "destination_count=" SIZE_FORMAT,
              cur_chunk, c->destination_count());
      issued_a_warning = true;
    }
  }

  for (cur_chunk = new_top_chunk; cur_chunk < old_top_chunk; ++cur_chunk) {
    const ChunkData* const c = sd.chunk(cur_chunk);
    if (!c->available()) {
      warning("chunk " SIZE_FORMAT " not empty:   "
              "destination_count=" SIZE_FORMAT,
              cur_chunk, c->destination_count());
      issued_a_warning = true;
    }
  }

  if (issued_a_warning) {
    print_chunk_ranges();
  }
}
#endif  // #ifdef ASSERT

void PSParallelCompact::compact_serial(ParCompactionManager* cm) {
  EventMark m("5 compact serial");
  TraceTime tm("compact serial", print_phases(), true, gclog_or_tty);

  ParallelScavengeHeap* heap = (ParallelScavengeHeap*)Universe::heap();
  assert(heap->kind() == CollectedHeap::ParallelScavengeHeap, "Sanity");

  PSYoungGen* young_gen = heap->young_gen();
  PSOldGen* old_gen = heap->old_gen();

  old_gen->start_array()->reset();
  old_gen->move_and_update(cm);
  young_gen->move_and_update(cm);
}

void PSParallelCompact::follow_root(ParCompactionManager* cm, oop* p) {
  assert(!Universe::heap()->is_in_reserved(p),
         "roots shouldn't be things within the heap");
#ifdef VALIDATE_MARK_SWEEP
  if (ValidateMarkSweep) {
    guarantee(!_root_refs_stack->contains(p), "should only be in here once");
    _root_refs_stack->push(p);
  }
#endif
  oop m = *p;
  if (m != NULL && mark_bitmap()->is_unmarked(m)) {
    if (mark_obj(m)) {
      m->follow_contents(cm);  // Follow contents of the marked object
    }
  }
  follow_stack(cm);
}

void PSParallelCompact::follow_stack(ParCompactionManager* cm) {
  while(!cm->overflow_stack()->is_empty()) {
    oop obj = cm->overflow_stack()->pop();
    obj->follow_contents(cm);
  }

  oop obj;
  // obj is a reference!!!
  while (cm->marking_stack()->pop_local(obj)) {
    // It would be nice to assert about the type of objects we might
    // pop, but they can come from anywhere, unfortunately.
    obj->follow_contents(cm);
  }
}

void
PSParallelCompact::follow_weak_klass_links(ParCompactionManager* serial_cm) {
  // All klasses on the revisit stack are marked at this point.
  // Update and follow all subklass, sibling and implementor links.
  for (uint i = 0; i < ParallelGCThreads+1; i++) {
    ParCompactionManager* cm = ParCompactionManager::manager_array(i);
    KeepAliveClosure keep_alive_closure(cm);
    for (int i = 0; i < cm->revisit_klass_stack()->length(); i++) {
      cm->revisit_klass_stack()->at(i)->follow_weak_klass_links(
        is_alive_closure(),
        &keep_alive_closure);
    }
    follow_stack(cm);
  }
}

void
PSParallelCompact::revisit_weak_klass_link(ParCompactionManager* cm, Klass* k) {
  cm->revisit_klass_stack()->push(k);
}

#ifdef VALIDATE_MARK_SWEEP

void PSParallelCompact::track_adjusted_pointer(oop* p, oop newobj, bool isroot) {
  if (!ValidateMarkSweep)
    return;

  if (!isroot) {
    if (_pointer_tracking) {
      guarantee(_adjusted_pointers->contains(p), "should have seen this pointer");
      _adjusted_pointers->remove(p);
    }
  } else {
    ptrdiff_t index = _root_refs_stack->find(p);
    if (index != -1) {
      int l = _root_refs_stack->length();
      if (l > 0 && l - 1 != index) {
        oop* last = _root_refs_stack->pop();
        assert(last != p, "should be different");
        _root_refs_stack->at_put(index, last);
      } else {
        _root_refs_stack->remove(p);
      }
    }
  }
}


void PSParallelCompact::check_adjust_pointer(oop* p) {
  _adjusted_pointers->push(p);
}


class AdjusterTracker: public OopClosure {
 public:
  AdjusterTracker() {};
  void do_oop(oop* o)   { PSParallelCompact::check_adjust_pointer(o); }
};


void PSParallelCompact::track_interior_pointers(oop obj) {
  if (ValidateMarkSweep) {
    _adjusted_pointers->clear();
    _pointer_tracking = true;

    AdjusterTracker checker;
    obj->oop_iterate(&checker);
  }
}


void PSParallelCompact::check_interior_pointers() {
  if (ValidateMarkSweep) {
    _pointer_tracking = false;
    guarantee(_adjusted_pointers->length() == 0, "should have processed the same pointers");
  }
}


void PSParallelCompact::reset_live_oop_tracking(bool at_perm) {
  if (ValidateMarkSweep) {
    guarantee((size_t)_live_oops->length() == _live_oops_index, "should be at end of live oops");
    _live_oops_index = at_perm ? _live_oops_index_at_perm : 0;
  }
}


void PSParallelCompact::register_live_oop(oop p, size_t size) {
  if (ValidateMarkSweep) {
    _live_oops->push(p);
    _live_oops_size->push(size);
    _live_oops_index++;
  }
}

void PSParallelCompact::validate_live_oop(oop p, size_t size) {
  if (ValidateMarkSweep) {
    oop obj = _live_oops->at((int)_live_oops_index);
    guarantee(obj == p, "should be the same object");
    guarantee(_live_oops_size->at((int)_live_oops_index) == size, "should be the same size");
    _live_oops_index++;
  }
}

void PSParallelCompact::live_oop_moved_to(HeapWord* q, size_t size,
                                  HeapWord* compaction_top) {
  assert(oop(q)->forwardee() == NULL || oop(q)->forwardee() == oop(compaction_top),
         "should be moved to forwarded location");
  if (ValidateMarkSweep) {
    PSParallelCompact::validate_live_oop(oop(q), size);
    _live_oops_moved_to->push(oop(compaction_top));
  }
  if (RecordMarkSweepCompaction) {
    _cur_gc_live_oops->push(q);
    _cur_gc_live_oops_moved_to->push(compaction_top);
    _cur_gc_live_oops_size->push(size);
  }
}


void PSParallelCompact::compaction_complete() {
  if (RecordMarkSweepCompaction) {
    GrowableArray<HeapWord*>* _tmp_live_oops          = _cur_gc_live_oops;
    GrowableArray<HeapWord*>* _tmp_live_oops_moved_to = _cur_gc_live_oops_moved_to;
    GrowableArray<size_t>   * _tmp_live_oops_size     = _cur_gc_live_oops_size;

    _cur_gc_live_oops           = _last_gc_live_oops;
    _cur_gc_live_oops_moved_to  = _last_gc_live_oops_moved_to;
    _cur_gc_live_oops_size      = _last_gc_live_oops_size;
    _last_gc_live_oops          = _tmp_live_oops;
    _last_gc_live_oops_moved_to = _tmp_live_oops_moved_to;
    _last_gc_live_oops_size     = _tmp_live_oops_size;
  }
}


void PSParallelCompact::print_new_location_of_heap_address(HeapWord* q) {
  if (!RecordMarkSweepCompaction) {
    tty->print_cr("Requires RecordMarkSweepCompaction to be enabled");
    return;
  }

  if (_last_gc_live_oops == NULL) {
    tty->print_cr("No compaction information gathered yet");
    return;
  }

  for (int i = 0; i < _last_gc_live_oops->length(); i++) {
    HeapWord* old_oop = _last_gc_live_oops->at(i);
    size_t    sz      = _last_gc_live_oops_size->at(i);
    if (old_oop <= q && q < (old_oop + sz)) {
      HeapWord* new_oop = _last_gc_live_oops_moved_to->at(i);
      size_t offset = (q - old_oop);
      tty->print_cr("Address " PTR_FORMAT, q);
      tty->print_cr(" Was in oop " PTR_FORMAT ", size %d, at offset %d", old_oop, sz, offset);
      tty->print_cr(" Now in oop " PTR_FORMAT ", actual address " PTR_FORMAT, new_oop, new_oop + offset);
      return;
    }
  }

  tty->print_cr("Address " PTR_FORMAT " not found in live oop information from last GC", q);
}
#endif //VALIDATE_MARK_SWEEP

void PSParallelCompact::adjust_pointer(oop* p, bool isroot) {
  oop obj = *p;
  VALIDATE_MARK_SWEEP_ONLY(oop saved_new_pointer = NULL);
  if (obj != NULL) {
    oop new_pointer = (oop) summary_data().calc_new_pointer(obj);
    assert(new_pointer != NULL ||                     // is forwarding ptr?
           obj->is_shared(),                          // never forwarded?
           "should have a new location");
    // Just always do the update unconditionally?
    if (new_pointer != NULL) {
      *p = new_pointer;
      assert(Universe::heap()->is_in_reserved(new_pointer),
             "should be in object space");
      VALIDATE_MARK_SWEEP_ONLY(saved_new_pointer = new_pointer);
    }
  }
  VALIDATE_MARK_SWEEP_ONLY(track_adjusted_pointer(p, saved_new_pointer, isroot));
}

// Update interior oops in the ranges of chunks [beg_chunk, end_chunk).
void
PSParallelCompact::update_and_deadwood_in_dense_prefix(ParCompactionManager* cm,
                                                       SpaceId space_id,
                                                       size_t beg_chunk,
                                                       size_t end_chunk) {
  ParallelCompactData& sd = summary_data();
  ParMarkBitMap* const mbm = mark_bitmap();

  HeapWord* beg_addr = sd.chunk_to_addr(beg_chunk);
  HeapWord* const end_addr = sd.chunk_to_addr(end_chunk);
  assert(beg_chunk <= end_chunk, "bad chunk range");
  assert(end_addr <= dense_prefix(space_id), "not in the dense prefix");

#ifdef  ASSERT
  // Claim the chunks to avoid triggering an assert when they are marked as
  // filled.
  for (size_t claim_chunk = beg_chunk; claim_chunk < end_chunk; ++claim_chunk) {
    assert(sd.chunk(claim_chunk)->claim_unsafe(), "claim() failed");
  }
#endif  // #ifdef ASSERT

  if (beg_addr != space(space_id)->bottom()) {
    // Find the first live object or block of dead space that *starts* in this
    // range of chunks.  If a partial object crosses onto the chunk, skip it; it
    // will be marked for 'deferred update' when the object head is processed.
    // If dead space crosses onto the chunk, it is also skipped; it will be
    // filled when the prior chunk is processed.  If neither of those apply, the
    // first word in the chunk is the start of a live object or dead space.
    assert(beg_addr > space(space_id)->bottom(), "sanity");
    const ChunkData* const cp = sd.chunk(beg_chunk);
    if (cp->partial_obj_size() != 0) {
      beg_addr = sd.partial_obj_end(beg_chunk);
    } else if (dead_space_crosses_boundary(cp, mbm->addr_to_bit(beg_addr))) {
      beg_addr = mbm->find_obj_beg(beg_addr, end_addr);
    }
  }

  if (beg_addr < end_addr) {
    // A live object or block of dead space starts in this range of Chunks.
     HeapWord* const dense_prefix_end = dense_prefix(space_id);

    // Create closures and iterate.
    UpdateOnlyClosure update_closure(mbm, cm, space_id);
    FillClosure fill_closure(cm, space_id);
    ParMarkBitMap::IterationStatus status;
    status = mbm->iterate(&update_closure, &fill_closure, beg_addr, end_addr,
                          dense_prefix_end);
    if (status == ParMarkBitMap::incomplete) {
      update_closure.do_addr(update_closure.source());
    }
  }

  // Mark the chunks as filled.
  ChunkData* const beg_cp = sd.chunk(beg_chunk);
  ChunkData* const end_cp = sd.chunk(end_chunk);
  for (ChunkData* cp = beg_cp; cp < end_cp; ++cp) {
    cp->set_completed();
  }
}

// Return the SpaceId for the space containing addr.  If addr is not in the
// heap, last_space_id is returned.  In debug mode it expects the address to be
// in the heap and asserts such.
PSParallelCompact::SpaceId PSParallelCompact::space_id(HeapWord* addr) {
  assert(Universe::heap()->is_in_reserved(addr), "addr not in the heap");

  for (unsigned int id = perm_space_id; id < last_space_id; ++id) {
    if (_space_info[id].space()->contains(addr)) {
      return SpaceId(id);
    }
  }

  assert(false, "no space contains the addr");
  return last_space_id;
}

void PSParallelCompact::update_deferred_objects(ParCompactionManager* cm,
                                                SpaceId id) {
  assert(id < last_space_id, "bad space id");

  ParallelCompactData& sd = summary_data();
  const SpaceInfo* const space_info = _space_info + id;
  ObjectStartArray* const start_array = space_info->start_array();

  const MutableSpace* const space = space_info->space();
  assert(space_info->dense_prefix() >= space->bottom(), "dense_prefix not set");
  HeapWord* const beg_addr = space_info->dense_prefix();
  HeapWord* const end_addr = sd.chunk_align_up(space_info->new_top());

  const ChunkData* const beg_chunk = sd.addr_to_chunk_ptr(beg_addr);
  const ChunkData* const end_chunk = sd.addr_to_chunk_ptr(end_addr);
  const ChunkData* cur_chunk;
  for (cur_chunk = beg_chunk; cur_chunk < end_chunk; ++cur_chunk) {
    HeapWord* const addr = cur_chunk->deferred_obj_addr();
    if (addr != NULL) {
      if (start_array != NULL) {
        start_array->allocate_block(addr);
      }
      oop(addr)->update_contents(cm);
      assert(oop(addr)->is_oop_or_null(), "should be an oop now");
    }
  }
}

// Skip over count live words starting from beg, and return the address of the
// next live word.  Unless marked, the word corresponding to beg is assumed to
// be dead.  Callers must either ensure beg does not correspond to the middle of
// an object, or account for those live words in some other way.  Callers must
// also ensure that there are enough live words in the range [beg, end) to skip.
HeapWord*
PSParallelCompact::skip_live_words(HeapWord* beg, HeapWord* end, size_t count)
{
  assert(count > 0, "sanity");

  ParMarkBitMap* m = mark_bitmap();
  idx_t bits_to_skip = m->words_to_bits(count);
  idx_t cur_beg = m->addr_to_bit(beg);
  const idx_t search_end = BitMap::word_align_up(m->addr_to_bit(end));

  do {
    cur_beg = m->find_obj_beg(cur_beg, search_end);
    idx_t cur_end = m->find_obj_end(cur_beg, search_end);
    const size_t obj_bits = cur_end - cur_beg + 1;
    if (obj_bits > bits_to_skip) {
      return m->bit_to_addr(cur_beg + bits_to_skip);
    }
    bits_to_skip -= obj_bits;
    cur_beg = cur_end + 1;
  } while (bits_to_skip > 0);

  // Skipping the desired number of words landed just past the end of an object.
  // Find the start of the next object.
  cur_beg = m->find_obj_beg(cur_beg, search_end);
  assert(cur_beg < m->addr_to_bit(end), "not enough live words to skip");
  return m->bit_to_addr(cur_beg);
}

HeapWord*
PSParallelCompact::first_src_addr(HeapWord* const dest_addr,
                                 size_t src_chunk_idx)
{
  ParMarkBitMap* const bitmap = mark_bitmap();
  const ParallelCompactData& sd = summary_data();
  const size_t ChunkSize = ParallelCompactData::ChunkSize;

  assert(sd.is_chunk_aligned(dest_addr), "not aligned");

  const ChunkData* const src_chunk_ptr = sd.chunk(src_chunk_idx);
  const size_t partial_obj_size = src_chunk_ptr->partial_obj_size();
  HeapWord* const src_chunk_destination = src_chunk_ptr->destination();

  assert(dest_addr >= src_chunk_destination, "wrong src chunk");
  assert(src_chunk_ptr->data_size() > 0, "src chunk cannot be empty");

  HeapWord* const src_chunk_beg = sd.chunk_to_addr(src_chunk_idx);
  HeapWord* const src_chunk_end = src_chunk_beg + ChunkSize;

  HeapWord* addr = src_chunk_beg;
  if (dest_addr == src_chunk_destination) {
    // Return the first live word in the source chunk.
    if (partial_obj_size == 0) {
      addr = bitmap->find_obj_beg(addr, src_chunk_end);
      assert(addr < src_chunk_end, "no objects start in src chunk");
    }
    return addr;
  }

  // Must skip some live data.
  size_t words_to_skip = dest_addr - src_chunk_destination;
  assert(src_chunk_ptr->data_size() > words_to_skip, "wrong src chunk");

  if (partial_obj_size >= words_to_skip) {
    // All the live words to skip are part of the partial object.
    addr += words_to_skip;
    if (partial_obj_size == words_to_skip) {
      // Find the first live word past the partial object.
      addr = bitmap->find_obj_beg(addr, src_chunk_end);
      assert(addr < src_chunk_end, "wrong src chunk");
    }
    return addr;
  }

  // Skip over the partial object (if any).
  if (partial_obj_size != 0) {
    words_to_skip -= partial_obj_size;
    addr += partial_obj_size;
  }

  // Skip over live words due to objects that start in the chunk.
  addr = skip_live_words(addr, src_chunk_end, words_to_skip);
  assert(addr < src_chunk_end, "wrong src chunk");
  return addr;
}

void PSParallelCompact::decrement_destination_counts(ParCompactionManager* cm,
                                                     size_t beg_chunk,
                                                     HeapWord* end_addr)
{
  ParallelCompactData& sd = summary_data();
  ChunkData* const beg = sd.chunk(beg_chunk);
  HeapWord* const end_addr_aligned_up = sd.chunk_align_up(end_addr);
  ChunkData* const end = sd.addr_to_chunk_ptr(end_addr_aligned_up);
  size_t cur_idx = beg_chunk;
  for (ChunkData* cur = beg; cur < end; ++cur, ++cur_idx) {
    assert(cur->data_size() > 0, "chunk must have live data");
    cur->decrement_destination_count();
    if (cur_idx <= cur->source_chunk() && cur->available() && cur->claim()) {
      cm->save_for_processing(cur_idx);
    }
  }
}

size_t PSParallelCompact::next_src_chunk(MoveAndUpdateClosure& closure,
                                         SpaceId& src_space_id,
                                         HeapWord*& src_space_top,
                                         HeapWord* end_addr)
{
  typedef ParallelCompactData::ChunkData ChunkData;

  ParallelCompactData& sd = PSParallelCompact::summary_data();
  const size_t chunk_size = ParallelCompactData::ChunkSize;

  size_t src_chunk_idx = 0;

  // Skip empty chunks (if any) up to the top of the space.
  HeapWord* const src_aligned_up = sd.chunk_align_up(end_addr);
  ChunkData* src_chunk_ptr = sd.addr_to_chunk_ptr(src_aligned_up);
  HeapWord* const top_aligned_up = sd.chunk_align_up(src_space_top);
  const ChunkData* const top_chunk_ptr = sd.addr_to_chunk_ptr(top_aligned_up);
  while (src_chunk_ptr < top_chunk_ptr && src_chunk_ptr->data_size() == 0) {
    ++src_chunk_ptr;
  }

  if (src_chunk_ptr < top_chunk_ptr) {
    // The next source chunk is in the current space.  Update src_chunk_idx and
    // the source address to match src_chunk_ptr.
    src_chunk_idx = sd.chunk(src_chunk_ptr);
    HeapWord* const src_chunk_addr = sd.chunk_to_addr(src_chunk_idx);
    if (src_chunk_addr > closure.source()) {
      closure.set_source(src_chunk_addr);
    }
    return src_chunk_idx;
  }

  // Switch to a new source space and find the first non-empty chunk.
  unsigned int space_id = src_space_id + 1;
  assert(space_id < last_space_id, "not enough spaces");

  HeapWord* const destination = closure.destination();

  do {
    MutableSpace* space = _space_info[space_id].space();
    HeapWord* const bottom = space->bottom();
    const ChunkData* const bottom_cp = sd.addr_to_chunk_ptr(bottom);

    // Iterate over the spaces that do not compact into themselves.
    if (bottom_cp->destination() != bottom) {
      HeapWord* const top_aligned_up = sd.chunk_align_up(space->top());
      const ChunkData* const top_cp = sd.addr_to_chunk_ptr(top_aligned_up);

      for (const ChunkData* src_cp = bottom_cp; src_cp < top_cp; ++src_cp) {
        if (src_cp->live_obj_size() > 0) {
          // Found it.
          assert(src_cp->destination() == destination,
                 "first live obj in the space must match the destination");
          assert(src_cp->partial_obj_size() == 0,
                 "a space cannot begin with a partial obj");

          src_space_id = SpaceId(space_id);
          src_space_top = space->top();
          const size_t src_chunk_idx = sd.chunk(src_cp);
          closure.set_source(sd.chunk_to_addr(src_chunk_idx));
          return src_chunk_idx;
        } else {
          assert(src_cp->data_size() == 0, "sanity");
        }
      }
    }
  } while (++space_id < last_space_id);

  assert(false, "no source chunk was found");
  return 0;
}

void PSParallelCompact::fill_chunk(ParCompactionManager* cm, size_t chunk_idx)
{
  typedef ParMarkBitMap::IterationStatus IterationStatus;
  const size_t ChunkSize = ParallelCompactData::ChunkSize;
  ParMarkBitMap* const bitmap = mark_bitmap();
  ParallelCompactData& sd = summary_data();
  ChunkData* const chunk_ptr = sd.chunk(chunk_idx);

  // Get the items needed to construct the closure.
  HeapWord* dest_addr = sd.chunk_to_addr(chunk_idx);
  SpaceId dest_space_id = space_id(dest_addr);
  ObjectStartArray* start_array = _space_info[dest_space_id].start_array();
  HeapWord* new_top = _space_info[dest_space_id].new_top();
  assert(dest_addr < new_top, "sanity");
  const size_t words = MIN2(pointer_delta(new_top, dest_addr), ChunkSize);

  // Get the source chunk and related info.
  size_t src_chunk_idx = chunk_ptr->source_chunk();
  SpaceId src_space_id = space_id(sd.chunk_to_addr(src_chunk_idx));
  HeapWord* src_space_top = _space_info[src_space_id].space()->top();

  MoveAndUpdateClosure closure(bitmap, cm, start_array, dest_addr, words);
  closure.set_source(first_src_addr(dest_addr, src_chunk_idx));

  // Adjust src_chunk_idx to prepare for decrementing destination counts (the
  // destination count is not decremented when a chunk is copied to itself).
  if (src_chunk_idx == chunk_idx) {
    src_chunk_idx += 1;
  }

  if (bitmap->is_unmarked(closure.source())) {
    // The first source word is in the middle of an object; copy the remainder
    // of the object or as much as will fit.  The fact that pointer updates were
    // deferred will be noted when the object header is processed.
    HeapWord* const old_src_addr = closure.source();
    closure.copy_partial_obj();
    if (closure.is_full()) {
      decrement_destination_counts(cm, src_chunk_idx, closure.source());
      chunk_ptr->set_deferred_obj_addr(NULL);
      chunk_ptr->set_completed();
      return;
    }

    HeapWord* const end_addr = sd.chunk_align_down(closure.source());
    if (sd.chunk_align_down(old_src_addr) != end_addr) {
      // The partial object was copied from more than one source chunk.
      decrement_destination_counts(cm, src_chunk_idx, end_addr);

      // Move to the next source chunk, possibly switching spaces as well.  All
      // args except end_addr may be modified.
      src_chunk_idx = next_src_chunk(closure, src_space_id, src_space_top,
                                     end_addr);
    }
  }

  do {
    HeapWord* const cur_addr = closure.source();
    HeapWord* const end_addr = MIN2(sd.chunk_align_up(cur_addr + 1),
                                    src_space_top);
    IterationStatus status = bitmap->iterate(&closure, cur_addr, end_addr);

    if (status == ParMarkBitMap::incomplete) {
      // The last obj that starts in the source chunk does not end in the chunk.
      assert(closure.source() < end_addr, "sanity")
      HeapWord* const obj_beg = closure.source();
      HeapWord* const range_end = MIN2(obj_beg + closure.words_remaining(),
                                       src_space_top);
      HeapWord* const obj_end = bitmap->find_obj_end(obj_beg, range_end);
      if (obj_end < range_end) {
        // The end was found; the entire object will fit.
        status = closure.do_addr(obj_beg, bitmap->obj_size(obj_beg, obj_end));
        assert(status != ParMarkBitMap::would_overflow, "sanity");
      } else {
        // The end was not found; the object will not fit.
        assert(range_end < src_space_top, "obj cannot cross space boundary");
        status = ParMarkBitMap::would_overflow;
      }
    }

    if (status == ParMarkBitMap::would_overflow) {
      // The last object did not fit.  Note that interior oop updates were
      // deferred, then copy enough of the object to fill the chunk.
      chunk_ptr->set_deferred_obj_addr(closure.destination());
      status = closure.copy_until_full(); // copies from closure.source()

      decrement_destination_counts(cm, src_chunk_idx, closure.source());
      chunk_ptr->set_completed();
      return;
    }

    if (status == ParMarkBitMap::full) {
      decrement_destination_counts(cm, src_chunk_idx, closure.source());
      chunk_ptr->set_deferred_obj_addr(NULL);
      chunk_ptr->set_completed();
      return;
    }

    decrement_destination_counts(cm, src_chunk_idx, end_addr);

    // Move to the next source chunk, possibly switching spaces as well.  All
    // args except end_addr may be modified.
    src_chunk_idx = next_src_chunk(closure, src_space_id, src_space_top,
                                   end_addr);
  } while (true);
}

void
PSParallelCompact::move_and_update(ParCompactionManager* cm, SpaceId space_id) {
  const MutableSpace* sp = space(space_id);
  if (sp->is_empty()) {
    return;
  }

  ParallelCompactData& sd = PSParallelCompact::summary_data();
  ParMarkBitMap* const bitmap = mark_bitmap();
  HeapWord* const dp_addr = dense_prefix(space_id);
  HeapWord* beg_addr = sp->bottom();
  HeapWord* end_addr = sp->top();

#ifdef ASSERT
  assert(beg_addr <= dp_addr && dp_addr <= end_addr, "bad dense prefix");
  if (cm->should_verify_only()) {
    VerifyUpdateClosure verify_update(cm, sp);
    bitmap->iterate(&verify_update, beg_addr, end_addr);
    return;
  }

  if (cm->should_reset_only()) {
    ResetObjectsClosure reset_objects(cm);
    bitmap->iterate(&reset_objects, beg_addr, end_addr);
    return;
  }
#endif

  const size_t beg_chunk = sd.addr_to_chunk_idx(beg_addr);
  const size_t dp_chunk = sd.addr_to_chunk_idx(dp_addr);
  if (beg_chunk < dp_chunk) {
    update_and_deadwood_in_dense_prefix(cm, space_id, beg_chunk, dp_chunk);
  }

  // The destination of the first live object that starts in the chunk is one
  // past the end of the partial object entering the chunk (if any).
  HeapWord* const dest_addr = sd.partial_obj_end(dp_chunk);
  HeapWord* const new_top = _space_info[space_id].new_top();
  assert(new_top >= dest_addr, "bad new_top value");
  const size_t words = pointer_delta(new_top, dest_addr);

  if (words > 0) {
    ObjectStartArray* start_array = _space_info[space_id].start_array();
    MoveAndUpdateClosure closure(bitmap, cm, start_array, dest_addr, words);

    ParMarkBitMap::IterationStatus status;
    status = bitmap->iterate(&closure, dest_addr, end_addr);
    assert(status == ParMarkBitMap::full, "iteration not complete");
    assert(bitmap->find_obj_beg(closure.source(), end_addr) == end_addr,
           "live objects skipped because closure is full");
  }
}

jlong PSParallelCompact::millis_since_last_gc() {
  jlong ret_val = os::javaTimeMillis() - _time_of_last_gc;
  // XXX See note in genCollectedHeap::millis_since_last_gc().
  if (ret_val < 0) {
    NOT_PRODUCT(warning("time warp: %d", ret_val);)
    return 0;
  }
  return ret_val;
}

void PSParallelCompact::reset_millis_since_last_gc() {
  _time_of_last_gc = os::javaTimeMillis();
}

ParMarkBitMap::IterationStatus MoveAndUpdateClosure::copy_until_full()
{
  if (source() != destination()) {
    assert(source() > destination(), "must copy to the left");
    Copy::aligned_conjoint_words(source(), destination(), words_remaining());
  }
  update_state(words_remaining());
  assert(is_full(), "sanity");
  return ParMarkBitMap::full;
}

void MoveAndUpdateClosure::copy_partial_obj()
{
  size_t words = words_remaining();

  HeapWord* const range_end = MIN2(source() + words, bitmap()->region_end());
  HeapWord* const end_addr = bitmap()->find_obj_end(source(), range_end);
  if (end_addr < range_end) {
    words = bitmap()->obj_size(source(), end_addr);
  }

  // This test is necessary; if omitted, the pointer updates to a partial object
  // that crosses the dense prefix boundary could be overwritten.
  if (source() != destination()) {
    assert(source() > destination(), "must copy to the left");
    Copy::aligned_conjoint_words(source(), destination(), words);
  }
  update_state(words);
}

ParMarkBitMapClosure::IterationStatus
MoveAndUpdateClosure::do_addr(HeapWord* addr, size_t words) {
  assert(destination() != NULL, "sanity");
  assert(bitmap()->obj_size(addr) == words, "bad size");

  _source = addr;
  assert(PSParallelCompact::summary_data().calc_new_pointer(source()) ==
         destination(), "wrong destination");

  if (words > words_remaining()) {
    return ParMarkBitMap::would_overflow;
  }

  // The start_array must be updated even if the object is not moving.
  if (_start_array != NULL) {
    _start_array->allocate_block(destination());
  }

  if (destination() != source()) {
    assert(destination() < source(), "must copy to the left");
    Copy::aligned_conjoint_words(source(), destination(), words);
  }

  oop moved_oop = (oop) destination();
  moved_oop->update_contents(compaction_manager());
  assert(moved_oop->is_oop_or_null(), "Object should be whole at this point");

  update_state(words);
  assert(destination() == (HeapWord*)moved_oop + moved_oop->size(), "sanity");
  return is_full() ? ParMarkBitMap::full : ParMarkBitMap::incomplete;
}

UpdateOnlyClosure::UpdateOnlyClosure(ParMarkBitMap* mbm,
                                     ParCompactionManager* cm,
                                     PSParallelCompact::SpaceId space_id) :
  ParMarkBitMapClosure(mbm, cm),
  _space_id(space_id),
  _start_array(PSParallelCompact::start_array(space_id))
{
}

// Updates the references in the object to their new values.
ParMarkBitMapClosure::IterationStatus
UpdateOnlyClosure::do_addr(HeapWord* addr, size_t words) {
  do_addr(addr);
  return ParMarkBitMap::incomplete;
}

BitBlockUpdateClosure::BitBlockUpdateClosure(ParMarkBitMap* mbm,
                        ParCompactionManager* cm,
                        size_t chunk_index) :
                        ParMarkBitMapClosure(mbm, cm),
                        _live_data_left(0),
                        _cur_block(0) {
  _chunk_start =
    PSParallelCompact::summary_data().chunk_to_addr(chunk_index);
  _chunk_end =
    PSParallelCompact::summary_data().chunk_to_addr(chunk_index) +
                 ParallelCompactData::ChunkSize;
  _chunk_index = chunk_index;
  _cur_block =
    PSParallelCompact::summary_data().addr_to_block_idx(_chunk_start);
}

bool BitBlockUpdateClosure::chunk_contains_cur_block() {
  return ParallelCompactData::chunk_contains_block(_chunk_index, _cur_block);
}

void BitBlockUpdateClosure::reset_chunk(size_t chunk_index) {
  DEBUG_ONLY(ParallelCompactData::BlockData::set_cur_phase(7);)
  ParallelCompactData& sd = PSParallelCompact::summary_data();
  _chunk_index = chunk_index;
  _live_data_left = 0;
  _chunk_start = sd.chunk_to_addr(chunk_index);
  _chunk_end = sd.chunk_to_addr(chunk_index) + ParallelCompactData::ChunkSize;

  // The first block in this chunk
  size_t first_block =  sd.addr_to_block_idx(_chunk_start);
  size_t partial_live_size = sd.chunk(chunk_index)->partial_obj_size();

  // Set the offset to 0. By definition it should have that value
  // but it may have been written while processing an earlier chunk.
  if (partial_live_size == 0) {
    // No live object extends onto the chunk.  The first bit
    // in the bit map for the first chunk must be a start bit.
    // Although there may not be any marked bits, it is safe
    // to set it as a start bit.
    sd.block(first_block)->set_start_bit_offset(0);
    sd.block(first_block)->set_first_is_start_bit(true);
  } else if (sd.partial_obj_ends_in_block(first_block)) {
    sd.block(first_block)->set_end_bit_offset(0);
    sd.block(first_block)->set_first_is_start_bit(false);
  } else {
    // The partial object extends beyond the first block.
    // There is no object starting in the first block
    // so the offset and bit parity are not needed.
    // Set the the bit parity to start bit so assertions
    // work when not bit is found.
    sd.block(first_block)->set_end_bit_offset(0);
    sd.block(first_block)->set_first_is_start_bit(false);
  }
  _cur_block = first_block;
#ifdef ASSERT
  if (sd.block(first_block)->first_is_start_bit()) {
    assert(!sd.partial_obj_ends_in_block(first_block),
      "Partial object cannot end in first block");
  }

  if (PrintGCDetails && Verbose) {
    if (partial_live_size == 1) {
    gclog_or_tty->print_cr("first_block " PTR_FORMAT
      " _offset " PTR_FORMAT
      " _first_is_start_bit %d",
      first_block,
      sd.block(first_block)->raw_offset(),
      sd.block(first_block)->first_is_start_bit());
    }
  }
#endif
  DEBUG_ONLY(ParallelCompactData::BlockData::set_cur_phase(17);)
}

// This method is called when a object has been found (both beginning
// and end of the object) in the range of iteration.  This method is
// calculating the words of live data to the left of a block.  That live
// data includes any object starting to the left of the block (i.e.,
// the live-data-to-the-left of block AAA will include the full size
// of any object entering AAA).

ParMarkBitMapClosure::IterationStatus
BitBlockUpdateClosure::do_addr(HeapWord* addr, size_t words) {
  // add the size to the block data.
  HeapWord* obj = addr;
  ParallelCompactData& sd = PSParallelCompact::summary_data();

  assert(bitmap()->obj_size(obj) == words, "bad size");
  assert(_chunk_start <= obj, "object is not in chunk");
  assert(obj + words <= _chunk_end, "object is not in chunk");

  // Update the live data to the left
  size_t prev_live_data_left = _live_data_left;
  _live_data_left = _live_data_left + words;

  // Is this object in the current block.
  size_t block_of_obj = sd.addr_to_block_idx(obj);
  size_t block_of_obj_last = sd.addr_to_block_idx(obj + words - 1);
  HeapWord* block_of_obj_last_addr = sd.block_to_addr(block_of_obj_last);
  if (_cur_block < block_of_obj) {

    //
    // No object crossed the block boundary and this object was found
    // on the other side of the block boundary.  Update the offset for
    // the new block with the data size that does not include this object.
    //
    // The first bit in block_of_obj is a start bit except in the
    // case where the partial object for the chunk extends into
    // this block.
    if (sd.partial_obj_ends_in_block(block_of_obj)) {
      sd.block(block_of_obj)->set_end_bit_offset(prev_live_data_left);
    } else {
      sd.block(block_of_obj)->set_start_bit_offset(prev_live_data_left);
    }

    // Does this object pass beyond the its block?
    if (block_of_obj < block_of_obj_last) {
      // Object crosses block boundary.  Two blocks need to be udpated:
      //        the current block where the object started
      //        the block where the object ends
      //
      // The offset for blocks with no objects starting in them
      // (e.g., blocks between _cur_block and  block_of_obj_last)
      // should not be needed.
      // Note that block_of_obj_last may be in another chunk.  If so,
      // it should be overwritten later.  This is a problem (writting
      // into a block in a later chunk) for parallel execution.
      assert(obj < block_of_obj_last_addr,
        "Object should start in previous block");

      // obj is crossing into block_of_obj_last so the first bit
      // is and end bit.
      sd.block(block_of_obj_last)->set_end_bit_offset(_live_data_left);

      _cur_block = block_of_obj_last;
    } else {
      // _first_is_start_bit has already been set correctly
      // in the if-then-else above so don't reset it here.
      _cur_block = block_of_obj;
    }
  } else {
    // The current block only changes if the object extends beyound
    // the block it starts in.
    //
    // The object starts in the current block.
    // Does this object pass beyond the end of it?
    if (block_of_obj < block_of_obj_last) {
      // Object crosses block boundary.
      // See note above on possible blocks between block_of_obj and
      // block_of_obj_last
      assert(obj < block_of_obj_last_addr,
        "Object should start in previous block");

      sd.block(block_of_obj_last)->set_end_bit_offset(_live_data_left);

      _cur_block = block_of_obj_last;
    }
  }

  // Return incomplete if there are more blocks to be done.
  if (chunk_contains_cur_block()) {
    return ParMarkBitMap::incomplete;
  }
  return ParMarkBitMap::complete;
}

// Verify the new location using the forwarding pointer
// from MarkSweep::mark_sweep_phase2().  Set the mark_word
// to the initial value.
ParMarkBitMapClosure::IterationStatus
PSParallelCompact::VerifyUpdateClosure::do_addr(HeapWord* addr, size_t words) {
  // The second arg (words) is not used.
  oop obj = (oop) addr;
  HeapWord* forwarding_ptr = (HeapWord*) obj->mark()->decode_pointer();
  HeapWord* new_pointer = summary_data().calc_new_pointer(obj);
  if (forwarding_ptr == NULL) {
    // The object is dead or not moving.
    assert(bitmap()->is_unmarked(obj) || (new_pointer == (HeapWord*) obj),
           "Object liveness is wrong.");
    return ParMarkBitMap::incomplete;
  }
  assert(UseParallelOldGCDensePrefix ||
         (HeapMaximumCompactionInterval > 1) ||
         (MarkSweepAlwaysCompactCount > 1) ||
         (forwarding_ptr == new_pointer),
    "Calculation of new location is incorrect");
  return ParMarkBitMap::incomplete;
}

// Reset objects modified for debug checking.
ParMarkBitMapClosure::IterationStatus
PSParallelCompact::ResetObjectsClosure::do_addr(HeapWord* addr, size_t words) {
  // The second arg (words) is not used.
  oop obj = (oop) addr;
  obj->init_mark();
  return ParMarkBitMap::incomplete;
}

// Prepare for compaction.  This method is executed once
// (i.e., by a single thread) before compaction.
// Save the updated location of the intArrayKlassObj for
// filling holes in the dense prefix.
void PSParallelCompact::compact_prologue() {
  _updated_int_array_klass_obj = (klassOop)
    summary_data().calc_new_pointer(Universe::intArrayKlassObj());
}

// The initial implementation of this method created a field
// _next_compaction_space_id in SpaceInfo and initialized
// that field in SpaceInfo::initialize_space_info().  That
// required that _next_compaction_space_id be declared a
// SpaceId in SpaceInfo and that would have required that
// either SpaceId be declared in a separate class or that
// it be declared in SpaceInfo.  It didn't seem consistent
// to declare it in SpaceInfo (didn't really fit logically).
// Alternatively, defining a separate class to define SpaceId
// seem excessive.  This implementation is simple and localizes
// the knowledge.

PSParallelCompact::SpaceId
PSParallelCompact::next_compaction_space_id(SpaceId id) {
  assert(id < last_space_id, "id out of range");
  switch (id) {
    case perm_space_id :
      return last_space_id;
    case old_space_id :
      return eden_space_id;
    case eden_space_id :
      return from_space_id;
    case from_space_id :
      return to_space_id;
    case to_space_id :
      return last_space_id;
    default:
      assert(false, "Bad space id");
      return last_space_id;
  }
}

// Here temporarily for debugging
#ifdef ASSERT
  size_t ParallelCompactData::block_idx(BlockData* block) {
    size_t index = pointer_delta(block,
      PSParallelCompact::summary_data()._block_data, sizeof(BlockData));
    return index;
  }
#endif
