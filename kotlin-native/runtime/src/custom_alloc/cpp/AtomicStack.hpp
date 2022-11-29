// Copyright 2022 Google LLC

#ifndef CUSTOM_ALLOC_CPP_ATOMICSTACK_HPP_
#define CUSTOM_ALLOC_CPP_ATOMICSTACK_HPP_

#include <atomic>

#include "CustomLogging.hpp"

namespace kotlin::alloc {

template<class T>
class AtomicStack {
public:
    // Pop() is not fully thread-safe, in that the returned page must not be
    // immediately freed, if another thread might be simultaneously Popping
    // from the same stack. As of writing this comment, this is handled by only
    // freeing pages during STW.
    T* Pop() noexcept {
        T* elm = stack_.load(std::memory_order_acquire);
        while (elm && !stack_.compare_exchange_weak(elm, elm->next_,
                    std::memory_order_acq_rel)) {}
        CustomAllocDebug("AtomicStack(%p)::Pop() = %p", this, elm);
        return elm;
    }

    void Push(T* elm) noexcept {
        T* head = nullptr;
        do {
            elm->next_ = head;
        } while (!stack_.compare_exchange_weak(head, elm,
                    std::memory_order_acq_rel));
    }

    void NonatomicTransferAllFrom(AtomicStack<T> &other) noexcept {
        T* head = other.stack_.load(std::memory_order_acquire);
        if (!head) return;
        other.stack_.store(nullptr, std::memory_order_release);
        T* tail = stack_.load(std::memory_order_acquire);
        if (tail) {
            while (tail->next_) tail = tail->next_;
            tail->next_ = head;
        } else {
            stack_.store(head, std::memory_order_release);
        }
    }

    // Should only be called during STW.
    void FreeAllPages() noexcept {
        CustomAllocDebug("AtomicStack(%p)::FreeAllPages()", this);
        T* page;
        while ((page = Pop())) {
            CustomAllocDebug("AtomicStack(%p) free(%p)", this, page);
            free(page);
        }
    }

private:
    std::atomic<T*> stack_{nullptr};
};

}  // namespace kotlin::alloc

#endif
