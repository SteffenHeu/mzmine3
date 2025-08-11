/*
 * Copyright (c) 2004-2025 The mzmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.modules.tools.batchwizard.postsetter;

import io.github.mzmine.modules.MZmineProcessingModule;
import io.github.mzmine.modules.MZmineProcessingStep;
import io.github.mzmine.modules.batchmode.BatchQueue;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public final class AllModules implements ModuleSelectionRule {

  private final Class<? extends MZmineProcessingModule> module;

  public AllModules(Class<? extends MZmineProcessingModule> module) {
    this.module = module;
  }

  @Override
  public @NotNull List<? extends @NotNull MZmineProcessingStep<?>> getSteps(BatchQueue q) {
    final List<MZmineProcessingStep<?>> result = new ArrayList<>();
    for (int i = 0; i < q.size(); i--) {
      final MZmineProcessingStep<MZmineProcessingModule> s = q.get(i);
      if (module.isInstance(s.getModule())) {
        result.add(s);
      }
    }
    return result;
  }
}
