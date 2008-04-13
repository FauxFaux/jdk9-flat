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

class ParallelScavengeHeap;
class PSAdaptiveSizePolicy;
class PSYoungGen;
class PSOldGen;
class PSPermGen;
class ParCompactionManager;
class ParallelTaskTerminator;
class PSParallelCompact;
class GCTaskManager;
class GCTaskQueue;
class PreGCValues;
class MoveAndUpdateClosure;
class RefProcTaskExecutor;

class SpaceInfo
{
 public:
  MutableSpace* space() const { return _space; }

  // Where the free space will start after the collection.  Valid only after the
  // summary phase completes.
  HeapWord* new_top() const { return _new_top; }

  // Allows new_top to be set.
  HeapWord** new_top_addr() { return &_new_top; }

  // Where the smallest allowable dense prefix ends (used only for perm gen).
  HeapWord* min_dense_prefix() const { return _min_dense_prefix; }

  // Where the dense prefix ends, or the compacted region begins.
  HeapWord* dense_prefix() const { return _dense_prefix; }

  // The start array for the (generation containing the) space, or NULL if there
  // is no start array.
  ObjectStartArray* start_array() const { return _start_array; }

  void set_space(MutableSpace* s)           { _space = s; }
  void set_new_top(HeapWord* addr)          { _new_top = addr; }
  void set_min_dense_prefix(HeapWord* addr) { _min_dense_prefix = addr; }
  void set_dense_prefix(HeapWord* addr)     { _dense_prefix = addr; }
  void set_start_array(ObjectStartArray* s) { _start_array = s; }

 private:
  MutableSpace*     _space;
  HeapWord*         _new_top;
  HeapWord*         _min_dense_prefix;
  HeapWord*         _dense_prefix;
  ObjectStartArray* _start_array;
};

class ParallelCompactData
{
public:
  // Sizes are in HeapWords, unless indicated otherwise.
  static const size_t Log2ChunkSize;
  static const size_t ChunkSize;
  static const size_t ChunkSizeBytes;

  // Mask for the bits in a size_t to get an offset within a chunk.
  static const size_t ChunkSizeOffsetMask;
  // Mask for the bits in a pointer to get an offset within a chunk.
  static const size_t ChunkAddrOffsetMask;
  // Mask for the bits in a pointer to get the address of the start of a chunk.
  static const size_t ChunkAddrMask;

  static const size_t Log2BlockSize;
  static const size_t BlockSize;
  static const size_t BlockOffsetMask;
  static const size_t BlockMask;

  static const size_t BlocksPerChunk;

  class ChunkData
  {
  public:
    // Destination address of the chunk.
    HeapWord* destination() const { return _destination; }

    // The first chunk containing data destined for this chunk.
    size_t source_chunk() const { return _source_chunk; }

    // The object (if any) starting in this chunk and ending in a different
    // chunk that could not be updated during the main (parallel) compaction
    // phase.  This is different from _partial_obj_addr, which is an object that
    // extends onto a source chunk.  However, the two uses do not overlap in
    // time, so the same field is used to save space.
    HeapWord* deferred_obj_addr() const { return _partial_obj_addr; }

    // The starting address of the partial object extending onto the chunk.
    HeapWord* partial_obj_addr() const { return _partial_obj_addr; }

    // Size of the partial object extending onto the chunk (words).
    size_t partial_obj_size() const { return _partial_obj_size; }

    // Size of live data that lies within this chunk due to objects that start
    // in this chunk (words).  This does not include the partial object
    // extending onto the chunk (if any), or the part of an object that extends
    // onto the next chunk (if any).
    size_t live_obj_size() const { return _dc_and_los & los_mask; }

    // Total live data that lies within the chunk (words).
    size_t data_size() const { return partial_obj_size() + live_obj_size(); }

    // The destination_count is the number of other chunks to which data from
    // this chunk will be copied.  At the end of the summary phase, the valid
    // values of destination_count are
    //
    // 0 - data from the chunk will be compacted completely into itself, or the
    //     chunk is empty.  The chunk can be claimed and then filled.
    // 1 - data from the chunk will be compacted into 1 other chunk; some
    //     data from the chunk may also be compacted into the chunk itself.
    // 2 - data from the chunk will be copied to 2 other chunks.
    //
    // During compaction as chunks are emptied, the destination_count is
    // decremented (atomically) and when it reaches 0, it can be claimed and
    // then filled.
    //
    // A chunk is claimed for processing by atomically changing the
    // destination_count to the claimed value (dc_claimed).  After a chunk has
    // been filled, the destination_count should be set to the completed value
    // (dc_completed).
    inline uint destination_count() const;
    inline uint destination_count_raw() const;

    // The location of the java heap data that corresponds to this chunk.
    inline HeapWord* data_location() const;

    // The highest address referenced by objects in this chunk.
    inline HeapWord* highest_ref() const;

    // Whether this chunk is available to be claimed, has been claimed, or has
    // been completed.
    //
    // Minor subtlety:  claimed() returns true if the chunk is marked
    // completed(), which is desirable since a chunk must be claimed before it
    // can be completed.
    bool available() const { return _dc_and_los < dc_one; }
    bool claimed() const   { return _dc_and_los >= dc_claimed; }
    bool completed() const { return _dc_and_los >= dc_completed; }

    // These are not atomic.
    void set_destination(HeapWord* addr)       { _destination = addr; }
    void set_source_chunk(size_t chunk)        { _source_chunk = chunk; }
    void set_deferred_obj_addr(HeapWord* addr) { _partial_obj_addr = addr; }
    void set_partial_obj_addr(HeapWord* addr)  { _partial_obj_addr = addr; }
    void set_partial_obj_size(size_t words)    {
      _partial_obj_size = (chunk_sz_t) words;
    }

    inline void set_destination_count(uint count);
    inline void set_live_obj_size(size_t words);
    inline void set_data_location(HeapWord* addr);
    inline void set_completed();
    inline bool claim_unsafe();

    // These are atomic.
    inline void add_live_obj(size_t words);
    inline void set_highest_ref(HeapWord* addr);
    inline void decrement_destination_count();
    inline bool claim();

  private:
    // The type used to represent object sizes within a chunk.
    typedef uint chunk_sz_t;

