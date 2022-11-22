// Copyright 2022 Google LLC

#include "CustomAllocator.hpp"

#include <atomic>
#include <cstdlib>
#include <cinttypes>
#include <new>

#include "ConcurrentMarkAndSweep.hpp"
#include "CustomLogging.hpp"
#include "LargePage.hpp"
#include "MediumPage.hpp"
#include "SmallPage.hpp"

namespace kotlin {
namespace alloc {

using ObjectData = gc::ConcurrentMarkAndSweep::ObjectData;

struct HeapObjHeader {
    ObjectData gcData;
    alignas(kObjectAlignment) ObjHeader object;
};

// Needs to be kept compatible with `HeapObjHeader` just like `ArrayHeader` is compatible
// with `ObjHeader`: the former can always be casted to the other.
struct HeapArrayHeader {
    ObjectData gcData;
    alignas(kObjectAlignment) ArrayHeader array;
};

size_t ObjectAllocatedDataSize(const TypeInfo* typeInfo) noexcept {
    size_t membersSize = typeInfo->instanceSize_ - sizeof(ObjHeader);
    return AlignUp(sizeof(HeapObjHeader) + membersSize, kObjectAlignment);
}

uint64_t ArrayAllocatedDataSize(const TypeInfo* typeInfo, uint32_t count) noexcept {
    // -(int32_t min) * uint32_t max cannot overflow uint64_t. And are capped
    // at about half of uint64_t max.
    uint64_t membersSize = static_cast<uint64_t>(-typeInfo->instanceSize_) * count;
    // Note: array body is aligned, but for size computation it is enough to align the sum.
    return AlignUp<uint64_t>(sizeof(HeapArrayHeader) + membersSize, kObjectAlignment);
}

ObjHeader* CustomAllocator::CreateObject(const TypeInfo* typeInfo) noexcept {
    RuntimeAssert(!typeInfo->IsArray(), "Must not be an array");
    size_t allocSize = ObjectAllocatedDataSize(typeInfo);
    auto* heapObject = new (Alloc(allocSize)) HeapObjHeader();
    auto* object = &heapObject->object;
    object->typeInfoOrMeta_ = const_cast<TypeInfo*>(typeInfo);
    return object;
}

ArrayHeader* CustomAllocator::CreateArray(const TypeInfo* typeInfo, uint32_t count) noexcept {
    RuntimeAssert(typeInfo->IsArray(), "Must be an array");
    auto allocSize = ArrayAllocatedDataSize(typeInfo, count);
    auto* heapArray = new (Alloc(allocSize)) HeapArrayHeader();
    auto* array = &heapArray->array;
    array->typeInfoOrMeta_ = const_cast<TypeInfo*>(typeInfo);
    array->count_ = count;
    return array;
}

void* CustomAllocator::Allocate(uint64_t cellCount) noexcept {
    CustomAllocDebug("CustomAllocator::Allocate(%" PRIu64 ")", cellCount);
    if (cellCount <= SMALL_PAGE_MAX_BLOCK_SIZE) {
        return AllocateInSmallPage(cellCount);
    }
    if (cellCount > LARGE_PAGE_SIZE_THRESHOLD) {
        return AllocateInLargePage(cellCount);
    }
    return AllocateInMediumPage(cellCount);
}

void* CustomAllocator::AllocateInLargePage(uint64_t cellCount) noexcept {
    CustomAllocDebug("CustomAllocator::AllocateInLargePage(%" PRIu64 ")", cellCount);
    void* block = heap_.GetLargePage(cellCount)->Data();
    return block;
}

void* CustomAllocator::AllocateInMediumPage(uint32_t cellCount) noexcept {
    CustomAllocDebug("CustomAllocator::AllocateInMediumPage(%u)", cellCount);
    if (mediumPage_) {
        Cell* block = mediumPage_->TryAllocate(cellCount);
        if (block) return block->Data();
    }
    CustomAllocDebug("Failed to allocate in curPage");
    while (true) {
        mediumPage_ = heap_.GetMediumPage(cellCount);
        Cell* block = mediumPage_->TryAllocate(cellCount);
        if (block) return block->Data();
    }
}

void* CustomAllocator::AllocateInSmallPage(uint32_t cellCount) noexcept {
    CustomAllocDebug("CustomAllocator::AllocateInSmallPage(%u)", cellCount);
    SmallPage* page = smallPages_[cellCount];
    if (page) {
        SmallCell* block = page->TryAllocate();
        if (block) return block->Data();
    }
    CustomAllocDebug("Failed to allocate in current SmallPage");
    while ((page = heap_.GetSmallPage(cellCount))) {
        SmallCell* block = page->TryAllocate();
        if (block) {
            smallPages_[cellCount] = page;
            return block->Data();
        }
    }
    return nullptr;
}

}  // namespace alloc
}  // namespace kotlin
