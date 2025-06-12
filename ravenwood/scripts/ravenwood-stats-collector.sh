#!/bin/bash
# Copyright (C) 2024 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Script to collect the ravenwood "stats" CVS files and create a single file.

set -e

# Output files
out_dir=/tmp/ravenwood
stats=$out_dir/ravenwood-stats-all.csv
apis=$out_dir/ravenwood-apis-all.csv
keep_all_dir=$out_dir/ravenwood-keep-all/
dump_dir=$out_dir/ravenwood-dump/

rm -fr $out_dir
mkdir -p $out_dir
mkdir -p $keep_all_dir
mkdir -p $dump_dir


stats_checker_module="ravenwood-stats-checker"
minfo=$OUT/module-info.json

timestamp="$(date --iso-8601=seconds)"

# First, use jq to get the output files from the checker module. This will be something like this:
#
# ---
# out/host/linux-x86/nativetest64/ravenwood-stats-checker/framework-configinfrastructure_apis.csv
# out/host/linux-x86/nativetest64/ravenwood-stats-checker/framework-configinfrastructure_dump.txt
#  :
# out/host/linux-x86/nativetest64/ravenwood-stats-checker/hoststubgen_services.core_stats.csv
# out/host/linux-x86/nativetest64/ravenwood-stats-checker/ravenwood-stats-checker
# ---
# Then, use grep to find the script's path (the last line in the above examle)
script_path="$(
    jq -r ".\"$stats_checker_module\".installed | .[]" $minfo |
    grep '/ravenwood-stats-checker$'
)"

if [[ "$script_path" == "" ]] ; then
    echo "Error: $stats_checker_module script not found from $minfo"
    exit 1
fi

# This is the directory where our input files are.
script_dir="$ANDROID_BUILD_TOP/$(dirname "$script_path")"

# Clear it before (re-)buildign the script, to make sure we won't have stale files.
rm -fr "$script_dir"

# Then build it, which will also collect the input files in the same dir.
echo "Collecting the input files..."
m "$stats_checker_module"

# Start...

echo "Files directory is: $script_dir"
cd "$script_dir"

dump() {
    local jar=$1
    local file=$2

    # Remove the header row, and prepend the columns.
    sed -e '1d' -e "s/^/$jar,$timestamp,/" $file
}

collect_stats() {
    local out="$1"
    local desc="$2"
    {
        # Copy the header, with the first column appended.
        echo -n "Jar,Generated Date,"
        head -n 1 hoststubgen_framework-minus-apex_stats.csv

        dump "framework-minus-apex" hoststubgen_framework-minus-apex_stats.csv
        dump "service.core"  hoststubgen_services.core_stats.csv
        dump "framework-configinfrastructure"  framework-configinfrastructure_stats.csv
        dump "framework-statsd"  framework-statsd_stats.csv
    } > "$out"

    echo "Stats CVS created at $out$desc"
}

collect_apis() {
    local out="$1"
    local desc="$2"
    {
        # Copy the header, with the first column appended.
        echo -n "Jar,Generated Date,"
        head -n 1 hoststubgen_framework-minus-apex_apis.csv

        dump "framework-minus-apex"  hoststubgen_framework-minus-apex_apis.csv
        dump "service.core"  hoststubgen_services.core_apis.csv
        dump "framework-configinfrastructure"  framework-configinfrastructure_apis.csv
        dump "framework-statsd"  framework-statsd_apis.csv
    } > "$out"

    echo "API CVS created at $out$desc"
}


collect_stats $stats " (import it as 'ravenwood_stats')"
collect_apis $apis " (import it as 'ravenwood_supported_apis2')"

cp *keep_all.txt $keep_all_dir
echo "Keep all files created at:"
find $keep_all_dir -type f

cp *dump.txt $dump_dir
echo "Dump files created at:"
find $dump_dir -type f
