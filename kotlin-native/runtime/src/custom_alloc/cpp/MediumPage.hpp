// Copyright 2022 Google LLC

#ifndef CUSTOM_ALLOC_CPP_MEDIUMPAGE_HPP_
#define CUSTOM_ALLOC_CPP_MEDIUMPAGE_HPP_

#include <atomic>
#include <cstdint>

#include "AtomicStack.hpp"
#include "Cell.hpp"
#include "CustomLogging.hpp"
#include "KAssert.h"

namespace kotlin::alloc {

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

    static MediumPage* Create(uint32_t cellCount) noexcept;

    // Tries to allocate in current page, returns null if no free block in page is big enough
    uint64_t* TryAllocate(uint32_t blockSize) noexcept;

    bool Sweep() noexcept;

    // Testing method
    bool CheckInvariants() noexcept;

private:
    MediumPage(uint32_t cellCount) noexcept;
    
    // Looks for a block big enough to hold cellsNeeded. If none big enough is
    // found, update to the largest one.
    void UpdateCurBlock(uint32_t cellsNeeded) noexcept;

    friend class AtomicStack<MediumPage>;
    MediumPage* next_;

    Cell* curBlock_;
    Cell kZeroBlock_;  // simplifies code to have a dummy empty cell in the same address neighborhood
    Cell cells_[];
};
}  // namespace kotlin::alloc

#endif