    // Constants for manipulating the _dc_and_los field, which holds both the
    // destination count and live obj size.  The live obj size lives at the
    // least significant end so no masking is necessary when adding.
    static const chunk_sz_t dc_shift;           // Shift amount.
    static const chunk_sz_t dc_mask;            // Mask for destination count.
    static const chunk_sz_t dc_one;             // 1, shifted appropriately.
    static const chunk_sz_t dc_claimed;         // Chunk has been claimed.
    static const chunk_sz_t dc_completed;       // Chunk has been completed.
    static const chunk_sz_t los_mask;           // Mask for live obj size.

    HeapWord*           _destination;
    size_t              _source_chunk;
    HeapWord*           _partial_obj_addr;
    chunk_sz_t          _partial_obj_size;
    chunk_sz_t volatile _dc_and_los;
#ifdef ASSERT
    // These enable optimizations that are only partially implemented.  Use
    // debug builds to prevent the code fragments from breaking.
    HeapWord*           _data_location;
    HeapWord*           _highest_ref;
#endif  // #ifdef ASSERT

#ifdef ASSERT
   public:
    uint            _pushed;    // 0 until chunk is pushed onto a worker's stack
   private:
#endif
  };

  // 'Blocks' allow shorter sections of the bitmap to be searched.  Each Block
  // holds an offset, which is the amount of live data in the Chunk to the left
  // of the first live object in the Block.  This amount of live data will
  // include any object extending into the block. The first block in
  // a chunk does not include any partial object extending into the
  // the chunk.
  //
  // The offset also encodes the
  // 'parity' of the first 1 bit in the Block:  a positive offset means the
  // first 1 bit marks the start of an object, a negative offset means the first
  // 1 bit marks the end of an object.
  class BlockData
  {
   public:
    typedef short int blk_ofs_t;

    blk_ofs_t offset() const { return _offset >= 0 ? _offset : -_offset; }
    blk_ofs_t raw_offset() const { return _offset; }
    void set_first_is_start_bit(bool v) { _first_is_start_bit = v; }

#if 0
    // The need for this method was anticipated but it is
    // never actually used.  Do not include it for now.  If
    // it is needed, consider the problem of what is passed
    // as "v".  To avoid warning errors the method set_start_bit_offset()
    // was changed to take a size_t as the parameter and to do the
    // check for the possible overflow.  Doing the cast in these
    // methods better limits the potential problems because of
    // the size of the field to this class.
    void set_raw_offset(blk_ofs_t v) { _offset = v; }
#endif
    void set_start_bit_offset(size_t val) {
      assert(val >= 0, "sanity");
      _offset = (blk_ofs_t) val;
      assert(val == (size_t) _offset, "Value is too large");
      _first_is_start_bit = true;
    }
    void set_end_bit_offset(size_t val) {
      assert(val >= 0, "sanity");
      _offset = (blk_ofs_t) val;
      assert(val == (size_t) _offset, "Value is too large");
      _offset = - _offset;
      _first_is_start_bit = false;
    }
    bool first_is_start_bit() {
      assert(_set_phase > 0, "Not initialized");
      return _first_is_start_bit;
    }
    bool first_is_end_bit() {
      assert(_set_phase > 0, "Not initialized");
      return !_first_is_start_bit;
    }

   private:
    blk_ofs_t _offset;
    // This is temporary until the mark_bitmap is separated into
    // a start bit array and an end bit array.
    bool      _first_is_start_bit;
#ifdef ASSERT
    short     _set_phase;
    static short _cur_phase;
   public:
    static void set_cur_phase(short v) { _cur_phase = v; }
#endif
  };

public:
  ParallelCompactData();
  bool initialize(MemRegion covered_region);

  size_t chunk_count() const { return _chunk_count; }

  // Convert chunk indices to/from ChunkData pointers.
  inline ChunkData* chunk(size_t chunk_idx) const;
  inline size_t     chunk(const ChunkData* const chunk_ptr) const;

  // Returns true if the given address is contained within the chunk
  bool chunk_contains(size_t chunk_index, HeapWord* addr);

  size_t block_count() const { return _block_count; }
  inline BlockData* block(size_t n) const;

  // Returns true if the given block is in the given chunk.
  static bool chunk_contains_block(size_t chunk_index, size_t block_index);

  void add_obj(HeapWord* addr, size_t len);
  void add_obj(oop p, size_t len) { add_obj((HeapWord*)p, len); }

  // Fill in the chunks covering [beg, end) so that no data moves; i.e., the
  // destination of chunk n is simply the start of chunk n.  The argument beg
  // must be chunk-aligned; end need not be.
  void summarize_dense_prefix(HeapWord* beg, HeapWord* end);

  bool summarize(HeapWord* target_beg, HeapWord* target_end,
                 HeapWord* source_beg, HeapWord* source_end,
                 HeapWord** target_next, HeapWord** source_next = 0);

  void clear();
  void clear_range(size_t beg_chunk, size_t end_chunk);
  void clear_range(HeapWord* beg, HeapWord* end) {
    clear_range(addr_to_chunk_idx(beg), addr_to_chunk_idx(end));
  }

  // Return the number of words between addr and the start of the chunk
  // containing addr.
  inline size_t     chunk_offset(const HeapWord* addr) const;

  // Convert addresses to/from a chunk index or chunk pointer.
  inline size_t     addr_to_chunk_idx(const HeapWord* addr) const;
  inline ChunkData* addr_to_chunk_ptr(const HeapWord* addr) const;
  inline HeapWord*  chunk_to_addr(size_t chunk) const;
  inline HeapWord*  chunk_to_addr(size_t chunk, size_t offset) const;
  inline HeapWord*  chunk_to_addr(const ChunkData* chunk) const;

  inline HeapWord*  chunk_align_down(HeapWord* addr) const;
  inline HeapWord*  chunk_align_up(HeapWord* addr) const;
  inline bool       is_chunk_aligned(HeapWord* addr) const;

  // Analogous to chunk_offset() for blocks.
  size_t     block_offset(const HeapWord* addr) const;
  size_t     addr_to_block_idx(const HeapWord* addr) const;
  size_t     addr_to_block_idx(const oop obj) const {
    return addr_to_block_idx((HeapWord*) obj);
  }
  inline BlockData* addr_to_block_ptr(const HeapWord* addr) const;
  inline HeapWord*  block_to_addr(size_t block) const;

  // Return the address one past the end of the partial object.
  HeapWord* partial_obj_end(size_t chunk_idx) const;

  // Return the new location of the object p after the
  // the compaction.
  HeapWord* calc_new_pointer(HeapWord* addr);

  // Same as calc_new_pointer() using blocks.
  HeapWord* block_calc_new_pointer(HeapWord* addr);

