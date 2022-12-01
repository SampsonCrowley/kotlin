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

    void TransferAllFrom(AtomicStack<T> &other) noexcept {
        // Clear out the `other` stack.
        T* otherHead = nullptr;
        while (!other.stack_.compare_exchange_weak(otherHead, nullptr, std::memory_order_acq_rel)) {}
        // If the `other` stack was empty, do nothing.
        if (!otherHead) return;
        // Now find the tail of `other`. If no deletions are performed, this is safe.
        T* otherTail = otherHead;
        while (otherTail->next_) otherTail = otherTail->next_;
        // Now make `otherTail->next_` point to the current head of `this` and
        // simultaneously make `otherHead` the new current head.
        T* thisHead = nullptr;
        // can't be because of the loop above
        RuntimeAssert(otherTail->next_ == nullptr, "otherTail->next_ must be a tail");
        while (!stack_.compare_exchange_weak(thisHead, otherHead, std::memory_order_acq_rel)) {
            otherTail->next_ = thisHead;
        }
    }

    bool isEmpty() noexcept {
        return stack_.load(std::memory_order_relaxed) == nullptr;
    }

private:
    std::atomic<T*> stack_{nullptr};
};

}  // namespace kotlin::alloc

#endif
