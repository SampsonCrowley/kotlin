// Copyright 2022 Google LLC

#include "SmallPage.hpp"

#include <atomic>
#include <cstring>
#include <random>

#include "CustomLogging.hpp"
#include "GCApi.hpp"

namespace kotlin::alloc {

SmallPage* SmallPage::Create(uint32_t blockSize) noexcept {
    CustomAllocInfo("SmallPage::Create(%u)", blockSize);
    RuntimeAssert(blockSize <= SMALL_PAGE_MAX_BLOCK_SIZE, "blockSize too large for small page");
    return new (SafeAlloc(SMALL_PAGE_SIZE)) SmallPage(blockSize);
}

SmallPage::SmallPage(uint32_t blockSize) noexcept : blockSize_(blockSize) {
    CustomAllocInfo("SmallPage(%p)::SmallPage(%u)", this, blockSize);
    nextFree_ = cells_;
    SmallCell* end = cells_ + (SMALL_PAGE_CELL_COUNT + 1 - blockSize_);
    for (SmallCell* cell = cells_; cell < end; cell = cell->nextFree) {
        cell->nextFree = cell + blockSize;
    }
}

SmallCell* SmallPage::TryAllocate() noexcept {
    if (nextFree_ + blockSize_ > cells_ + SMALL_PAGE_CELL_COUNT) {
        return nullptr;
    }
    SmallCell* freeBlock = nextFree_;
    nextFree_ = freeBlock->nextFree;
    CustomAllocDebug("SmallPage(%p){%u}::TryAllocate() = %p", this, blockSize_, freeBlock);
    return freeBlock;
}

bool SmallPage::Sweep() noexcept {
    CustomAllocInfo("SmallPage(%p)::Sweep()", this);
    // `end` is after the last legal allocation of a block, but does not
    // necessarily match an actual block starting point.
    SmallCell* end = cells_ + (SMALL_PAGE_CELL_COUNT + 1 - blockSize_);
    bool alive = false;
    SmallCell* block = cells_;
    SmallCell** nextFree = &nextFree_;
    while (block < end) {
        while (block != *nextFree) {
            SmallCell* cell = block;
            if (!TryResetMark(block)) {
                cell->nextFree = *nextFree;
                *nextFree = block;
                nextFree = &cell->nextFree;
            } else {
                alive = true;
            }
            block += blockSize_;
        }
        if (block >= end) break;
        nextFree = &block->nextFree;
        block += blockSize_;
    }
    return alive;
}

}  // namespace kotlin::alloc