  // Same as calc_new_pointer() using chunks.
  HeapWord* chunk_calc_new_pointer(HeapWord* addr);

  HeapWord* calc_new_pointer(oop p) {
    return calc_new_pointer((HeapWord*) p);
  }

  // Return the updated address for the given klass
  klassOop calc_new_klass(klassOop);

  // Given a block returns true if the partial object for the
  // corresponding chunk ends in the block.  Returns false, otherwise
  // If there is no partial object, returns false.
  bool partial_obj_ends_in_block(size_t block_index);

  // Returns the block index for the block
  static size_t block_idx(BlockData* block);

#ifdef  ASSERT
  void verify_clear(const PSVirtualSpace* vspace);
  void verify_clear();
#endif  // #ifdef ASSERT

private:
  bool initialize_block_data(size_t region_size);
  bool initialize_chunk_data(size_t region_size);
  PSVirtualSpace* create_vspace(size_t count, size_t element_size);

private:
  HeapWord*       _region_start;
#ifdef  ASSERT
  HeapWord*       _region_end;
#endif  // #ifdef ASSERT

  PSVirtualSpace* _chunk_vspace;
  ChunkData*      _chunk_data;
  size_t          _chunk_count;

  PSVirtualSpace* _block_vspace;
  BlockData*      _block_data;
  size_t          _block_count;
};

inline uint
ParallelCompactData::ChunkData::destination_count_raw() const
{
  return _dc_and_los & dc_mask;
}

inline uint
ParallelCompactData::ChunkData::destination_count() const
{
  return destination_count_raw() >> dc_shift;
}

inline void
ParallelCompactData::ChunkData::set_destination_count(uint count)
{
  assert(count <= (dc_completed >> dc_shift), "count too large");
  const chunk_sz_t live_sz = (chunk_sz_t) live_obj_size();
  _dc_and_los = (count << dc_shift) | live_sz;
}

inline void ParallelCompactData::ChunkData::set_live_obj_size(size_t words)
{
  assert(words <= los_mask, "would overflow");
  _dc_and_los = destination_count_raw() | (chunk_sz_t)words;
}

inline void ParallelCompactData::ChunkData::decrement_destination_count()
{
  assert(_dc_and_los < dc_claimed, "already claimed");
  assert(_dc_and_los >= dc_one, "count would go negative");
  Atomic::add((int)dc_mask, (volatile int*)&_dc_and_los);
}

inline HeapWord* ParallelCompactData::ChunkData::data_location() const
{
  DEBUG_ONLY(return _data_location;)
  NOT_DEBUG(return NULL;)
}

inline HeapWord* ParallelCompactData::ChunkData::highest_ref() const
{
  DEBUG_ONLY(return _highest_ref;)
  NOT_DEBUG(return NULL;)
}

inline void ParallelCompactData::ChunkData::set_data_location(HeapWord* addr)
{
  DEBUG_ONLY(_data_location = addr;)
}

inline void ParallelCompactData::ChunkData::set_completed()
{
  assert(claimed(), "must be claimed first");
  _dc_and_los = dc_completed | (chunk_sz_t) live_obj_size();
}

// MT-unsafe claiming of a chunk.  Should only be used during single threaded
// execution.
inline bool ParallelCompactData::ChunkData::claim_unsafe()
{
  if (available()) {
    _dc_and_los |= dc_claimed;
    return true;
  }
  return false;
}

inline void ParallelCompactData::ChunkData::add_live_obj(size_t words)
{
  assert(words <= (size_t)los_mask - live_obj_size(), "overflow");
  Atomic::add((int) words, (volatile int*) &_dc_and_los);
}

inline void ParallelCompactData::ChunkData::set_highest_ref(HeapWord* addr)
{
#ifdef ASSERT
  HeapWord* tmp = _highest_ref;
  while (addr > tmp) {
    tmp = (HeapWord*)Atomic::cmpxchg_ptr(addr, &_highest_ref, tmp);
  }
#endif  // #ifdef ASSERT
}

inline bool ParallelCompactData::ChunkData::claim()
{
  const int los = (int) live_obj_size();
  const int old = Atomic::cmpxchg(dc_claimed | los,
                                  (volatile int*) &_dc_and_los, los);
  return old == los;
}

inline ParallelCompactData::ChunkData*
ParallelCompactData::chunk(size_t chunk_idx) const
{
  assert(chunk_idx <= chunk_count(), "bad arg");
  return _chunk_data + chunk_idx;
}

inline size_t
ParallelCompactData::chunk(const ChunkData* const chunk_ptr) const
{
  assert(chunk_ptr >= _chunk_data, "bad arg");
  assert(chunk_ptr <= _chunk_data + chunk_count(), "bad arg");
  return pointer_delta(chunk_ptr, _chunk_data, sizeof(ChunkData));
}

inline ParallelCompactData::BlockData*
ParallelCompactData::block(size_t n) const {
  assert(n < block_count(), "bad arg");
  return _block_data + n;
}

inline size_t
ParallelCompactData::chunk_offset(const HeapWord* addr) const
{
  assert(addr >= _region_start, "bad addr");
  assert(addr <= _region_end, "bad addr");
  return (size_t(addr) & ChunkAddrOffsetMask) >> LogHeapWordSize;
}

inline size_t
ParallelCompactData::addr_to_chunk_idx(const HeapWord* addr) const
{
  assert(addr >= _region_start, "bad addr");
  assert(addr <= _region_end, "bad addr");
  return pointer_delta(addr, _region_start) >> Log2ChunkSize;
}

inline ParallelCompactData::ChunkData*
ParallelCompactData::addr_to_chunk_ptr(const HeapWord* addr) const
{
  return chunk(addr_to_chunk_idx(addr));
}

inline HeapWord*
ParallelCompactData::chunk_to_addr(size_t chunk) const
{
  assert(chunk <= _chunk_count, "chunk out of range");
  return _region_start + (chunk << Log2ChunkSize);
}

inline HeapWord*
ParallelCompactData::chunk_to_addr(const ChunkData* chunk) const
{
  return chunk_to_addr(pointer_delta(chunk, _chunk_data, sizeof(ChunkData)));
}

inline HeapWord*
ParallelCompactData::chunk_to_addr(size_t chunk, size_t offset) const
{
  assert(chunk <= _chunk_count, "chunk out of range");
  assert(offset < ChunkSize, "offset too big");  // This may be too strict.
  return chunk_to_addr(chunk) + offset;
}

