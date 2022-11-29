// Copyright 2022 Google LLC

#ifndef CUSTOM_ALLOC_CPP_LARGEPAGE_HPP_
#define CUSTOM_ALLOC_CPP_LARGEPAGE_HPP_

#include <atomic>
#include <cstdint>

#include "AtomicStack.hpp"
#include "MediumPage.hpp"

namespace kotlin::alloc {

#define LARGE_PAGE_SIZE_THRESHOLD (MEDIUM_PAGE_CELL_COUNT-1)

class alignas(8) LargePage {
public:
    static LargePage* Create(uint64_t cellCount) noexcept;

    uint64_t* TryAllocate() noexcept;

    uint64_t* Data() noexcept;

    bool Sweep() noexcept;

private:
    friend class AtomicStack<LargePage>;
    LargePage* next_;
    bool isAllocated_ = false;
};

}  // namespace kotlin::alloc

#endif
