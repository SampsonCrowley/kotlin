// Copyright 2022 Google LLC

#ifndef CUSTOM_ALLOC_CPP_LARGEPAGE_HPP_
#define CUSTOM_ALLOC_CPP_LARGEPAGE_HPP_

#include <atomic>
#include <cstdint>

#include "AtomicStack.hpp"

namespace kotlin::alloc {

class alignas(8) LargePage {
public:
    static LargePage* Create(uint64_t cellCount) noexcept;

    void Destroy() noexcept;

    uint8_t* TryAllocate() noexcept;

    uint8_t* Data() noexcept;

    bool Sweep() noexcept;

private:
    friend class AtomicStack<LargePage>;
    LargePage* next_;
    bool isAllocated_ = false;
};

} // namespace kotlin::alloc

#endif