inline HeapWord*
ParallelCompactData::chunk_align_down(HeapWord* addr) const
{
  assert(addr >= _region_start, "bad addr");
  assert(addr < _region_end + ChunkSize, "bad addr");
  return (HeapWord*)(size_t(addr) & ChunkAddrMask);
}

inline HeapWord*
ParallelCompactData::chunk_align_up(HeapWord* addr) const
{
  assert(addr >= _region_start, "bad addr");
  assert(addr <= _region_end, "bad addr");
  return chunk_align_down(addr + ChunkSizeOffsetMask);
}

inline bool
ParallelCompactData::is_chunk_aligned(HeapWord* addr) const
{
  return chunk_offset(addr) == 0;
}

inline size_t
ParallelCompactData::block_offset(const HeapWord* addr) const
{
  assert(addr >= _region_start, "bad addr");
  assert(addr <= _region_end, "bad addr");
  return pointer_delta(addr, _region_start) & BlockOffsetMask;
}

inline size_t
ParallelCompactData::addr_to_block_idx(const HeapWord* addr) const
{
  assert(addr >= _region_start, "bad addr");
  assert(addr <= _region_end, "bad addr");
  return pointer_delta(addr, _region_start) >> Log2BlockSize;
}

inline ParallelCompactData::BlockData*
ParallelCompactData::addr_to_block_ptr(const HeapWord* addr) const
{
  return block(addr_to_block_idx(addr));
}

inline HeapWord*
ParallelCompactData::block_to_addr(size_t block) const
{
  assert(block < _block_count, "block out of range");
  return _region_start + (block << Log2BlockSize);
}

// Abstract closure for use with ParMarkBitMap::iterate(), which will invoke the
// do_addr() method.
//
// The closure is initialized with the number of heap words to process
// (words_remaining()), and becomes 'full' when it reaches 0.  The do_addr()
// methods in subclasses should update the total as words are processed.  Since
// only one subclass actually uses this mechanism to terminate iteration, the
// default initial value is > 0.  The implementation is here and not in the
// single subclass that uses it to avoid making is_full() virtual, and thus
// adding a virtual call per live object.

class ParMarkBitMapClosure: public StackObj {
 public:
  typedef ParMarkBitMap::idx_t idx_t;
  typedef ParMarkBitMap::IterationStatus IterationStatus;

 public:
  inline ParMarkBitMapClosure(ParMarkBitMap* mbm, ParCompactionManager* cm,
                              size_t words = max_uintx);

  inline ParCompactionManager* compaction_manager() const;
  inline ParMarkBitMap*        bitmap() const;
  inline size_t                words_remaining() const;
  inline bool                  is_full() const;
  inline HeapWord*             source() const;

  inline void                  set_source(HeapWord* addr);

  virtual IterationStatus do_addr(HeapWord* addr, size_t words) = 0;

 protected:
  inline void decrement_words_remaining(size_t words);

 private:
  ParMarkBitMap* const        _bitmap;
  ParCompactionManager* const _compaction_manager;
  DEBUG_ONLY(const size_t     _initial_words_remaining;) // Useful in debugger.
  size_t                      _words_remaining; // Words left to copy.

 protected:
  HeapWord*                   _source;          // Next addr that would be read.
};

inline
ParMarkBitMapClosure::ParMarkBitMapClosure(ParMarkBitMap* bitmap,
                                           ParCompactionManager* cm,
                                           size_t words):
  _bitmap(bitmap), _compaction_manager(cm)
#ifdef  ASSERT
  , _initial_words_remaining(words)
#endif
{
  _words_remaining = words;
  _source = NULL;
}

inline ParCompactionManager* ParMarkBitMapClosure::compaction_manager() const {
  return _compaction_manager;
}

inline ParMarkBitMap* ParMarkBitMapClosure::bitmap() const {
  return _bitmap;
}

inline size_t ParMarkBitMapClosure::words_remaining() const {
  return _words_remaining;
}

inline bool ParMarkBitMapClosure::is_full() const {
  return words_remaining() == 0;
}

inline HeapWord* ParMarkBitMapClosure::source() const {
  return _source;
}

inline void ParMarkBitMapClosure::set_source(HeapWord* addr) {
  _source = addr;
}

inline void ParMarkBitMapClosure::decrement_words_remaining(size_t words) {
  assert(_words_remaining >= words, "processed too many words");
  _words_remaining -= words;
}

// Closure for updating the block data during the summary phase.
class BitBlockUpdateClosure: public ParMarkBitMapClosure {
  // ParallelCompactData::BlockData::blk_ofs_t _live_data_left;
  size_t    _live_data_left;
  size_t    _cur_block;
  HeapWord* _chunk_start;
  HeapWord* _chunk_end;
  size_t    _chunk_index;

 public:
  BitBlockUpdateClosure(ParMarkBitMap* mbm,
                        ParCompactionManager* cm,
                        size_t chunk_index);

  size_t cur_block() { return _cur_block; }
  size_t chunk_index() { return _chunk_index; }
  size_t live_data_left() { return _live_data_left; }
  // Returns true the first bit in the current block (cur_block) is
  // a start bit.
  // Returns true if the current block is within the chunk for the closure;
  bool chunk_contains_cur_block();

  // Set the chunk index and related chunk values for
  // a new chunk.
  void reset_chunk(size_t chunk_index);

  virtual IterationStatus do_addr(HeapWord* addr, size_t words);
};

class PSParallelCompact : AllStatic {
 public:
  // Convenient access to type names.
  typedef ParMarkBitMap::idx_t idx_t;
  typedef ParallelCompactData::ChunkData ChunkData;
  typedef ParallelCompactData::BlockData BlockData;

  typedef enum {
    perm_space_id, old_space_id, eden_space_id,
    from_space_id, to_space_id, last_space_id
  } SpaceId;

 public:
  // Inline closure decls
  //
  class IsAliveClosure: public BoolObjectClosure {
   public:
    virtual void do_object(oop p);
    virtual bool do_object_b(oop p);
  };

  class KeepAliveClosure: public OopClosure {
   private:
    ParCompactionManager* _compaction_manager;
   protected:
    template <class T> inline void do_oop_work(T* p);
   public:
    KeepAliveClosure(ParCompactionManager* cm) : _compaction_manager(cm) { }
    virtual void do_oop(oop* p);
    virtual void do_oop(narrowOop* p);
  };

