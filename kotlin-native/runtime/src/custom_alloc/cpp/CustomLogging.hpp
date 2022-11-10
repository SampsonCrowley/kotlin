// Copyright 2022 Google LLC

#ifndef CUSTOM_ALLOC_CPP_CUSTOMLOGGING_HPP_
#define CUSTOM_ALLOC_CPP_CUSTOMLOGGING_HPP_

#include "Logging.hpp"
#include "Porting.h"

#define CustomAllocInfo(format, ...) RuntimeLogInfo({"ca"}, "t%u " format, konan::currentThreadId(), ##__VA_ARGS__)
#define CustomAllocDebug(format, ...) RuntimeLogDebug({"ca"}, "t%u " format, konan::currentThreadId(), ##__VA_ARGS__)
#define CustomAllocWarning(format, ...) RuntimeLogWarning({"ca"}, "t%u " format, konan::currentThreadId(), ##__VA_ARGS__)
#define CustomAllocError(format, ...) RuntimeLogError({"ca"}, "t%u " format, konan::currentThreadId(), ##__VA_ARGS__)

#endif
