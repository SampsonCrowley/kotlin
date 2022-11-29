// Copyright 2022 Google LLC

#include "LargePage.hpp"

#include <atomic>
#include <cstdint>

#include "CustomLogging.hpp"
#include "GCApi.hpp"
#include "MediumPage.hpp"

namespace kotlin::alloc {

LargePage* LargePage::Create(uint64_t cellCount) noexcept {
    CustomAllocInfo("LargePage::Create(%" PRIu64 ")", cellCount);
    RuntimeAssert(cellCount > LARGE_PAGE_SIZE_THRESHOLD,
            "blockSize too small for large page");
    uint64_t size = sizeof(LargePage) + cellCount * sizeof(uint64_t);
    return new (SafeAlloc(size)) LargePage();
}

uint64_t* LargePage::Data() noexcept {
    return reinterpret_cast<uint64_t*>(this + 1);
}

uint64_t* LargePage::TryAllocate() noexcept {
    if (isAllocated_) return nullptr;
    isAllocated_ = true;
    return Data();
}

bool LargePage::Sweep() noexcept {
    CustomAllocDebug("LargePage@%p::Sweep()", this);
    if (!TryResetMark(Data())) {
        isAllocated_ = false;
        return false;
    }
    return true;
}

}  // namespace kotlin::alloc
