/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <memory>
#include <vector>

#include "process/IResourceTableConsumer.h"
#include "xml/XmlDom.h"

namespace aapt {

// FlaggedXmlVersioner takes an XmlResource and checks if any elements have read write android
// flags on them. If the doc doesn't refer to any such flags the returned vector only contains
// the original doc.
//
// Read/write flags within xml resources files is only supported in android baklava and later. If
// the config resource specifies a version that is baklava or later it returns a vector containing
// the original XmlResource. Otherwise FlaggedXmlVersioner creates a version of the doc where all
// flags are assumed disabled and the config version is the same as the original doc, if specified.
// It also creates an XmlResource where the contents are the same as the original doc and the config
// version is baklava. The returned vector is composed of these two new docs.
class FlaggedXmlVersioner {
 public:
  std::vector<std::unique_ptr<xml::XmlResource>> Process(IAaptContext* context,
                                                         xml::XmlResource* doc);
};
}  // namespace aapt