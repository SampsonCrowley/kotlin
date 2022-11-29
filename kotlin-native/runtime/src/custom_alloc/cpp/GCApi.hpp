// Copyright 2022 Google LLC

#ifndef CUSTOM_ALLOC_CPP_GCAPI_HPP_
#define CUSTOM_ALLOC_CPP_GCAPI_HPP_

#include <cstdint>
#include <inttypes.h>
#include <limits>
#include <stdlib.h>

namespace kotlin::alloc {

bool TryResetMark(void* ptr) noexcept;

void* SafeAlloc(uint64_t size) noexcept;

}  // namespace kotlin::alloc

#endif
