// Copyright 2022 Google LLC

#ifndef CUSTOM_ALLOC_CPP_SMALLPAGE_HPP_
#define CUSTOM_ALLOC_CPP_SMALLPAGE_HPP_

#include <atomic>
#include <cstdint>

#include "AtomicStack.hpp"

namespace kotlin::alloc {

#define KiB 1024
#define SMALL_PAGE_SIZE (256*KiB)
#define SMALL_PAGE_MAX_BLOCK_SIZE 128
#define SMALL_PAGE_CELL_COUNT ((SMALL_PAGE_SIZE-sizeof(kotlin::alloc::SmallPage))/sizeof(kotlin::alloc::SmallCell))

struct alignas(8) SmallCell {
    uint64_t* Data() { return reinterpret_cast<uint64_t*>(this); }

    SmallCell* nextFree;
};

class alignas(8) SmallPage {
public:
    static SmallPage* Create(uint32_t blockSize) noexcept;

    // Tries to allocate in current page, returns null if no free block in page
    SmallCell* TryAllocate() noexcept;

    bool Sweep() noexcept;

private:
    friend class AtomicStack<SmallPage>;

    explicit SmallPage(uint32_t blockSize) noexcept;

    // Used for linking pages together in `pages` queue or in `unswept` queue.
    SmallPage* next_;
    uint32_t blockSize_;
    SmallCell* nextFree_;
    SmallCell cells_[];
};

static_assert(sizeof(SmallPage) % 8 == 0, "Page header size is not aligned");

}  // namespace kotlin::alloc

#endif
