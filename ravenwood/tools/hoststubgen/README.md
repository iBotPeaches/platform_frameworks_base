# HostStubGen

## Overview

HostStubGen is a tool built for ravenwood. It can read an Android framework jar file
(such as `framework-minus-apex.jar` or `framework-all.jar`) and
converts them, so that they can be used on the Ravenwood environment.

This directory contains the HostStubGen source code, tests and some library source files
used at runtime.

- HostStubGen itself is design to be agnostic to Android. It doesn't use any Android APIs
(hidden or not). But it may use Android specific knowledge -- e.g. as of now,
AndroidHeuristicsFilter has hardcoded heuristics to detect AIDL generated classes.

- `test-tiny-framework/` contains basic tests that are agnostic to Android.

- More Android specific build files and code are stored in `frameworks/base/Ravenwood.bp`
  `frameworks/base/ravenwood`.
