// Copyright 2022 Google LLC

#ifndef CUSTOM_ALLOC_CPP_ALLOCATOR_HPP_
#define CUSTOM_ALLOC_CPP_ALLOCATOR_HPP_

#include <atomic>
#include <cstring>

#include "Cell.hpp"
#include "CustomLogging.hpp"
#include "Heap.hpp"
#include "MediumPage.hpp"
#include "Memory.h"
#include "SmallPage.hpp"

namespace kotlin {
namespace alloc {

class CustomAllocator {

// # CustomAllocator
//
// The primary responsibility of this class is to delegate each requested
// allocation to pages of the appropriate type, based on allocation size. To do
// this, it requests pages from the shared allocation space (Heap) and stores
// pages for later allocations. Each thread thus owns a number of pages for
// different allocation sizes, but at most one for each size class. When
// allocating, the CustomAllocator will first try to allocate in one of its
// owned pages. If this fails, it will request a new page for that size class
// from a shared Heap object.

// # Heap
//
// A Heap object represents a shared allocation space for multiple
// CustomAllocators, which can request pages through one of the GetSmallPage,
// GetMediumPage, GetLargePage methods. It also provides methods for sweeping
// through all blocks that have been allocated in this heap. The Heap object
// is the synchronization point, and guarantees that every page is returned at
// most once. Page ownership is thus implicitly given to the thread that called
// the method. The Heap object keeps track of all pages, so there is no need to
// explicitly return ownership of a page.

// # PageStore
//
// The Heap keeps the pages for each size class in a PageStore. A PageStore has
// three stacks of pages. The stack, that a given page is in, determines its
// current state:
//  * unswept_: have not yet been swept since last GC cycle.
//  * ready_:   are ready for allocation.
//  * used_:    has been given to some thread for allocation; it might still be
//              used for allocation, or it might have been discarded with not
//              enough space left. Will not be used until next GC cycle.
// When a page is requested, the page is taken from ready_, if there are any.
// Otherwise, an unswept_ page is taken and swept before returning. If there
// are no unswept pages either, a new page is created in the size category. All
// returned pages are added to full_. During the marking phase, all pages are
// moved to unswept_. The GC thread will go through all PageStores and sweep
// the pages in unswept_ and move them to ready_. If one of the other threads
// sweeps a page from unswept_, it is moved directly to used_, as it is claimed
// by the CustomAllocator that swept it.

// # AtomicStack
//
// The only place where atomics are used are in the stacks inside the
// PageStore. All page classes have a non-atomic next pointer, to be used for
// linking up in exactly one stack.

// # SmallPage
//
// All sufficiently small allocations (<1KiB) are directed to a SmallPage,
// where all blocks have the same fixed size. Most allocations are expected to
// be in this page type. A SmallPage has a singly-linked free-list of all free
// blocks. The important point is that all links in the list point forward in
// the page, so all blocks between two consecutive links are implicitly
// allocated. Sweeping a SmallPage consists of walking the free-list forward,
// and sweeping all blocks in between the links, maintaining the free list
// when blocks are freed.

// # MediumPage
//
// Allocations that are too big for a SmallPage, but not big enough to get a
// page for themselves, end up in a MediumPage. All blocks in a MediumPage have
// a header that tells how big the block is, and whether it is allocated or
// not. There are no gaps between blocks, so the size of a block also tells
// where the next block is.

// # LargePage
//
// Allocations too big for a medium page are allocated in a LargePage, which
// only contains that single allocation. They are also handled slightly
// differently by both Heap and CustomAllocator. First off, Heap::GetLargePage
// will never check existing pages, and instead just allocate a new page.
// Secondly, a CustomAllocator does not keep a reference to any of the
// LargePages. As a consequence, they are only swept by the GC thread.

public:
    explicit CustomAllocator(Heap& heap) noexcept : heap_(heap), mediumPage_(nullptr) {
        CustomAllocInfo("CustomAllocator::CustomAllocator(heap)");
        memset(smallPages_, 0, sizeof(smallPages_));
    }

    ObjHeader* CreateObject(const TypeInfo* typeInfo) noexcept;

    ArrayHeader* CreateArray(const TypeInfo* typeInfo, uint32_t count) noexcept;

    void PrepareForGC() noexcept {
        CustomAllocInfo("CustomAllocator@%p::PrepareForGC()", this);
        mediumPage_ = nullptr;
        memset(smallPages_, 0, sizeof(smallPages_));
    }

    static void Free(void* ptr) noexcept {
        CustomAllocWarning("CustomAllocator::Free not supported");
    }

private:
    // Allocates a block of `size` bytes. This is the only method in this
    // module that measures size in bytes rather than number of Cells.  Returns
    // null only in the case that malloc returns null.
    void* Alloc(uint64_t size) noexcept {
        CustomAllocDebug("CustomAllocator@%p::Alloc(%" PRIu64 ")", this, size);
        uint64_t cellCount = (size + sizeof(Cell) - 1) / sizeof(Cell);
        void* ptr = Allocate(cellCount);
        memset(ptr, 0, size);
        return ptr;
    }

    void* Allocate(uint64_t cellCount) noexcept;
    void* AllocateInLargePage(uint64_t cellCount) noexcept;
    void* AllocateInMediumPage(uint32_t cellCount) noexcept;
    void* AllocateInSmallPage(uint32_t cellCount) noexcept;

    Heap& heap_;
    MediumPage* mediumPage_;
    SmallPage* smallPages_[SMALL_PAGE_MAX_BLOCK_SIZE+1];
};

}  // namespace alloc
}  // namespace kotlin

#endif
