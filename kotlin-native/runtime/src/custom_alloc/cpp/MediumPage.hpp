// Copyright 2022 Google LLC

#ifndef CUSTOM_ALLOC_CPP_MEDIUMPAGE_HPP_
#define CUSTOM_ALLOC_CPP_MEDIUMPAGE_HPP_

#include <atomic>

#include "AtomicStack.hpp"
#include "Cell.hpp"
#include "CustomLogging.hpp"
#include "GCApi.hpp"
#include "KAssert.h"

namespace kotlin {
namespace alloc {

#define KiB 1024
#define MEDIUM_PAGE_SIZE (256*KiB)
#define MEDIUM_PAGE_CELL_COUNT ((MEDIUM_PAGE_SIZE - sizeof(kotlin::alloc::MediumPage)) / sizeof(kotlin::alloc::Cell))

class alignas(8) MediumPage {
public:
    class Iterator {
        public:
            Cell& operator*() noexcept { return *cell_; }
            Cell* operator->() noexcept { return cell_; }

            Iterator& operator++() noexcept {
                cell_ = cell_->Next();
                return *this;
            }

            bool operator==(const Iterator& rhs) const noexcept { return cell_ == rhs.cell_; }
            bool operator!=(const Iterator& rhs) const noexcept { return cell_ != rhs.cell_; }

        private:
            friend class MediumPage;
            explicit Iterator(Cell* cell) noexcept : cell_(cell) {}

            Cell* cell_;
    };

    Iterator begin() noexcept { return Iterator(cells_); }
    Iterator end() noexcept { return Iterator(cells_ + MEDIUM_PAGE_CELL_COUNT); }

    static MediumPage* Create(uint32_t cellCount) noexcept {
        CustomAllocInfo("MediumPage::Create(%u)", cellCount);
        RuntimeAssert(cellCount < MEDIUM_PAGE_CELL_COUNT, "cellCount is too large for medium page");
        return new (alloc(MEDIUM_PAGE_SIZE)) MediumPage(cellCount);
    }

    // Tries to allocate in current page, returns null if no free block in page is big enough
    Cell* TryAllocate(uint32_t blockSize) noexcept;

    bool Sweep() noexcept;

    bool CheckInvariants() noexcept {
        if (curBlock_ < &kZeroBlock_ || curBlock_ >= cells_ + MEDIUM_PAGE_CELL_COUNT) return false;
        for (Cell* cur = cells_;; cur = cur->Next()) {
            if (cur->Next() <= cur) return false;
            if (cur->Next() > cells_ + MEDIUM_PAGE_CELL_COUNT) return false;
            if (cur->Next() == cells_ + MEDIUM_PAGE_CELL_COUNT) return true;
        }
    }

private:
    MediumPage(uint32_t cellCount) noexcept : curBlock_(cells_), kZeroBlock_(0) {
        cells_[0] = Cell(MEDIUM_PAGE_CELL_COUNT);
    }

    // Coalesces adjecent unallocated blocks and sets cur_block to the largest one.
    void UpdateCurBlock(uint32_t cellsNeeded) noexcept;

    friend class AtomicStack<MediumPage>;
    MediumPage* next_;

    Cell* curBlock_;
    Cell kZeroBlock_;  // simplifies code to have a dummy empty cell in the same address neighborhood
    Cell cells_[];
};
}  // namespace alloc
}  // namespace kotlin

#endif