  // Current unused
  class FollowRootClosure: public OopsInGenClosure {
   private:
    ParCompactionManager* _compaction_manager;
   public:
    FollowRootClosure(ParCompactionManager* cm) : _compaction_manager(cm) { }
    virtual void do_oop(oop* p);
    virtual void do_oop(narrowOop* p);
    virtual const bool do_nmethods() const { return true; }
  };

  class FollowStackClosure: public VoidClosure {
   private:
    ParCompactionManager* _compaction_manager;
   public:
    FollowStackClosure(ParCompactionManager* cm) : _compaction_manager(cm) { }
    virtual void do_void();
  };

  class AdjustPointerClosure: public OopsInGenClosure {
   private:
    bool _is_root;
   public:
    AdjustPointerClosure(bool is_root) : _is_root(is_root) { }
    virtual void do_oop(oop* p);
    virtual void do_oop(narrowOop* p);
  };

  // Closure for verifying update of pointers.  Does not
  // have any side effects.
  class VerifyUpdateClosure: public ParMarkBitMapClosure {
    const MutableSpace* _space; // Is this ever used?

   public:
    VerifyUpdateClosure(ParCompactionManager* cm, const MutableSpace* sp) :
      ParMarkBitMapClosure(PSParallelCompact::mark_bitmap(), cm), _space(sp)
    { }

    virtual IterationStatus do_addr(HeapWord* addr, size_t words);

    const MutableSpace* space() { return _space; }
  };

  // Closure for updating objects altered for debug checking
  class ResetObjectsClosure: public ParMarkBitMapClosure {
   public:
    ResetObjectsClosure(ParCompactionManager* cm):
      ParMarkBitMapClosure(PSParallelCompact::mark_bitmap(), cm)
    { }

    virtual IterationStatus do_addr(HeapWord* addr, size_t words);
  };

  friend class KeepAliveClosure;
  friend class FollowStackClosure;
  friend class AdjustPointerClosure;
  friend class FollowRootClosure;
  friend class instanceKlassKlass;
  friend class RefProcTaskProxy;

 private:
  static elapsedTimer         _accumulated_time;
  static unsigned int         _total_invocations;
  static unsigned int         _maximum_compaction_gc_num;
  static jlong                _time_of_last_gc;   // ms
  static CollectorCounters*   _counters;
  static ParMarkBitMap        _mark_bitmap;
  static ParallelCompactData  _summary_data;
  static IsAliveClosure       _is_alive_closure;
  static SpaceInfo            _space_info[last_space_id];
  static bool                 _print_phases;
  static AdjustPointerClosure _adjust_root_pointer_closure;
  static AdjustPointerClosure _adjust_pointer_closure;

  // Reference processing (used in ...follow_contents)
  static ReferenceProcessor*  _ref_processor;

  // Updated location of intArrayKlassObj.
  static klassOop _updated_int_array_klass_obj;

  // Values computed at initialization and used by dead_wood_limiter().
  static double _dwl_mean;
  static double _dwl_std_dev;
  static double _dwl_first_term;
  static double _dwl_adjustment;
#ifdef  ASSERT
  static bool   _dwl_initialized;
#endif  // #ifdef ASSERT

 private:
  // Closure accessors
  static OopClosure* adjust_pointer_closure()      { return (OopClosure*)&_adjust_pointer_closure; }
  static OopClosure* adjust_root_pointer_closure() { return (OopClosure*)&_adjust_root_pointer_closure; }
  static BoolObjectClosure* is_alive_closure()     { return (BoolObjectClosure*)&_is_alive_closure; }

  static void initialize_space_info();

  // Return true if details about individual phases should be printed.
  static inline bool print_phases();

  // Clear the marking bitmap and summary data that cover the specified space.
  static void clear_data_covering_space(SpaceId id);

  static void pre_compact(PreGCValues* pre_gc_values);
  static void post_compact();

  // Mark live objects
  static void marking_phase(ParCompactionManager* cm,
                            bool maximum_heap_compaction);
  static void follow_stack(ParCompactionManager* cm);
  static void follow_weak_klass_links(ParCompactionManager* cm);

  template <class T> static inline void adjust_pointer(T* p, bool is_root);
  static void adjust_root_pointer(oop* p) { adjust_pointer(p, true); }

  template <class T>
  static inline void follow_root(ParCompactionManager* cm, T* p);

  // Compute the dense prefix for the designated space.  This is an experimental
  // implementation currently not used in production.
  static HeapWord* compute_dense_prefix_via_density(const SpaceId id,
                                                    bool maximum_compaction);

  // Methods used to compute the dense prefix.

  // Compute the value of the normal distribution at x = density.  The mean and
  // standard deviation are values saved by initialize_dead_wood_limiter().
  static inline double normal_distribution(double density);

  // Initialize the static vars used by dead_wood_limiter().
  static void initialize_dead_wood_limiter();

  // Return the percentage of space that can be treated as "dead wood" (i.e.,
  // not reclaimed).
  static double dead_wood_limiter(double density, size_t min_percent);

  // Find the first (left-most) chunk in the range [beg, end) that has at least
  // dead_words of dead space to the left.  The argument beg must be the first
  // chunk in the space that is not completely live.
  static ChunkData* dead_wood_limit_chunk(const ChunkData* beg,
                                          const ChunkData* end,
                                          size_t dead_words);

  // Return a pointer to the first chunk in the range [beg, end) that is not
  // completely full.
  static ChunkData* first_dead_space_chunk(const ChunkData* beg,
                                           const ChunkData* end);

  // Return a value indicating the benefit or 'yield' if the compacted region
  // were to start (or equivalently if the dense prefix were to end) at the
  // candidate chunk.  Higher values are better.
  //
  // The value is based on the amount of space reclaimed vs. the costs of (a)
  // updating references in the dense prefix plus (b) copying objects and
  // updating references in the compacted region.
  static inline double reclaimed_ratio(const ChunkData* const candidate,
                                       HeapWord* const bottom,
                                       HeapWord* const top,
                                       HeapWord* const new_top);

  // Compute the dense prefix for the designated space.
  static HeapWord* compute_dense_prefix(const SpaceId id,
                                        bool maximum_compaction);

  // Return true if dead space crosses onto the specified Chunk; bit must be the
  // bit index corresponding to the first word of the Chunk.
  static inline bool dead_space_crosses_boundary(const ChunkData* chunk,
                                                 idx_t bit);

  // Summary phase utility routine to fill dead space (if any) at the dense
  // prefix boundary.  Should only be called if the the dense prefix is
  // non-empty.
  static void fill_dense_prefix_end(SpaceId id);

