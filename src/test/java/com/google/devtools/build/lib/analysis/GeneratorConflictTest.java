// Copyright 2020 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.analysis;

import static com.google.devtools.build.lib.testutil.MoreAsserts.assertThrows;

import com.google.devtools.build.lib.analysis.util.AnalysisTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * This test ensures that "duplicate generating action" errors in the action
 * graph are correctly detected and reported.
 *
 * <p>Note that conceptually, such errors aren't expressible in the vocabulary of
 * the BUILD language (targets, rules) but only in that of Blaze's action graph
 * (actions, artifacts).  That these errors exist at all is really an
 * implementation restriction of Blaze, and in a perfect world, they wouldn't.
 *
 * <p>While we might be able to catch some of the important cases by checks at the
 * level of the target graph, with sufficiently complex multi-action rules such
 * as cc_library, there are bound to be ways of creating action-graph conflicts
 * even where the target graph is meaningful, hence these checks are required.
 */
@RunWith(JUnit4.class)
public final class GeneratorConflictTest extends AnalysisTestCase {

  @Test
  public void testIntermediateFileConflictsWithAnotherRule() throws Exception {
    scratch.file(
        "conflict/BUILD",
        "cc_library(name='x', srcs=['foo.cc'])",
        "cc_binary(name='_objs/x/foo.o', srcs=['bar.cc'])");

    reporter.removeHandler(failFastHandler); // expect errors

    update("//conflict:x");
    assertNoEvents();
    assertThrows(
        ViewCreationFailedException.class,
        () -> update("//conflict:x", "//conflict:_objs/x/foo.o"));

    assertContainsEvent("file 'conflict/_objs/x/foo.o' is generated by ");
  }

  // Source files don't conflict with bin files.
  // This test exists to clarify the status quo; it's not clear that it asserts
  // ideal behavior.
  @Test
  public void testIntermediateFilesDontConflictWithSources() throws Exception {
    scratch.file("conflict/BUILD", "cc_library(name='x', srcs=['foo.cc', '_objs/x/foo.o'])");

    // No conflict, doesn't fail:
    update("//conflict:x");
    update("//conflict:_objs/x/foo.o");
  }

  // Outputs of a genrule are emitted to genfiles, so there is no conflict with
  // generated artifacts of a cc_library rule.
  // This test exists to clarify the status quo; it's not clear that it asserts
  // ideal behavior.
  @Test
  public void testIntermediateFileDoesntConflictWithGenfiles()
      throws Exception {
    scratch.file(
        "conflict/BUILD",
        "cc_library(name='x', srcs=['foo.cc'])",
        "genrule(name='y', outs=['_objs/x/foo.o'], cmd=':')");

    // No conflict, doesn't fail:
    update("//conflict:x");
    update("//conflict:_objs/x/foo.o");
  }

  @Test
  public void testNoConflictForSameBasename() throws Exception {
    // It should be OK to use source files with the same basename
    // (but different directory names) in the same rule.
    scratch.file("noconflict/BUILD",
                "cc_library(name='lib', srcs=['foo/baz.cc', 'bar/baz.cc'])");
    update("//noconflict:lib");
    assertNoEvents();
  }
}
