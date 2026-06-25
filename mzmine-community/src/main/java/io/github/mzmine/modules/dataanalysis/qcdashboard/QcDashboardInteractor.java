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

package io.github.mzmine.modules.dataanalysis.qcdashboard;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.javafx.mvci.FxInteractor;
import io.github.mzmine.modules.visualization.projectmetadata.SampleType;
import io.github.mzmine.modules.visualization.projectmetadata.table.MetadataTable;
import io.github.mzmine.modules.visualization.projectmetadata.table.columns.MetadataColumn;
import io.github.mzmine.project.ProjectService;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Derives the read-only dashboard state ({@code orderedFiles}, {@code qcFiles}, {@code fileColors})
 * from the inputs (feature list, sample types to show, batch column). Runs on the FX thread; the
 * file-level iteration here is cheap.
 */
public class QcDashboardInteractor extends FxInteractor<QcDashboardModel> {

  public QcDashboardInteractor(QcDashboardModel model) {
    super(model);
  }

  @Override
  public void updateModel() {
    final FeatureList flist = firstAligned();
    model.setFeatureList(
        flist instanceof io.github.mzmine.datamodel.features.ModularFeatureList mfl ? mfl : null);
    if (flist == null) {
      model.setOrderedFiles(List.of());
      model.setQcFiles(List.of());
      model.setFileColors(java.util.Map.of());
      return;
    }

    final MetadataTable metadata = ProjectService.getMetadata();
    final MetadataColumn<String> sampleTypeColumn = metadata.getSampleTypeColumn();
    final Set<String> typesToShow = Set.copyOf(model.getSampleTypesToShow());

    // filter to selected sample types and sort by acquisition date (nulls last, then name)
    final List<RawDataFile> ordered = flist.getRawDataFiles().stream()
        .filter(f -> typesToShow.isEmpty() || typesToShow.contains(sampleTypeOf(metadata,
            sampleTypeColumn, f))).sorted(acquisitionOrder()).toList();

    final List<RawDataFile> qcFiles = ordered.stream()
        .filter(f -> SampleType.QC.toString().equals(sampleTypeOf(metadata, sampleTypeColumn, f)))
        .toList();

    model.setOrderedFiles(ordered);
    model.setQcFiles(qcFiles);
    model.setFileColors(QcDashboardColorService.computeColors(ordered, model.getBatchColumn()));
  }

  private @org.jetbrains.annotations.Nullable FeatureList firstAligned() {
    return model.getFlists().stream().filter(FeatureList::isAligned).findFirst().orElse(null);
  }

  private static @NotNull String sampleTypeOf(@NotNull MetadataTable metadata,
      @NotNull MetadataColumn<String> sampleTypeColumn, @NotNull RawDataFile file) {
    final String value = metadata.getValue(sampleTypeColumn, file);
    return value != null ? value : SampleType.ofFile(file).toString();
  }

  private static @NotNull Comparator<RawDataFile> acquisitionOrder() {
    return Comparator.comparing((RawDataFile f) -> f.getStartTimeStamp(),
            Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder()))
        .thenComparing(RawDataFile::getName);
  }
}
