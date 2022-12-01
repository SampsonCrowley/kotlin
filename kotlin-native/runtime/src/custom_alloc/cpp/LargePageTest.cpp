// Copyright 2022 Google LLC

#include <cstdint>
#include <random>

#include "CustomAllocConstants.hpp"
#include "gtest/gtest.h"
#include "LargePage.hpp"
#include "TypeInfo.h"

namespace {

using LargePage = typename kotlin::alloc::LargePage;

TypeInfo fakeType = {.flags_ = 0}; // a type without a finalizer

#define MIN_BLOCK_SIZE MEDIUM_PAGE_CELL_COUNT

void mark(void* obj) {
    reinterpret_cast<uint64_t*>(obj)[0] = 1;
}

LargePage* alloc(uint64_t blockSize) {
    LargePage* page = LargePage::Create(blockSize);
    uint64_t* ptr = reinterpret_cast<uint64_t*>(page->TryAllocate());
    memset(ptr, 0, 8 * blockSize);
    ptr[1] = reinterpret_cast<uint64_t>(&fakeType);
    return page;
}

TEST(CustomAllocTest, LargePageSweepEmptyPage) {
    LargePage* page = alloc(MIN_BLOCK_SIZE);
    EXPECT_TRUE(page);
    EXPECT_FALSE(page->Sweep());
    free(page);
}

TEST(CustomAllocTest, LargePageSweepFullPage) {
    LargePage* page = alloc(MIN_BLOCK_SIZE);
    EXPECT_TRUE(page);
    EXPECT_TRUE(page->Data());
    mark(page->Data());
    EXPECT_TRUE(page->Sweep());
    free(page);
}

#undef MIN_BLOCK_SIZE
} // namespace
