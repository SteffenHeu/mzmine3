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
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.modules.visualization.projectmetadata.table.MetadataTable;
import io.github.mzmine.modules.visualization.projectmetadata.table.columns.MetadataColumn;
import io.github.mzmine.project.ProjectService;
import io.github.mzmine.util.color.ColorUtils;
import io.github.mzmine.util.color.SimpleColorPalette;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Computes the file -> color mapping for the QC dashboard.
 * <ul>
 *   <li>No batch column: each file keeps its own {@link RawDataFile#getColor()}.</li>
 *   <li>Batch column set: one base color per batch group (from the project
 *   {@link SimpleColorPalette}), then per-file shades within a group via
 *   {@link ColorUtils#colorFadeLighter(int, Color, double)} so files within a batch stay
 *   distinguishable.</li>
 * </ul>
 */
public final class QcDashboardColorService {

  /**
   * Brightness range used to fade files within a batch group.
   */
  private static final double SHADE_RANGE = 0.25;

  private QcDashboardColorService() {
  }

  public static @NotNull Map<RawDataFile, Color> computeColors(@NotNull List<RawDataFile> files,
      @Nullable MetadataColumn<?> batchColumn) {
    final Map<RawDataFile, Color> colors = new LinkedHashMap<>();
    if (files.isEmpty()) {
      return colors;
    }

    if (batchColumn == null) {
      for (RawDataFile file : files) {
        colors.put(file, file.getColor());
      }
      return colors;
    }

    // group files by their batch value, preserving acquisition order
    final MetadataTable metadata = ProjectService.getMetadata();
    final Map<Object, List<RawDataFile>> groups = new LinkedHashMap<>();
    for (RawDataFile file : files) {
      final Object value = getGroupValue(metadata, batchColumn, file);
      groups.computeIfAbsent(value, _ -> new java.util.ArrayList<>()).add(file);
    }

    final SimpleColorPalette palette = ConfigService.getDefaultColorPalette().clone(true);
    int groupIndex = 0;
    for (List<RawDataFile> groupFiles : groups.values()) {
      final Color base = palette.getNextColor();
      final List<Color> shades = ColorUtils.colorFadeLighter(groupFiles.size(), base, SHADE_RANGE);
      for (int i = 0; i < groupFiles.size(); i++) {
        // colorFadeLighter returns one entry per step; guard in case of mismatch
        final Color c = i < shades.size() ? shades.get(i) : base;
        colors.put(groupFiles.get(i), c);
      }
      groupIndex++;
    }
    return colors;
  }

  private static @Nullable Object getGroupValue(@NotNull MetadataTable metadata,
      @NotNull MetadataColumn<?> batchColumn, @NotNull RawDataFile file) {
    final Object value = metadata.getValue(batchColumn, file);
    // normalize to a key; null grouped together
    return Objects.toString(value, null);
  }
}