  static void summarize_spaces_quick();
  static void summarize_space(SpaceId id, bool maximum_compaction);
  static void summary_phase(ParCompactionManager* cm, bool maximum_compaction);

  static bool block_first_offset(size_t block_index, idx_t* block_offset_ptr);

  // Fill in the BlockData
  static void summarize_blocks(ParCompactionManager* cm,
                               SpaceId first_compaction_space_id);

  // The space that is compacted after space_id.
  static SpaceId next_compaction_space_id(SpaceId space_id);

  // Adjust addresses in roots.  Does not adjust addresses in heap.
  static void adjust_roots();

  // Serial code executed in preparation for the compaction phase.
  static void compact_prologue();

  // Move objects to new locations.
  static void compact_perm(ParCompactionManager* cm);
  static void compact();

  // Add available chunks to the stack and draining tasks to the task queue.
  static void enqueue_chunk_draining_tasks(GCTaskQueue* q,
                                           uint parallel_gc_threads);

  // Add dense prefix update tasks to the task queue.
  static void enqueue_dense_prefix_tasks(GCTaskQueue* q,
                                         uint parallel_gc_threads);

  // Add chunk stealing tasks to the task queue.
  static void enqueue_chunk_stealing_tasks(
                                       GCTaskQueue* q,
                                       ParallelTaskTerminator* terminator_ptr,
                                       uint parallel_gc_threads);

  // For debugging only - compacts the old gen serially
  static void compact_serial(ParCompactionManager* cm);

  // If objects are left in eden after a collection, try to move the boundary
  // and absorb them into the old gen.  Returns true if eden was emptied.
  static bool absorb_live_data_from_eden(PSAdaptiveSizePolicy* size_policy,
                                         PSYoungGen* young_gen,
                                         PSOldGen* old_gen);

  // Reset time since last full gc
  static void reset_millis_since_last_gc();

 protected:
#ifdef VALIDATE_MARK_SWEEP
  static GrowableArray<void*>*           _root_refs_stack;
  static GrowableArray<oop> *            _live_oops;
  static GrowableArray<oop> *            _live_oops_moved_to;
  static GrowableArray<size_t>*          _live_oops_size;
  static size_t                          _live_oops_index;
  static size_t                          _live_oops_index_at_perm;
  static GrowableArray<void*>*           _other_refs_stack;
  static GrowableArray<void*>*           _adjusted_pointers;
  static bool                            _pointer_tracking;
  static bool                            _root_tracking;

  // The following arrays are saved since the time of the last GC and
  // assist in tracking down problems where someone has done an errant
  // store into the heap, usually to an oop that wasn't properly
  // handleized across a GC. If we crash or otherwise fail before the
  // next GC, we can query these arrays to find out the object we had
  // intended to do the store to (assuming it is still alive) and the
  // offset within that object. Covered under RecordMarkSweepCompaction.
  static GrowableArray<HeapWord*> *      _cur_gc_live_oops;
  static GrowableArray<HeapWord*> *      _cur_gc_live_oops_moved_to;
  static GrowableArray<size_t>*          _cur_gc_live_oops_size;
  static GrowableArray<HeapWord*> *      _last_gc_live_oops;
  static GrowableArray<HeapWord*> *      _last_gc_live_oops_moved_to;
  static GrowableArray<size_t>*          _last_gc_live_oops_size;
#endif

 public:
  class MarkAndPushClosure: public OopClosure {
   private:
    ParCompactionManager* _compaction_manager;
   public:
    MarkAndPushClosure(ParCompactionManager* cm) : _compaction_manager(cm) { }
    virtual void do_oop(oop* p);
    virtual void do_oop(narrowOop* p);
    virtual const bool do_nmethods() const { return true; }
  };

  PSParallelCompact();

  // Convenient accessor for Universe::heap().
  static ParallelScavengeHeap* gc_heap() {
    return (ParallelScavengeHeap*)Universe::heap();
  }

  static void invoke(bool maximum_heap_compaction);
  static void invoke_no_policy(bool maximum_heap_compaction);

  static void post_initialize();
  // Perform initialization for PSParallelCompact that requires
  // allocations.  This should be called during the VM initialization
  // at a pointer where it would be appropriate to return a JNI_ENOMEM
  // in the event of a failure.
  static bool initialize();

  // Public accessors
  static elapsedTimer* accumulated_time() { return &_accumulated_time; }
  static unsigned int total_invocations() { return _total_invocations; }
  static CollectorCounters* counters()    { return _counters; }

  // Used to add tasks
  static GCTaskManager* const gc_task_manager();
  static klassOop updated_int_array_klass_obj() {
    return _updated_int_array_klass_obj;
  }

  // Marking support
  static inline bool mark_obj(oop obj);
  // Check mark and maybe push on marking stack
  template <class T> static inline void mark_and_push(ParCompactionManager* cm,
                                                      T* p);

  // Compaction support.
  // Return true if p is in the range [beg_addr, end_addr).
  static inline bool is_in(HeapWord* p, HeapWord* beg_addr, HeapWord* end_addr);
  static inline bool is_in(oop* p, HeapWord* beg_addr, HeapWord* end_addr);

  // Convenience wrappers for per-space data kept in _space_info.
  static inline MutableSpace*     space(SpaceId space_id);
  static inline HeapWord*         new_top(SpaceId space_id);
  static inline HeapWord*         dense_prefix(SpaceId space_id);
  static inline ObjectStartArray* start_array(SpaceId space_id);

  // Return true if the klass should be updated.
  static inline bool should_update_klass(klassOop k);

  // Move and update the live objects in the specified space.
  static void move_and_update(ParCompactionManager* cm, SpaceId space_id);

  // Process the end of the given chunk range in the dense prefix.
  // This includes saving any object not updated.
  static void dense_prefix_chunks_epilogue(ParCompactionManager* cm,
                                           size_t chunk_start_index,
                                           size_t chunk_end_index,
                                           idx_t exiting_object_offset,
                                           idx_t chunk_offset_start,
                                           idx_t chunk_offset_end);

  // Update a chunk in the dense prefix.  For each live object
  // in the chunk, update it's interior references.  For each
  // dead object, fill it with deadwood. Dead space at the end
  // of a chunk range will be filled to the start of the next
  // live object regardless of the chunk_index_end.  None of the
  // objects in the dense prefix move and dead space is dead
  // (holds only dead objects that don't need any processing), so
  // dead space can be filled in any order.
  static void update_and_deadwood_in_dense_prefix(ParCompactionManager* cm,
                                                  SpaceId space_id,
                                                  size_t chunk_index_start,
                                                  size_t chunk_index_end);

