// Copyright 2022 Google LLC

#include "LargePage.hpp"

#include <atomic>
#include <cstdint>

#include "CustomLogging.hpp"
#include "CustomAllocConstants.hpp"
#include "GCApi.hpp"

namespace kotlin::alloc {

LargePage* LargePage::Create(uint64_t cellCount) noexcept {
    CustomAllocInfo("LargePage::Create(%" PRIu64 ")", cellCount);
    RuntimeAssert(cellCount > LARGE_PAGE_SIZE_THRESHOLD, "blockSize too small for large page");
    uint64_t size = sizeof(LargePage) + cellCount * sizeof(uint64_t);
    return new (SafeAlloc(size)) LargePage();
}

void LargePage::Destroy() noexcept {
    std_support::free(this);
}

uint8_t* LargePage::Data() noexcept {
    return reinterpret_cast<uint8_t*>(this + 1);
}

uint8_t* LargePage::TryAllocate() noexcept {
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

} // namespace kotlin::alloc
