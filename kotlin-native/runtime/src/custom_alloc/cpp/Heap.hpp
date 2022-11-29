// Copyright 2022 Google LLC

#ifndef CUSTOM_ALLOC_CPP_HEAP_HPP_
#define CUSTOM_ALLOC_CPP_HEAP_HPP_

#include <atomic>
#include <cstring>

#include "LargePage.hpp"
#include "MediumPage.hpp"
#include "PageStore.hpp"
#include "SmallPage.hpp"

namespace kotlin::alloc {

class Heap {
public:
    // Called once by the GC thread after all mutators have been suspended
    void PrepareForGC() noexcept;

    // Sweep through all remaining pages, freeing those blocks where CanReclaim
    // returns true. If multiple sweepers are active, each page will only be
    // seen by one sweeper.
    void Sweep() noexcept;

    SmallPage* GetSmallPage(uint32_t cellCount) noexcept;
    MediumPage* GetMediumPage(uint32_t cellCount) noexcept;
    LargePage* GetLargePage(uint64_t cellCount) noexcept;

private:
    PageStore<SmallPage> smallPages_[SMALL_PAGE_MAX_BLOCK_SIZE+1];
    PageStore<MediumPage> mediumPages_;
    PageStore<LargePage> largePages_;
};

}  // namespace kotlin::alloc

#endif
