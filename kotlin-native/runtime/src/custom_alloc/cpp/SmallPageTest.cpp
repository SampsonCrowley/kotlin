// Copyright 2022 Google LLC

#include <cstdint>

#include "gtest/gtest.h"
#include "SmallPage.hpp"
#include "TypeInfo.h"

namespace {

using SmallPage = typename kotlin::alloc::SmallPage;

TypeInfo fakeType = { .flags_ = 0 };  // a type without a finalizer

void mark(void* obj) {
    reinterpret_cast<uint64_t*>(obj)[0] = 1;
}

uint64_t* alloc(SmallPage* page, size_t blockSize) {
    uint64_t* ptr = reinterpret_cast<uint64_t*>(page->TryAllocate());
    if (ptr) {
        memset(ptr, 0, 8 * blockSize);
        ptr[1] = reinterpret_cast<uint64_t>(&fakeType);
    }
    return ptr;
}

TEST(CustomAllocTest, SmallPageConsequtiveAlloc) {
    for (uint32_t size = 2; size <= SMALL_PAGE_MAX_BLOCK_SIZE; ++size) {
        SmallPage* page = SmallPage::Create(size);
        uint64_t* prev = alloc(page, size);
        uint64_t* cur;
        while ((cur = alloc(page, size))) {
            EXPECT_EQ(prev + size, cur);
            prev = cur;
        }
        free(page);
    }
}

TEST(CustomAllocTest, SmallPageSweepEmptyPage) {
    for (uint32_t size = 2; size <= SMALL_PAGE_MAX_BLOCK_SIZE; ++size) {
        SmallPage* page = SmallPage::Create(size);
        EXPECT_FALSE(page->Sweep());
        free(page);
    }
}

TEST(CustomAllocTest, SmallPageSweepFullUnmarkedPage) {
    for (uint32_t size = 2; size <= SMALL_PAGE_MAX_BLOCK_SIZE; ++size) {
        SmallPage* page = SmallPage::Create(size);
        uint32_t count = 0;
        while (alloc(page, size)) ++count;
        EXPECT_EQ(count, SMALL_PAGE_CELL_COUNT / size);
        EXPECT_FALSE(page->Sweep());
        free(page);
    }
}

TEST(CustomAllocTest, SmallPageSweepSingleMarked) {
    for (uint32_t size = 2; size <= SMALL_PAGE_MAX_BLOCK_SIZE; ++size) {
        SmallPage* page = SmallPage::Create(size);
        uint64_t* ptr = alloc(page, size);
        mark(ptr);
        EXPECT_TRUE(page->Sweep());
        free(page);
    }
}

TEST(CustomAllocTest, SmallPageSweepSingleReuse) {
    for (uint32_t size = 2; size <= SMALL_PAGE_MAX_BLOCK_SIZE; ++size) {
        SmallPage* page = SmallPage::Create(size);
        uint64_t* ptr = alloc(page, size);
        EXPECT_FALSE(page->Sweep());
        EXPECT_EQ(alloc(page, size), ptr);
        free(page);
    }
}

TEST(CustomAllocTest, SmallPageSweepReuse) {
    for (uint32_t size = 2; size <= SMALL_PAGE_MAX_BLOCK_SIZE; ++size) {
        SmallPage* page = SmallPage::Create(size);
        uint64_t* ptr;
        for (int count = 0; (ptr = alloc(page, size)); ++count) {
            if (count % 2 == 0) mark(ptr);
        }
        EXPECT_TRUE(page->Sweep());
        uint32_t count = 0;
        for (; (ptr = alloc(page, size)); ++count) {
            if (count % 2 == 0) mark(ptr);
        }
        EXPECT_EQ(count, SMALL_PAGE_CELL_COUNT / size / 2);
        free(page);
    }
}
}  // namespace
