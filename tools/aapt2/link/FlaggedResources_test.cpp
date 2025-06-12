/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <regex>
#include <string>

#include "LoadedApk.h"
#include "cmd/Dump.h"
#include "io/StringStream.h"
#include "test/Common.h"
#include "test/Test.h"
#include "text/Printer.h"

using ::aapt::io::StringOutputStream;
using ::aapt::text::Printer;
using testing::Eq;
using testing::Ne;

namespace aapt {

using FlaggedResourcesTest = CommandTestFixture;

static android::NoOpDiagnostics noop_diag;

void DumpStringPoolToString(LoadedApk* loaded_apk, std::string* output) {
  StringOutputStream output_stream(output);
  Printer printer(&output_stream);

  DumpStringsCommand command(&printer, &noop_diag);
  ASSERT_EQ(command.Dump(loaded_apk), 0);
  output_stream.Flush();
}

void DumpResourceTableToString(LoadedApk* loaded_apk, std::string* output) {
  StringOutputStream output_stream(output);
  Printer printer(&output_stream);

  DumpTableCommand command(&printer, &noop_diag);
  ASSERT_EQ(command.Dump(loaded_apk), 0);
  output_stream.Flush();
}

void DumpChunksToString(LoadedApk* loaded_apk, std::string* output) {
  StringOutputStream output_stream(output);
  Printer printer(&output_stream);

  DumpChunks command(&printer, &noop_diag);
  ASSERT_EQ(command.Dump(loaded_apk), 0);
  output_stream.Flush();
}

void DumpXmlTreeToString(LoadedApk* loaded_apk, std::string file, std::string* output) {
  StringOutputStream output_stream(output);
  Printer printer(&output_stream);

  auto xml = loaded_apk->LoadXml(file, &noop_diag);
  ASSERT_NE(xml, nullptr);
  Debug::DumpXml(*xml, &printer);
  output_stream.Flush();
}

TEST_F(FlaggedResourcesTest, DisabledStringRemovedFromPool) {
  auto apk_path = file::BuildPath({android::base::GetExecutableDirectory(), "resapp.apk"});
  auto loaded_apk = LoadedApk::LoadApkFromPath(apk_path, &noop_diag);

  std::string output;
  DumpStringPoolToString(loaded_apk.get(), &output);

  std::string excluded = "DONTFIND";
  ASSERT_EQ(output.find(excluded), std::string::npos);
}

TEST_F(FlaggedResourcesTest, DisabledResourcesRemovedFromTable) {
  auto apk_path = file::BuildPath({android::base::GetExecutableDirectory(), "resapp.apk"});
  auto loaded_apk = LoadedApk::LoadApkFromPath(apk_path, &noop_diag);

  std::string output;
  DumpResourceTableToString(loaded_apk.get(), &output);
  ASSERT_EQ(output.find("bool4"), std::string::npos);
  ASSERT_EQ(output.find("str1"), std::string::npos);
  ASSERT_EQ(output.find("layout2"), std::string::npos);
  ASSERT_EQ(output.find("removedpng"), std::string::npos);
}

TEST_F(FlaggedResourcesTest, DisabledResourcesRemovedFromTableChunks) {
  auto apk_path = file::BuildPath({android::base::GetExecutableDirectory(), "resapp.apk"});
  auto loaded_apk = LoadedApk::LoadApkFromPath(apk_path, &noop_diag);

  std::string output;
  DumpChunksToString(loaded_apk.get(), &output);

  ASSERT_EQ(output.find("bool4"), std::string::npos);
  ASSERT_EQ(output.find("str1"), std::string::npos);
  ASSERT_EQ(output.find("layout2"), std::string::npos);
  ASSERT_EQ(output.find("removedpng"), std::string::npos);
}

TEST_F(FlaggedResourcesTest, DisabledResourcesInRJava) {
  auto r_path = file::BuildPath({android::base::GetExecutableDirectory(), "resource-flagging-java",
                                 "com", "android", "intenal", "flaggedresources", "R.java"});
  std::string r_contents;
  ::android::base::ReadFileToString(r_path, &r_contents);

  ASSERT_NE(r_contents.find("public static final int bool4"), std::string::npos);
  ASSERT_NE(r_contents.find("public static final int str1"), std::string::npos);
}

TEST_F(FlaggedResourcesTest, TwoValuesSameDisabledFlag) {
  test::TestDiagnosticsImpl diag;
  const std::string compiled_files_dir = GetTestPath("compiled");
  ASSERT_FALSE(CompileFile(
      GetTestPath("res/values/values.xml"),
      R"(<resources xmlns:android="http://schemas.android.com/apk/res/android">
           <bool name="bool1" android:featureFlag="test.package.falseFlag">false</bool>
           <bool name="bool1" android:featureFlag="test.package.falseFlag">true</bool>
         </resources>)",
      compiled_files_dir, &diag,
      {"--feature-flags", "test.package.falseFlag:ro=false,test.package.trueFlag:ro=true"}));
  ASSERT_TRUE(diag.GetLog().contains("duplicate value for resource 'bool/bool1'"));
}