  // Return the address of the count + 1st live word in the range [beg, end).
  static HeapWord* skip_live_words(HeapWord* beg, HeapWord* end, size_t count);

  // Return the address of the word to be copied to dest_addr, which must be
  // aligned to a chunk boundary.
  static HeapWord* first_src_addr(HeapWord* const dest_addr,
                                  size_t src_chunk_idx);

  // Determine the next source chunk, set closure.source() to the start of the
  // new chunk return the chunk index.  Parameter end_addr is the address one
  // beyond the end of source range just processed.  If necessary, switch to a
  // new source space and set src_space_id (in-out parameter) and src_space_top
  // (out parameter) accordingly.
  static size_t next_src_chunk(MoveAndUpdateClosure& closure,
                               SpaceId& src_space_id,
                               HeapWord*& src_space_top,
                               HeapWord* end_addr);

  // Decrement the destination count for each non-empty source chunk in the
  // range [beg_chunk, chunk(chunk_align_up(end_addr))).
  static void decrement_destination_counts(ParCompactionManager* cm,
                                           size_t beg_chunk,
                                           HeapWord* end_addr);

  // Fill a chunk, copying objects from one or more source chunks.
  static void fill_chunk(ParCompactionManager* cm, size_t chunk_idx);
  static void fill_and_update_chunk(ParCompactionManager* cm, size_t chunk) {
    fill_chunk(cm, chunk);
  }

  // Update the deferred objects in the space.
  static void update_deferred_objects(ParCompactionManager* cm, SpaceId id);

  // Mark pointer and follow contents.
  template <class T>
  static inline void mark_and_follow(ParCompactionManager* cm, T* p);

  static ParMarkBitMap* mark_bitmap() { return &_mark_bitmap; }
  static ParallelCompactData& summary_data() { return _summary_data; }

  static inline void adjust_pointer(oop* p)       { adjust_pointer(p, false); }
  static inline void adjust_pointer(narrowOop* p) { adjust_pointer(p, false); }

  template <class T>
  static inline void adjust_pointer(T* p,
                                    HeapWord* beg_addr,
                                    HeapWord* end_addr);

  // Reference Processing
  static ReferenceProcessor* const ref_processor() { return _ref_processor; }

  // Return the SpaceId for the given address.
  static SpaceId space_id(HeapWord* addr);

  // Time since last full gc (in milliseconds).
  static jlong millis_since_last_gc();

#ifdef VALIDATE_MARK_SWEEP
  static void track_adjusted_pointer(void* p, bool isroot);
  static void check_adjust_pointer(void* p);
  static void track_interior_pointers(oop obj);
  static void check_interior_pointers();

  static void reset_live_oop_tracking(bool at_perm);
  static void register_live_oop(oop p, size_t size);
  static void validate_live_oop(oop p, size_t size);
  static void live_oop_moved_to(HeapWord* q, size_t size, HeapWord* compaction_top);
  static void compaction_complete();

  // Querying operation of RecordMarkSweepCompaction results.
  // Finds and prints the current base oop and offset for a word
  // within an oop that was live during the last GC. Helpful for
  // tracking down heap stomps.
  static void print_new_location_of_heap_address(HeapWord* q);
#endif  // #ifdef VALIDATE_MARK_SWEEP

  // Call backs for class unloading
  // Update subklass/sibling/implementor links at end of marking.
  static void revisit_weak_klass_link(ParCompactionManager* cm, Klass* k);

#ifndef PRODUCT
  // Debugging support.
  static const char* space_names[last_space_id];
  static void print_chunk_ranges();
  static void print_dense_prefix_stats(const char* const algorithm,
                                       const SpaceId id,
                                       const bool maximum_compaction,
                                       HeapWord* const addr);
#endif  // #ifndef PRODUCT

#ifdef  ASSERT
  // Verify that all the chunks have been emptied.
  static void verify_complete(SpaceId space_id);
#endif  // #ifdef ASSERT
};

inline bool PSParallelCompact::mark_obj(oop obj) {
  const int obj_size = obj->size();
  if (mark_bitmap()->mark_obj(obj, obj_size)) {
    _summary_data.add_obj(obj, obj_size);
    return true;
  } else {
    return false;
  }
}

template <class T>
inline void PSParallelCompact::follow_root(ParCompactionManager* cm, T* p) {
  assert(!Universe::heap()->is_in_reserved(p),
         "roots shouldn't be things within the heap");
#ifdef VALIDATE_MARK_SWEEP
  if (ValidateMarkSweep) {
    guarantee(!_root_refs_stack->contains(p), "should only be in here once");
    _root_refs_stack->push(p);
  }
#endif
  T heap_oop = oopDesc::load_heap_oop(p);
  if (!oopDesc::is_null(heap_oop)) {
    oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
    if (mark_bitmap()->is_unmarked(obj)) {
      if (mark_obj(obj)) {
        obj->follow_contents(cm);
      }
    }
  }
  follow_stack(cm);
}

template <class T>
inline void PSParallelCompact::mark_and_follow(ParCompactionManager* cm,
                                               T* p) {
  T heap_oop = oopDesc::load_heap_oop(p);
  if (!oopDesc::is_null(heap_oop)) {
    oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
    if (mark_bitmap()->is_unmarked(obj)) {
      if (mark_obj(obj)) {
        obj->follow_contents(cm);
      }
    }
  }
}

template <class T>
inline void PSParallelCompact::mark_and_push(ParCompactionManager* cm, T* p) {
  T heap_oop = oopDesc::load_heap_oop(p);
  if (!oopDesc::is_null(heap_oop)) {
    oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
    if (mark_bitmap()->is_unmarked(obj)) {
      if (mark_obj(obj)) {
        // This thread marked the object and owns the subsequent processing of it.
        cm->save_for_scanning(obj);
      }
    }
  }
}

template <class T>
inline void PSParallelCompact::adjust_pointer(T* p, bool isroot) {
  T heap_oop = oopDesc::load_heap_oop(p);
  if (!oopDesc::is_null(heap_oop)) {
    oop obj     = oopDesc::decode_heap_oop_not_null(heap_oop);
    oop new_obj = (oop)summary_data().calc_new_pointer(obj);
    assert(new_obj != NULL ||                     // is forwarding ptr?
           obj->is_shared(),                      // never forwarded?
           "should be forwarded");
    // Just always do the update unconditionally?
    if (new_obj != NULL) {
      assert(Universe::heap()->is_in_reserved(new_obj),
             "should be in object space");
      oopDesc::encode_store_heap_oop_not_null(p, new_obj);
    }
  }
  VALIDATE_MARK_SWEEP_ONLY(track_adjusted_pointer(p, isroot));
}

