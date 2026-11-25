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

package io.github.mzmine.datamodel.features.compoundannotations.pfas;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.MassSpectrumType;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.compoundannotations.FeatureAnnotation;
import io.github.mzmine.datamodel.features.types.annotations.CompoundNameType;
import io.github.mzmine.datamodel.features.types.annotations.formula.FormulaType;
import io.github.mzmine.datamodel.features.types.annotations.pfas.PfasBuildingBlocksType;
import io.github.mzmine.datamodel.features.types.annotations.pfas.PfasCandidateSourceType;
import io.github.mzmine.datamodel.features.types.annotations.pfas.PfasClassType;
import io.github.mzmine.datamodel.features.types.annotations.pfas.PfasConfidenceLevelType;
import io.github.mzmine.datamodel.features.types.numbers.PrecursorMZType;
import io.github.mzmine.datamodel.impl.DDAMsMsInfoImpl;
import io.github.mzmine.datamodel.impl.SimpleScan;
import io.github.mzmine.project.impl.MZmineProjectImpl;
import io.github.mzmine.project.impl.RawDataFileImpl;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * PFAS annotations are created by mzmine pro, but saved and loaded by mzmine community. Loading
 * through the generic {@link FeatureAnnotation} loader keeps e.g. a manually preferred PFAS
 * annotation.
 */
class PfasAnnotationXmlTest {

  private final RawDataFileImpl raw = new RawDataFileImpl("pfas", null, null);
  private final ModularFeatureList flist = new ModularFeatureList("pfas", null, raw);
  private final ModularFeatureListRow row = new ModularFeatureListRow(flist, 1);

  /**
   * PFOS at level 3b with every persisted field set, built without the pro annotation logic.
   */
  private PfasAnnotation pfos() {
    final double[] mzs = {79.9574, 98.9558, 418.9734};
    final double[] intensities = {100, 50, 25};
    final SimpleScan scan = new SimpleScan(raw, 1, 2, 1f, new DDAMsMsInfoImpl(498.9302, -1, 2), mzs,
        intensities, MassSpectrumType.CENTROIDED, PolarityType.NEGATIVE, "",
        Range.closed(50d, 600d));
    raw.addScan(scan);
    final PfasAnnotation annotation = new PfasAnnotation();
    annotation.setScan(scan);
    annotation.setMatchedPeaks(List.of(
        new PfasMatchedPeak(79.9574, 100, 79.9574, "O3S", -1, PfasEvidenceLevel.DIAGNOSTIC,
            List.of("sulfonate.so3"), "C8F17"),
        new PfasMatchedPeak(98.9558, 50, 98.9558, "FO3S", -1, PfasEvidenceLevel.DIAGNOSTIC,
            List.of("sulfonate.fso3"), null)));
    annotation.setInSourceForms(
        List.of(new PfasInSourceForm("C8F17", -1, 418.9734, "O3S", "test.isf")));
    annotation.setCandidateIdentity("PFSA", List.of(8), List.of(0));
    annotation.put(CompoundNameType.class, "PFOS");
    annotation.put(FormulaType.class, "C8HF17O3S");
    annotation.put(PrecursorMZType.class, 498.9302);
    annotation.put(PfasConfidenceLevelType.class, PfasConfidenceLevel.LEVEL_3B);
    annotation.put(PfasCandidateSourceType.class, PfasCandidateSource.KNOWN);
    annotation.put(PfasClassType.class, "Perfluoroalkyl sulfonic acids");
    annotation.put(PfasBuildingBlocksType.class,
        List.of(new PfasBlockEvidence("Sulfonic acid", "HO3S", "[*:2]S(=O)(=O)O", List.of(0, 1))));
    annotation.getFlags().add(PfasRefinementFlag.SERIES_RT_CONSISTENT);
    annotation.setRefinedLevel(PfasConfidenceLevel.LEVEL_3D);
    return annotation;
  }

  @Test
  void genericAnnotationLoaderRestoresAllFields() {
    final PfasAnnotation annotation = pfos();
    final PfasAnnotation inSource = PfasAnnotation.createInSource(annotation, 7,
        annotation.getInSourceForms().getFirst(), PfasInSourceEvidence.COMPOUND_MEMBER, 418.9740);
    final MZmineProjectImpl project = new MZmineProjectImpl();
    project.addFile(raw);

    // toXMLString writes no root element, so one annotation per string
    for (final PfasAnnotation original : List.of(annotation, inSource)) {
      final String xml = FeatureAnnotation.toXMLString(List.of(original), flist, row);
      Assertions.assertNotNull(xml);
      Assertions.assertEquals(List.of(original),
          FeatureAnnotation.parseFromXMLString(xml, project, flist, row));
    }
    final PfasAnnotation pfos = (PfasAnnotation) FeatureAnnotation.parseFromXMLString(
            FeatureAnnotation.toXMLString(List.of(annotation), flist, row), project, flist, row)
        .getFirst();
    Assertions.assertEquals(PfasConfidenceLevel.LEVEL_3D, pfos.getConfidenceLevel());
    Assertions.assertEquals(List.of(8), pfos.getChainLengths());
    Assertions.assertEquals(1, pfos.getBuildingBlocks().size());
    Assertions.assertEquals(annotation.getMatchedPeaks(), pfos.getMatchedPeaks());
    Assertions.assertEquals("C8F17", pfos.getMatchedPeaks().getFirst().lossFormula());
    // the refinement restores the original level after loading
    pfos.resetRefinement();
    Assertions.assertEquals(PfasConfidenceLevel.LEVEL_3B, pfos.getConfidenceLevel());
  }
}
