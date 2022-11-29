// Copyright 2022 Google LLC

#ifndef CUSTOM_ALLOC_CPP_ALLOCATOR_HPP_
#define CUSTOM_ALLOC_CPP_ALLOCATOR_HPP_

#include <atomic>
#include <cstring>

#include "GCScheduler.hpp"
#include "Heap.hpp"
#include "MediumPage.hpp"
#include "Memory.h"
#include "SmallPage.hpp"

namespace kotlin::alloc {

class CustomAllocator {
public:
    explicit CustomAllocator(Heap& heap, gc::GCSchedulerThreadData& gcScheduler) noexcept;

    ObjHeader* CreateObject(const TypeInfo* typeInfo) noexcept;

    ArrayHeader* CreateArray(const TypeInfo* typeInfo, uint32_t count) noexcept;

    void PrepareForGC() noexcept;

private:
    void* Allocate(uint64_t cellCount) noexcept;
    void* AllocateInLargePage(uint64_t cellCount) noexcept;
    void* AllocateInMediumPage(uint32_t cellCount) noexcept;
    void* AllocateInSmallPage(uint32_t cellCount) noexcept;

    Heap& heap_;
    gc::GCSchedulerThreadData& gcScheduler_;
    MediumPage* mediumPage_;
    SmallPage* smallPages_[SMALL_PAGE_MAX_BLOCK_SIZE+1];
};

}  // namespace kotlin::alloc

#endif