template <class T>
inline void PSParallelCompact::KeepAliveClosure::do_oop_work(T* p) {
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

inline bool PSParallelCompact::print_phases() {
  return _print_phases;
}

inline double PSParallelCompact::normal_distribution(double density) {
  assert(_dwl_initialized, "uninitialized");
  const double squared_term = (density - _dwl_mean) / _dwl_std_dev;
  return _dwl_first_term * exp(-0.5 * squared_term * squared_term);
}

inline bool
PSParallelCompact::dead_space_crosses_boundary(const ChunkData* chunk,
                                               idx_t bit)
{
  assert(bit > 0, "cannot call this for the first bit/chunk");
  assert(_summary_data.chunk_to_addr(chunk) == _mark_bitmap.bit_to_addr(bit),
         "sanity check");

  // Dead space crosses the boundary if (1) a partial object does not extend
  // onto the chunk, (2) an object does not start at the beginning of the chunk,
  // and (3) an object does not end at the end of the prior chunk.
  return chunk->partial_obj_size() == 0 &&
    !_mark_bitmap.is_obj_beg(bit) &&
    !_mark_bitmap.is_obj_end(bit - 1);
}

inline bool
PSParallelCompact::is_in(HeapWord* p, HeapWord* beg_addr, HeapWord* end_addr) {
  return p >= beg_addr && p < end_addr;
}

inline bool
PSParallelCompact::is_in(oop* p, HeapWord* beg_addr, HeapWord* end_addr) {
  return is_in((HeapWord*)p, beg_addr, end_addr);
}

inline MutableSpace* PSParallelCompact::space(SpaceId id) {
  assert(id < last_space_id, "id out of range");
  return _space_info[id].space();
}

inline HeapWord* PSParallelCompact::new_top(SpaceId id) {
  assert(id < last_space_id, "id out of range");
  return _space_info[id].new_top();
}

inline HeapWord* PSParallelCompact::dense_prefix(SpaceId id) {
  assert(id < last_space_id, "id out of range");
  return _space_info[id].dense_prefix();
}

inline ObjectStartArray* PSParallelCompact::start_array(SpaceId id) {
  assert(id < last_space_id, "id out of range");
  return _space_info[id].start_array();
}

inline bool PSParallelCompact::should_update_klass(klassOop k) {
  return ((HeapWord*) k) >= dense_prefix(perm_space_id);
}

template <class T>
inline void PSParallelCompact::adjust_pointer(T* p,
                                              HeapWord* beg_addr,
                                              HeapWord* end_addr) {
  if (is_in((HeapWord*)p, beg_addr, end_addr)) {
    adjust_pointer(p);
  }
}

class MoveAndUpdateClosure: public ParMarkBitMapClosure {
 public:
  inline MoveAndUpdateClosure(ParMarkBitMap* bitmap, ParCompactionManager* cm,
                              ObjectStartArray* start_array,
                              HeapWord* destination, size_t words);

  // Accessors.
  HeapWord* destination() const         { return _destination; }

  // If the object will fit (size <= words_remaining()), copy it to the current
  // destination, update the interior oops and the start array and return either
  // full (if the closure is full) or incomplete.  If the object will not fit,
  // return would_overflow.
  virtual IterationStatus do_addr(HeapWord* addr, size_t size);

  // Copy enough words to fill this closure, starting at source().  Interior
  // oops and the start array are not updated.  Return full.
  IterationStatus copy_until_full();

  // Copy enough words to fill this closure or to the end of an object,
  // whichever is smaller, starting at source().  Interior oops and the start
  // array are not updated.
  void copy_partial_obj();

 protected:
  // Update variables to indicate that word_count words were processed.
  inline void update_state(size_t word_count);

 protected:
  ObjectStartArray* const _start_array;
  HeapWord*               _destination;         // Next addr to be written.
};

inline
MoveAndUpdateClosure::MoveAndUpdateClosure(ParMarkBitMap* bitmap,
                                           ParCompactionManager* cm,
                                           ObjectStartArray* start_array,
                                           HeapWord* destination,
                                           size_t words) :
  ParMarkBitMapClosure(bitmap, cm, words), _start_array(start_array)
{
  _destination = destination;
}

inline void MoveAndUpdateClosure::update_state(size_t words)
{
  decrement_words_remaining(words);
  _source += words;
  _destination += words;
}

class UpdateOnlyClosure: public ParMarkBitMapClosure {
 private:
  const PSParallelCompact::SpaceId _space_id;
  ObjectStartArray* const          _start_array;

 public:
  UpdateOnlyClosure(ParMarkBitMap* mbm,
                    ParCompactionManager* cm,
                    PSParallelCompact::SpaceId space_id);

  // Update the object.
  virtual IterationStatus do_addr(HeapWord* addr, size_t words);

  inline void do_addr(HeapWord* addr);
};

inline void UpdateOnlyClosure::do_addr(HeapWord* addr)
{
  _start_array->allocate_block(addr);
  oop(addr)->update_contents(compaction_manager());
}

class FillClosure: public ParMarkBitMapClosure {
 public:
  FillClosure(ParCompactionManager* cm, PSParallelCompact::SpaceId space_id) :
    ParMarkBitMapClosure(PSParallelCompact::mark_bitmap(), cm),
    _space_id(space_id),
    _start_array(PSParallelCompact::start_array(space_id)) {
    assert(_space_id == PSParallelCompact::perm_space_id ||
           _space_id == PSParallelCompact::old_space_id,
           "cannot use FillClosure in the young gen");
    assert(bitmap() != NULL, "need a bitmap");
    assert(_start_array != NULL, "need a start array");
  }

  void fill_region(HeapWord* addr, size_t size) {
    MemRegion region(addr, size);
    SharedHeap::fill_region_with_object(region);
    _start_array->allocate_block(addr);
  }

  virtual IterationStatus do_addr(HeapWord* addr, size_t size) {
    fill_region(addr, size);
    return ParMarkBitMap::incomplete;
  }

private:
  const PSParallelCompact::SpaceId _space_id;
  ObjectStartArray* const          _start_array;
};