TEST_F(FlaggedResourcesTest, TwoValuesSameDisabledFlagDifferentFiles) {
  test::TestDiagnosticsImpl diag;
  const std::string compiled_files_dir = GetTestPath("compiled");
  ASSERT_TRUE(CompileFile(
      GetTestPath("res/values/values1.xml"),
      R"(<resources xmlns:android="http://schemas.android.com/apk/res/android">
           <bool name="bool1" android:featureFlag="test.package.falseFlag">false</bool>
         </resources>)",
      compiled_files_dir, &diag,
      {"--feature-flags", "test.package.falseFlag:ro=false,test.package.trueFlag:ro=true"}));
  ASSERT_TRUE(CompileFile(
      GetTestPath("res/values/values2.xml"),
      R"(<resources xmlns:android="http://schemas.android.com/apk/res/android">
           <bool name="bool1" android:featureFlag="test.package.falseFlag">true</bool>
         </resources>)",
      compiled_files_dir, &diag,
      {"--feature-flags", "test.package.falseFlag:ro=false,test.package.trueFlag:ro=true"}));
  const std::string out_apk = GetTestPath("out.apk");
  std::vector<std::string> link_args = {
      "--manifest",
      GetDefaultManifest(),
      "-o",
      out_apk,
  };

  ASSERT_FALSE(Link(link_args, compiled_files_dir, &diag));
  ASSERT_TRUE(diag.GetLog().contains("duplicate value for resource 'bool1'"));
}

TEST_F(FlaggedResourcesTest, EnabledXmlELementAttributeRemoved) {
  auto apk_path = file::BuildPath({android::base::GetExecutableDirectory(), "resapp.apk"});
  auto loaded_apk = LoadedApk::LoadApkFromPath(apk_path, &noop_diag);

  std::string output;
  DumpXmlTreeToString(loaded_apk.get(), "res/layout-v36/layout1.xml", &output);
  ASSERT_FALSE(output.contains("test.package.trueFlag"));
  ASSERT_TRUE(output.contains("FIND_ME"));
  ASSERT_TRUE(output.contains("test.package.readWriteFlag"));
}

TEST_F(FlaggedResourcesTest, ReadWriteFlagInPathFails) {
  test::TestDiagnosticsImpl diag;
  const std::string compiled_files_dir = GetTestPath("compiled");
  ASSERT_FALSE(CompileFile(GetTestPath("res/values/flag(!test.package.rwFlag)/bools.xml"),
                           R"(<resources>
                                <bool name="bool1">false</bool>
                              </resources>)",
                           compiled_files_dir, &diag,
                           {"--feature-flags", "test.package.rwFlag=false"}));

  ASSERT_TRUE(diag.GetLog().contains(
      "Only read only flags may be used with resources: test.package.rwFlag"));
}

TEST_F(FlaggedResourcesTest, ReadWriteFlagInXmlGetsFlagged) {
  auto apk_path = file::BuildPath({android::base::GetExecutableDirectory(), "resapp.apk"});
  auto loaded_apk = LoadedApk::LoadApkFromPath(apk_path, &noop_diag);

  std::string output;
  DumpChunksToString(loaded_apk.get(), &output);

  // The actual line looks something like:
  // [ResTable_entry] id: 0x0000 name: layout1 keyIndex: 14 size: 8 flags: 0x0010
  //
  // This regex matches that line and captures the name and the flag value for checking.
  std::regex regex("[0-9a-zA-Z:_\\]\\[ ]+name: ([0-9a-zA-Z]+)[0-9a-zA-Z: ]+flags: (0x\\d{4})");
  std::smatch match;

  std::stringstream ss(output);
  std::string line;
  bool found = false;
  int fields_flagged = 0;
  while (std::getline(ss, line)) {
    bool first_line = false;
    if (line.contains("config: v36")) {
      std::getline(ss, line);
      first_line = true;
    }
    if (!line.contains("flags")) {
      continue;
    }
    if (std::regex_search(line, match, regex) && (match.size() == 3)) {
      unsigned int hex_value;
      std::stringstream hex_ss;
      hex_ss << std::hex << match[2];
      hex_ss >> hex_value;
      if (hex_value & android::ResTable_entry::FLAG_USES_FEATURE_FLAGS) {
        fields_flagged++;
        if (first_line && match[1] == "layout1") {
          found = true;
        }
      }
    }
  }

  ASSERT_TRUE(found) << "No entry for layout1 at v36 with FLAG_USES_FEATURE_FLAGS bit set";
  // There should only be 2 entry that has the FLAG_USES_FEATURE_FLAGS bit of flags set to 1, the
  // three versions of the layout file that has flags
  ASSERT_EQ(fields_flagged, 3);
}

}  // namespace aapt
