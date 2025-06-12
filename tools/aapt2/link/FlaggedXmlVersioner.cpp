/*
 * Copyright (C) 2025 The Android Open Source Project
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

#include "link/FlaggedXmlVersioner.h"

#include "SdkConstants.h"
#include "androidfw/Util.h"

using ::aapt::xml::Element;
using ::aapt::xml::NodeCast;

namespace aapt {

// An xml visitor that goes through the a doc and removes any elements that are behind non-negated
// flags. It also removes the featureFlag attribute from elements behind negated flags.
class AllDisabledFlagsVisitor : public xml::Visitor {
 public:
  void Visit(xml::Element* node) override {
    std::erase_if(node->children, [this](const std::unique_ptr<xml::Node>& node) {
      return FixupOrShouldRemove(node);
    });
    VisitChildren(node);
  }

 private:
  bool FixupOrShouldRemove(const std::unique_ptr<xml::Node>& node) {
    if (auto* el = NodeCast<Element>(node.get())) {
      auto* attr = el->FindAttribute(xml::kSchemaAndroid, xml::kAttrFeatureFlag);
      if (attr == nullptr) {
        return false;
      }

      // This class assumes all flags are disabled so we want to remove any elements behind flags
      // unless the flag specification is negated. In the negated case we remove the featureFlag
      // attribute because we have already determined whether we are keeping the element or not.
      std::string_view flag_name = util::TrimWhitespace(attr->value);
      if (flag_name.starts_with('!')) {
        el->RemoveAttribute(xml::kSchemaAndroid, xml::kAttrFeatureFlag);
        return false;
      } else {
        return true;
      }
    }

    return false;
  }
};

std::vector<std::unique_ptr<xml::XmlResource>> FlaggedXmlVersioner::Process(IAaptContext* context,
                                                                            xml::XmlResource* doc) {
  std::vector<std::unique_ptr<xml::XmlResource>> docs;
  if (!doc->file.uses_readwrite_feature_flags) {
    docs.push_back(doc->Clone());
  } else if ((static_cast<ApiVersion>(doc->file.config.sdkVersion) >= SDK_BAKLAVA) ||
             (static_cast<ApiVersion>(context->GetMinSdkVersion()) >= SDK_BAKLAVA)) {
    // Support for read/write flags was added in baklava so if the doc will only get used on
    // baklava or later we can just return the original doc.
    docs.push_back(doc->Clone());
  } else {
    auto preBaklavaVersion = doc->Clone();
    AllDisabledFlagsVisitor visitor;
    preBaklavaVersion->root->Accept(&visitor);
    docs.push_back(std::move(preBaklavaVersion));

    auto baklavaVersion = doc->Clone();
    baklavaVersion->file.config.sdkVersion = SDK_BAKLAVA;
    docs.push_back(std::move(baklavaVersion));
  }
  return docs;
}

}  // namespace aapt