#!/bin/bash
#
# Copyright 2015 The Bazel Authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Test the local_repository binding
#

# Load test environment
source $(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/test-setup.sh \
  || { echo "test-setup.sh not found!" >&2; exit 1; }

# Basic test.
function test_macro_local_repository() {
  create_new_workspace
  repo2=$new_workspace_dir

  mkdir -p carnivore
  cat > carnivore/BUILD <<'EOF'
genrule(
    name = "mongoose",
    cmd = "echo 'Tra-la!' > $@",
    outs = ["moogoose.txt"],
    visibility = ["//visibility:public"],
)
EOF
  cd ${WORKSPACE_DIR}
  pwd
  cat > WORKSPACE <<EOF
load('/test', 'macro')

macro('$repo2')
EOF

  # Empty package for the .bzl file
  echo -n >BUILD

  # Our macro
  cat >test.bzl <<EOF
def macro(path):
  print('bleh')
  native.local_repository(name='endangered', path=path)
  native.bind(name='mongoose', actual='@endangered//carnivore:mongoose')
EOF
  mkdir -p zoo
  cat > zoo/BUILD <<'EOF'
genrule(
    name = "ball-pit1",
    srcs = ["@endangered//carnivore:mongoose"],
    outs = ["ball-pit1.txt"],
    cmd = "cat $< >$@",
)

genrule(
    name = "ball-pit2",
    srcs = ["//external:mongoose"],
    outs = ["ball-pit2.txt"],
    cmd = "cat $< >$@",
)
EOF

  bazel build //zoo:ball-pit1 >& $TEST_log || fail "Failed to build"
  expect_log "bleh."
  cat bazel-genfiles/zoo/ball-pit1.txt >$TEST_log
  expect_log "Tra-la!"

  bazel build //zoo:ball-pit2 >& $TEST_log || fail "Failed to build"
  cat bazel-genfiles/zoo/ball-pit2.txt >$TEST_log
  expect_log "Tra-la!"
}

function tear_down() {
  true
}

run_suite "local repository tests"
