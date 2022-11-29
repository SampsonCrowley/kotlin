// Copyright 2022 Google LLC

#ifndef CUSTOM_ALLOC_CPP_CUSTOMLOGGING_HPP_
#define CUSTOM_ALLOC_CPP_CUSTOMLOGGING_HPP_

#include "Logging.hpp"
#include "Porting.h"

#define CustomAllocInfo(format, ...) RuntimeLogInfo({"alloc"}, "t%u " format, konan::currentThreadId(), ##__VA_ARGS__)
#define CustomAllocDebug(format, ...) RuntimeLogDebug({"alloc"}, "t%u " format, konan::currentThreadId(), ##__VA_ARGS__)
#define CustomAllocWarning(format, ...) RuntimeLogWarning({"alloc"}, "t%u " format, konan::currentThreadId(), ##__VA_ARGS__)
#define CustomAllocError(format, ...) RuntimeLogError({"alloc"}, "t%u " format, konan::currentThreadId(), ##__VA_ARGS__)

#endif
