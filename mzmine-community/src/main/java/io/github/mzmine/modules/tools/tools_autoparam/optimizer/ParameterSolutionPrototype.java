/*
 * Copyright (c) 2004-2026 The mzmine Development Team
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
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.modules.tools.tools_autoparam.optimizer;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.moeaframework.core.variable.Variable;

/**
 * A prototype / factory for an optimization parameter. Used as items in the
 * {@link WizardParameterSolutionCheckListParameter} checklist and stored in
 * {@link OptimizerParameters}. Two sub-types exist:
 * <ul>
 *   <li>{@link WizardParameterSolutionPrototype} — wizard-side parameter, built via
 *       {@link WizardParameterSolutionBuilder}</li>
 *   <li>{@link BatchParameterSolutionPrototype} — batch-queue parameter, built via
 *       {@link BatchParameterSolutionBuilder}</li>
 * </ul>
 * Instances carry only the data needed for display and XML serialisation. The real,
 * index-aware solution objects are produced on demand during optimisation.
 * <p>
 * All solutions must have a distinct name, because that is how the equality will be checked!
 */
public sealed interface ParameterSolutionPrototype {

  /**
   * Variable supplier used solely to derive {@link #name()} for display and XML.
   */
  Supplier<? extends Variable> variable();

  default String name() {
    return variable().get().getName();
  }

  /**
   * Prototype for a wizard-side optimization parameter. Call
   * {@link #toRealSolution(WizardParameterSolutionBuilder, int)} to obtain the actual
   * {@link WizardParameterSolution} with real data ranges and a correct solution-vector index.
   *
   * @param variable dummy variable supplier from a default-range builder, used for {@link #name()}
   * @param factory  builds the real {@link WizardParameterSolution} given a builder and index
   */
  record WizardParameterSolutionPrototype(Supplier<? extends Variable> variable,
                                          BiFunction<WizardParameterSolutionBuilder, Integer, WizardParameterSolution> factory) implements
      ParameterSolutionPrototype {

    /**
     * Builds the real solution with actual data ranges and the given solution-vector index.
     */
    public WizardParameterSolution toRealSolution(WizardParameterSolutionBuilder builder, int idx) {
      return factory.apply(builder, idx);
    }

    @Override
    public @NotNull String toString() {
      return name();
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(name());
    }

    @Override
    public boolean equals(@Nullable Object other) {
      return other instanceof WizardParameterSolutionPrototype wp && wp.name().equals(this.name());
    }
  }

  /**
   * Prototype for a batch-queue optimization parameter. Call {@link #toBatchParameterSolution(int)}
   * to obtain the actual {@link BatchParameterSolution} with the correct solution-vector index.
   *
   * @param factory builds the {@link BatchParameterSolution} for a given solution-vector index
   */
  record BatchParameterSolutionPrototype(IntFunction<BatchParameterSolution> factory) implements
      ParameterSolutionPrototype {

    @Override
    public Supplier<? extends Variable> variable() {
      return factory.apply(0).variable();
    }

    /**
     * Creates the concrete {@link BatchParameterSolution} with the given solution-vector index.
     */
    public BatchParameterSolution toBatchParameterSolution(int idx) {
      return factory.apply(idx);
    }

    @Override
    public @NotNull String toString() {
      return name();
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(name());
    }

    @Override
    public boolean equals(@Nullable Object other) {
      return other instanceof BatchParameterSolutionPrototype wp && wp.name().equals(this.name());
    }
  }
}
