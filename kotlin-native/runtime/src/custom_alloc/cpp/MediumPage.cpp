// Copyright 2022 Google LLC

#include "MediumPage.hpp"

#include <atomic>
#include <cstdint>

#include "CustomLogging.hpp"
#include "GCApi.hpp"

namespace kotlin {
namespace alloc {

Cell* MediumPage::TryAllocate(uint32_t blockSize) noexcept {
    CustomAllocDebug("MediumPage@%p::TryAllocate(%u)", this, blockSize);
    // +1 accounts for header, since cell->size also includes header cell
    uint32_t cellsNeeded = blockSize + 1;
    Cell* block = curBlock_->TryAllocate(cellsNeeded);
    if (block) return block;
    UpdateCurBlock(cellsNeeded);
    return curBlock_->TryAllocate(cellsNeeded);
}

bool MediumPage::Sweep() noexcept {
    CustomAllocDebug("MediumPage@%p::Sweep()", this);
    Cell* end = cells_ + MEDIUM_PAGE_CELL_COUNT;
    bool alive = false;
    for (Cell& block : *this) {
        if (block.isAllocated_) {
            if (TryResetMark(block.Data())) {
                alive = true;
            } else {
                block.Deallocate();
            }
        }
    }
    Cell* maxBlock = &kZeroBlock_;
    for (Cell& block : *this) {
        if (block.isAllocated_) continue;
        while (block.Next() != end && !block.Next()->isAllocated_) {
            block.size_ += block.Next()->size_;
        }
        if (block.size_ > maxBlock->size_) maxBlock = &block;
    }
    curBlock_ = maxBlock;
    return alive;
}

void MediumPage::UpdateCurBlock(uint32_t cellsNeeded) noexcept {
    CustomAllocDebug("MediumPage@%p::UpdateCurBlock(%u)", this, cellsNeeded);
    if (curBlock_ == &kZeroBlock_) curBlock_ = cells_;
    Cell* maxBlock = &kZeroBlock_;
    for (Cell& block : *this) {
        if (!block.isAllocated_ && block.size_ > maxBlock->size_) {
            maxBlock = &block;
            if (block.size_ >= cellsNeeded) {
                curBlock_ = maxBlock;
                return;
            }
        }
    }
    CustomAllocDebug("MediumPage@%p::UpdateCurBlock: starting from beginning", this);
    for (Cell& block : *this) {
        if (!block.isAllocated_ && block.size_ > maxBlock->size_) {
            maxBlock = &block;
            if (block.size_ >= cellsNeeded) {
                curBlock_ = maxBlock;
                return;
            }
        }
    }
    curBlock_ = maxBlock;
}

}  // namespace alloc
}  // namespace kotlin
