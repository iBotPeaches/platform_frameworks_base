/*
 * Copyright (C) 2018 The Android Open Source Project
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
#include "Command.h"

namespace aapt {

class DiffCommand : public Command {
 public:
  explicit DiffCommand() : Command("diff") {
    SetDescription("Prints the differences in resources of two apks.");
    AddOptionalSwitch("--ignore-id-shift",
                      "Match the resources when their IDs shift, e.g. because of the added\n"
                      "or deleted entries.",
                      &ignore_id_shift_);
  }

  int Action(const std::vector<std::string>& args) override;

 private:
  bool ignore_id_shift_ = false;
};

}  // namespace aapt
