// Copyright 2022 Google LLC

#include "Heap.hpp"

#include <atomic>
#include <cstdlib>
#include <cinttypes>
#include <new>

#include "CustomAllocator.hpp"
#include "CustomLogging.hpp"
#include "LargePage.hpp"
#include "MediumPage.hpp"
#include "SmallPage.hpp"
#include "ThreadRegistry.hpp"
#include "GCImpl.hpp"

namespace kotlin {
namespace alloc {

void Heap::PrepareForGC() noexcept {
    CustomAllocDebug("Heap::PrepareForGC()");
    for (auto& thread : kotlin::mm::ThreadRegistry::Instance().LockForIter()) {
        thread.gc().impl().alloc().PrepareForGC();
    }

    mediumPages_.PrepareForGC();
    largePages_.PrepareForGC();
    for (int blockSize = 0 ; blockSize <= SMALL_PAGE_MAX_BLOCK_SIZE ; ++blockSize) {
        smallPages_[blockSize].PrepareForGC();
    }
}

void Heap::Sweep() noexcept {
    CustomAllocDebug("Heap::Sweep()");
    for (int blockSize = 0 ; blockSize <= SMALL_PAGE_MAX_BLOCK_SIZE ; ++blockSize) {
        smallPages_[blockSize].Sweep();
    }
    mediumPages_.Sweep();
    largePages_.Sweep();
}

MediumPage* Heap::GetMediumPage(uint32_t cellCount) noexcept {
    CustomAllocDebug("Heap::GetMediumPage()");
    return mediumPages_.GetPage(cellCount);
}

SmallPage* Heap::GetSmallPage(uint32_t cellCount) noexcept {
    CustomAllocDebug("Heap::GetSmallPage()");
    return smallPages_[cellCount].GetPage(cellCount);
}

LargePage* Heap::GetLargePage(uint64_t cellCount) noexcept {
    CustomAllocInfo("CustomAllocator::AllocateInLargePage(%" PRIu64 ")", cellCount);
    return largePages_.NewPage(cellCount);
}
} // namespace alloc
} // namespace kotlin
