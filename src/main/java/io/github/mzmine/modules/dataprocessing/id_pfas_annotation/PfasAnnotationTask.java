/*
 * Copyright 2006-2020 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */

package io.github.mzmine.modules.dataprocessing.id_pfas_annotation;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.SimpleFeatureListAppliedMethod;
import io.github.mzmine.datamodel.features.types.annotations.pfasannotation.PfasAnnotationType;
import io.github.mzmine.datamodel.features.types.annotations.pfasannotation.PfasMatchSummaryType;
import io.github.mzmine.datamodel.impl.masslist.SimpleMassList;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser.PfasCompound;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser.PfasFragment;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser.PfasLibraryBuilder;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser.PfasLibraryParser;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.DataPointSorter;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.SortingDirection;
import io.github.mzmine.util.SortingProperty;
import io.github.mzmine.util.scans.ScanUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PfasAnnotationTask extends AbstractTask {

  private static final String PREFIX = "PFAS Annotation Task: ";
  private static final DataPointSorter ascendingMzSorter = new DataPointSorter(SortingProperty.MZ,
      SortingDirection.Ascending);

  private final File libraryFile;
  private final Range<Integer> nRange;
  private final Range<Integer> mRange;
  private final Range<Integer> kRange;
  private final ModularFeatureList[] flists;
  private final ParameterSet parameters;
  private final MZmineProject project;
  private final MZTolerance mzTolerance;
  private final boolean checkPrecursorMz;
  private final double minCoverage;
  private final AtomicInteger processed = new AtomicInteger(0);
  private final boolean removeIsotopes;
  private final boolean removePrecursor;
  private List<PfasCompound> compounds;
  private int totalRows = 1;
  private String description;

  protected PfasAnnotationTask(@Nullable MemoryMapStorage storage, MZmineProject project,
      @NotNull final ParameterSet parameterSet) {
    super(storage);

    flists = parameterSet.getParameter(PfasAnnotationParameters.featureLists).getValue()
        .getMatchingFeatureLists();
    libraryFile = parameterSet.getParameter(PfasAnnotationParameters.databaseFile).getValue();
    nRange = parameterSet.getParameter(PfasAnnotationParameters.nRange).getValue();
    mRange = parameterSet.getParameter(PfasAnnotationParameters.mRange).getValue();
    kRange = parameterSet.getParameter(PfasAnnotationParameters.kRange).getValue();
    checkPrecursorMz = parameterSet.getParameter(PfasAnnotationParameters.checkPrecursorMz)
        .getValue();
    mzTolerance = parameterSet.getParameter(PfasAnnotationParameters.checkPrecursorMz)
        .getEmbeddedParameter().getValue();
    minCoverage = parameterSet.getParameter(PfasAnnotationParameters.minimumCoverage).getValue();
    removePrecursor = parameterSet.getParameter(PfasAnnotationParameters.removePrecursor)
        .getValue();
    removeIsotopes = parameterSet.getParameter(PfasAnnotationParameters.removeIsotopes).getValue();

    parameters = parameterSet;
    this.project = project;
    description = PREFIX + "Waiting...";
  }

  @Override
  public String getTaskDescription() {
    return description;
  }

  @Override
  public double getFinishedPercentage() {
    return processed.get() / (double) totalRows;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);

    description = PREFIX + "Building compound library...";

    compounds = buildPfasLibrary();
    if (compounds == null) {
      setStatus(TaskStatus.ERROR);
      return;
    }

    totalRows = Arrays.stream(flists).mapToInt(ModularFeatureList::getNumberOfRows).sum();
    description = PREFIX + "Annotating row " + processed + "/" + totalRows;

    for (var flist : flists) {
      final List<ModularFeatureListRow> rows = flist.modularStream()
          .filter(row -> !row.getAllFragmentScans().isEmpty()).toList();

      if (!flist.getRowTypes().containsKey(PfasAnnotationType.class)) {
        flist.addRowType(new PfasAnnotationType());
      }

      rows.parallelStream().filter(row -> row.getMostIntenseFragmentScan() != null).forEach(row -> {

        final Scan msms = row.getMostIntenseFragmentScan();
        final double[][] processedDp = ScanUtils
            .deisotopeAndRemovePrecursorIons(msms, mzTolerance, msms.getPrecursorMZ(),
                removePrecursor, removeIsotopes);
        final MassSpectrum processedMSMS = new SimpleMassList(null, processedDp[0], processedDp[1]);

        for (final PfasCompound comp : compounds) {
          if (checkPrecursorMz && !mzTolerance
              .checkWithinTolerance(comp.getPrecursorMz(msms.getPolarity()), row.getAverageMZ())) {
            continue;
          }

          final List<PfasFragment> matchedFragments = new ArrayList<>();
          final double coverage = IntensityCoverageUtils
              .getIntensityCoverage(processedMSMS, comp.getIonMzs(msms.getPolarity()), mzTolerance,
                  comp.getObservedIons(msms.getPolarity()), matchedFragments);

          /*if ((bestMatch == null && coverage >= minCoverage) || (bestMatch != null
              && coverage > bestMatch.getCoverageScore())) {
            bestMatch = new PfasMatch(row, comp, coverage, matchedFragments);
          }*/

          if (coverage >= minCoverage) {
            row.get(PfasAnnotationType.class).get(PfasMatchSummaryType.class)
                .add(new PfasMatch(row, comp, coverage, matchedFragments));
          }
        }

        // sort results
        if (row.get(PfasAnnotationType.class).get(PfasMatchSummaryType.class) != null) {
          row.get(PfasAnnotationType.class).get(PfasMatchSummaryType.class)
              .sort((a1, a2) -> Double.compare(a1.getCoverageScore(), a2.getCoverageScore() * -1));
        }

        processed.getAndIncrement();
        description = PREFIX + "Annotating row " + processed + "/" + totalRows;
      });

      flist.getAppliedMethods()
          .add(new SimpleFeatureListAppliedMethod(PfasAnnotationModule.class, parameters));
    }

    setStatus(TaskStatus.FINISHED);
  }

  private List<PfasCompound> buildPfasLibrary() {
    final PfasLibraryParser parser = new PfasLibraryParser();

    try {
      if (!parser.read(libraryFile)) {
        setErrorMessage("Cannot read library file.");
        return null;
      }
    } catch (IllegalStateException e) {
      setErrorMessage(e.getMessage());
      return null;
    }

    final PfasLibraryBuilder builder = new PfasLibraryBuilder(parser.getEntries(), nRange, mRange,
        kRange);

    builder.buildLibrary();

    return builder.getLibrary();
  }
}
