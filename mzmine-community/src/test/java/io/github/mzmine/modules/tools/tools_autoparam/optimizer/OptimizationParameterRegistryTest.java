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

import io.github.mzmine.modules.tools.batchwizard.WizardPart;
import io.github.mzmine.modules.tools.batchwizard.WizardSequence;
import io.github.mzmine.modules.tools.batchwizard.subparameters.factories.IonInterfaceWizardParameterFactory;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OptimizationParameterRegistryTest {

  @Test
  void defaultSelectionExcludesOnlyOptionalWaveletParameters() {
    final List<String> allNames = OptimizationParameterRegistry.allSolutions().stream()
        .map(ParameterSolutionPrototype::name).toList();
    final List<String> defaultNames = OptimizationParameterRegistry.defaultSolutions().stream()
        .map(ParameterSolutionPrototype::name).toList();

    Assertions.assertTrue(allNames.contains("Wavelet SNR threshold"));
    Assertions.assertFalse(defaultNames.stream().anyMatch(name -> name.startsWith("Wavelet ")));
    Assertions.assertTrue(defaultNames.contains("Min height"));
    Assertions.assertTrue(defaultNames.contains("Top-to-edge ratio"));
    Assertions.assertEquals(allNames.size() - 3, defaultNames.size());
  }

  @Test
  void sequenceContributesOnlyParametersForItsResolver() {
    final WizardSequence sequence = new WizardSequence();
    sequence.set(WizardPart.ION_INTERFACE,
        IonInterfaceWizardParameterFactory.LC_WAVELET.create());

    final List<String> names = OptimizationParameterRegistry.forSequence(sequence).stream()
        .map(ParameterSolutionPrototype::name).toList();

    Assertions.assertEquals(List.of("Inter sample RT tolerance", "Min consecutive",
        "Wavelet SNR threshold", "Wavelet baseline method", "Wavelet noise calculation"), names);
    Assertions.assertFalse(names.contains("Top-to-edge ratio"));
  }

  @Test
  void parametersDeclareTheirSearchScale() {
    final Map<String, SearchScale> scales = OptimizationParameterRegistry.allSolutions().stream()
        .collect(Collectors.toMap(ParameterSolutionPrototype::name,
            ParameterSolutionPrototype::searchScale));

    Assertions.assertEquals(SearchScale.LOGARITHMIC, scales.get("Inter sample RT tolerance"));
    Assertions.assertEquals(SearchScale.LOGARITHMIC, scales.get("Min height"));
    Assertions.assertEquals(SearchScale.LINEAR, scales.get("Min consecutive"));
    Assertions.assertEquals(SearchScale.LINEAR, scales.get("Chrom. Threshold"));
  }
}
