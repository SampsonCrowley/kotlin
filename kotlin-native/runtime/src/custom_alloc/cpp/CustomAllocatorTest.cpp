// Copyright 2022 Google LLC

#include <cstdint>
#include <random>

#include "CustomAllocator.hpp"
#include "gtest/gtest.h"
#include "Heap.hpp"
#include "SmallPage.hpp"
#include "TypeInfo.h"

namespace {

using Heap = typename kotlin::alloc::Heap;
using CustomAllocator = typename kotlin::alloc::CustomAllocator;

#define MIN_BLOCK_SIZE 2

TEST(CustomAllocTest, SmallAllocNonNull) {
    const int N = 200;
    TypeInfo fakeTypes[N];
    for (int i = 1 ; i < N ; ++i) {
        fakeTypes[i] = { .instanceSize_ = 8 * i , .flags_ = 0 };
    }
    Heap heap;
    CustomAllocator ca(heap);
    uint64_t* obj[N];
    for (int i = 1 ; i < N ; ++i) {
        TypeInfo* type = fakeTypes + i;
        obj[i] = reinterpret_cast<uint64_t*>(ca.CreateObject(type));
        EXPECT_TRUE(obj[i]);
    }
}

TEST(CustomAllocTest, SmallAllocSameSmallPage) {
    const int N = SMALL_PAGE_CELL_COUNT / SMALL_PAGE_MAX_BLOCK_SIZE;
    for (int blocks = MIN_BLOCK_SIZE ;
            blocks < SMALL_PAGE_MAX_BLOCK_SIZE ; ++blocks) {
        Heap heap;
        CustomAllocator ca(heap);
        TypeInfo fakeType = { .instanceSize_ = 8*blocks , .flags_ = 0 };
        void* obj = ca.CreateObject(&fakeType);
        uint64_t* first = reinterpret_cast<uint64_t*>(obj);
        for (int i = 1 ; i < N ; ++i) {
            obj = ca.CreateObject(&fakeType);
            uint64_t* cur = reinterpret_cast<uint64_t*>(obj);
            uint64_t dist = abs(cur - first);
            EXPECT_TRUE(dist < SMALL_PAGE_CELL_COUNT);
        }
    }
}

TEST(CustomAllocTest, TwoAllocatorsDifferentPages) {
    for (int blocks = MIN_BLOCK_SIZE ; blocks < 2000 ; ++blocks) {
        Heap heap;
        CustomAllocator ca1(heap);
        CustomAllocator ca2(heap);
        TypeInfo fakeType = { .instanceSize_ = 8*blocks , .flags_ = 0 };
        uint8_t* obj1 = reinterpret_cast<uint8_t*>(ca1.CreateObject(&fakeType));
        uint8_t* obj2 = reinterpret_cast<uint8_t*>(ca2.CreateObject(&fakeType));
        uint64_t dist = abs(obj2 - obj1);
        EXPECT_TRUE(dist >= SMALL_PAGE_SIZE);
    }
}

#undef MIN_BLOCK_SIZE
}  // namespace
