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

#include "Debug.h"
#include "SdkConstants.h"
#include "io/StringStream.h"
#include "test/Test.h"

using ::aapt::test::ValueEq;
using ::testing::Eq;
using ::testing::IsNull;
using ::testing::NotNull;
using ::testing::Pointee;
using ::testing::SizeIs;

namespace aapt {

class FlaggedXmlVersionerTest : public ::testing::Test {
 public:
  void SetUp() override {
    context_ = test::ContextBuilder()
                   .SetCompilationPackage("com.app")
                   .SetPackageId(0x7f)
                   .SetPackageType(PackageType::kApp)
                   .Build();
  }

 protected:
  std::unique_ptr<IAaptContext> context_;
};

static void PrintDocToString(xml::XmlResource* doc, std::string* out) {
  io::StringOutputStream stream(out, 1024u);
  text::Printer printer(&stream);
  Debug::DumpXml(*doc, &printer);
  stream.Flush();
}

TEST_F(FlaggedXmlVersionerTest, NoFlagReturnsOriginal) {
  auto doc = test::BuildXmlDomForPackageName(context_.get(), R"(
      <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
        <TextView />
        <TextView />
        <TextView />
      </LinearLayout>)");
  doc->file.config.sdkVersion = SDK_GINGERBREAD;

  FlaggedXmlVersioner versioner;
  auto results = versioner.Process(context_.get(), doc.get());
  EXPECT_THAT(results.size(), Eq(1));
  EXPECT_THAT(results[0]->file.config.sdkVersion, Eq(SDK_GINGERBREAD));

  std::string expected;
  PrintDocToString(doc.get(), &expected);
  std::string actual;
  PrintDocToString(results[0].get(), &actual);

  EXPECT_THAT(actual, Eq(expected));
}

TEST_F(FlaggedXmlVersionerTest, AlreadyBaklavaReturnsOriginal) {
  auto doc = test::BuildXmlDomForPackageName(context_.get(), R"(
      <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
        <TextView android:featureFlag="package.flag" />
        <TextView />
        <TextView />
      </LinearLayout>)");
  doc->file.config.sdkVersion = SDK_BAKLAVA;

  FlaggedXmlVersioner versioner;
  auto results = versioner.Process(context_.get(), doc.get());
  EXPECT_THAT(results.size(), Eq(1));
  EXPECT_THAT(results[0]->file.config.sdkVersion, Eq(SDK_BAKLAVA));

  std::string expected;
  PrintDocToString(doc.get(), &expected);
  std::string actual;
  PrintDocToString(results[0].get(), &actual);

  EXPECT_THAT(actual, Eq(expected));
}

TEST_F(FlaggedXmlVersionerTest, PreBaklavaGetsSplit) {
  auto doc = test::BuildXmlDomForPackageName(context_.get(), R"(
      <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
        <TextView android:featureFlag="package.flag" /><TextView /><TextView />
      </LinearLayout>)");
  doc->file.config.sdkVersion = SDK_GINGERBREAD;
  doc->file.uses_readwrite_feature_flags = true;

  FlaggedXmlVersioner versioner;
  auto results = versioner.Process(context_.get(), doc.get());
  EXPECT_THAT(results.size(), Eq(2));
  EXPECT_THAT(results[0]->file.config.sdkVersion, Eq(SDK_GINGERBREAD));
  EXPECT_THAT(results[1]->file.config.sdkVersion, Eq(SDK_BAKLAVA));

  auto gingerbread_doc = test::BuildXmlDomForPackageName(context_.get(), R"(
      <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
        <TextView /><TextView />
      </LinearLayout>)");

  std::string expected0;
  PrintDocToString(gingerbread_doc.get(), &expected0);
  std::string actual0;
  PrintDocToString(results[0].get(), &actual0);
  EXPECT_THAT(actual0, Eq(expected0));

  std::string expected1;
  PrintDocToString(doc.get(), &expected1);
  std::string actual1;
  PrintDocToString(results[1].get(), &actual1);
  EXPECT_THAT(actual1, Eq(expected1));
}

TEST_F(FlaggedXmlVersionerTest, NoVersionGetsSplit) {
  auto doc = test::BuildXmlDomForPackageName(context_.get(), R"(
      <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
        <TextView android:featureFlag="package.flag" /><TextView /><TextView />
      </LinearLayout>)");
  doc->file.uses_readwrite_feature_flags = true;

  FlaggedXmlVersioner versioner;
  auto results = versioner.Process(context_.get(), doc.get());
  EXPECT_THAT(results.size(), Eq(2));
  EXPECT_THAT(results[0]->file.config.sdkVersion, Eq(0));
  EXPECT_THAT(results[1]->file.config.sdkVersion, Eq(SDK_BAKLAVA));

  auto gingerbread_doc = test::BuildXmlDomForPackageName(context_.get(), R"(
      <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
        <TextView /><TextView />
      </LinearLayout>)");

  std::string expected0;
  PrintDocToString(gingerbread_doc.get(), &expected0);
  std::string actual0;
  PrintDocToString(results[0].get(), &actual0);
  EXPECT_THAT(actual0, Eq(expected0));

  std::string expected1;
  PrintDocToString(doc.get(), &expected1);
  std::string actual1;
  PrintDocToString(results[1].get(), &actual1);
  EXPECT_THAT(actual1, Eq(expected1));
}

TEST_F(FlaggedXmlVersionerTest, NegatedFlagAttributeRemoved) {
  auto doc = test::BuildXmlDomForPackageName(context_.get(), R"(
      <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
        <TextView android:featureFlag="!package.flag" /><TextView /><TextView />
      </LinearLayout>)");
  doc->file.config.sdkVersion = SDK_GINGERBREAD;
  doc->file.uses_readwrite_feature_flags = true;

  FlaggedXmlVersioner versioner;
  auto results = versioner.Process(context_.get(), doc.get());
  EXPECT_THAT(results.size(), Eq(2));
  EXPECT_THAT(results[0]->file.config.sdkVersion, Eq(SDK_GINGERBREAD));
  EXPECT_THAT(results[1]->file.config.sdkVersion, Eq(SDK_BAKLAVA));

  auto gingerbread_doc = test::BuildXmlDomForPackageName(context_.get(), R"(
      <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
        <TextView /><TextView /><TextView />
      </LinearLayout>)");

  std::string expected0;
  PrintDocToString(gingerbread_doc.get(), &expected0);
  std::string actual0;
  PrintDocToString(results[0].get(), &actual0);
  EXPECT_THAT(actual0, Eq(expected0));

  std::string expected1;
  PrintDocToString(doc.get(), &expected1);
  std::string actual1;
  PrintDocToString(results[1].get(), &actual1);
  EXPECT_THAT(actual1, Eq(expected1));
}

TEST_F(FlaggedXmlVersionerTest, NegatedFlagAttributeRemovedNoSpecifiedVersion) {
  auto doc = test::BuildXmlDomForPackageName(context_.get(), R"(
      <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
        <TextView android:featureFlag="!package.flag" /><TextView /><TextView />
      </LinearLayout>)");
  doc->file.uses_readwrite_feature_flags = true;

  FlaggedXmlVersioner versioner;
  auto results = versioner.Process(context_.get(), doc.get());
  EXPECT_THAT(results.size(), Eq(2));
  EXPECT_THAT(results[0]->file.config.sdkVersion, Eq(0));
  EXPECT_THAT(results[1]->file.config.sdkVersion, Eq(SDK_BAKLAVA));

  auto gingerbread_doc = test::BuildXmlDomForPackageName(context_.get(), R"(
      <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
        <TextView /><TextView /><TextView />
      </LinearLayout>)");

  std::string expected0;
  PrintDocToString(gingerbread_doc.get(), &expected0);
  std::string actual0;
  PrintDocToString(results[0].get(), &actual0);
  EXPECT_THAT(actual0, Eq(expected0));

  std::string expected1;
  PrintDocToString(doc.get(), &expected1);
  std::string actual1;
  PrintDocToString(results[1].get(), &actual1);
  EXPECT_THAT(actual1, Eq(expected1));
}

}  // namespace aapt