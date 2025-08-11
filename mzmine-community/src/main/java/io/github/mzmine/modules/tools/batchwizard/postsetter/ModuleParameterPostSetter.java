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
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.UserParameter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record ModuleParameterPostSetter<V>(@NotNull Class<? extends MZmineProcessingModule> module, @NotNull UserParameter<V, ?> parameter,
                                           @Nullable V valueToSet, @NotNull ModuleSelectionRule rule) {

  public boolean apply(BatchQueue q) {
    List<String> errorMessages = new ArrayList<>();
    final List<? extends @NotNull MZmineProcessingStep<?>> steps = rule.getSteps(q, module);
    for (MZmineProcessingStep<?> step : steps) {
      final ParameterSet parameterSet = step.getParameterSet();
      if (parameterSet == null) {
        continue;
      }
      parameterSet.setParameter(parameter, valueToSet);
      if (!parameterSet.checkParameterValues(errorMessages, true)) {
        throw new RuntimeException(
            "Cannot apply specific parameter value %s to parameter %s of step %s.\nError message:\n%s".formatted(
                String.valueOf(valueToSet), parameter.getName(), step.getModule().getName(),
                errorMessages.stream().collect(Collectors.joining(","))));
      }
    }
    return true;
  }
}
