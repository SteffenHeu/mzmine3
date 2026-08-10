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

package io.github.mzmine.modules.dataprocessing.featdet_masscalibration2.api;

import io.github.mzmine.datamodel.SimpleRange.SimpleDoubleRange;
import io.github.mzmine.datamodel.features.compoundannotations.CompoundDBAnnotation;
import io.github.mzmine.datamodel.features.types.numbers.PrecursorMZType;
import io.github.mzmine.datamodel.features.types.numbers.RTType;
import io.github.mzmine.parameters.parametertypes.ImportType;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.ArrayUtils;
import io.github.mzmine.util.CSVParsingUtils;
import io.github.mzmine.util.CSVParsingUtils.CompoundDbLoadResult;
import io.github.mzmine.util.files.FileAndPathUtil;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A list of calibrant ions (expected m/z, optional retention time) used by the calibration methods
 * to match measured peaks. Read from a CSV/TSV via {@link CSVParsingUtils} using the default
 * {@code mz} / {@code rt} column names (no import-type parameter is exposed).
 * <p>
 * Matching follows the legacy exactly-one rule: a measured peak is only accepted if a single
 * calibrant falls within the m/z (and optional RT) tolerance, so ambiguous peaks are skipped.
 */
public class CalibrantList {

  private static final Logger logger = Logger.getLogger(CalibrantList.class.getName());

  /**
   * Default columns: m/z (required) and optional retention time.
   */
  private static final List<ImportType<?>> DEFAULT_IMPORT_TYPES = List.of(
      new ImportType<>(true, "mz", new PrecursorMZType()),
      new ImportType<>(false, "rt", new RTType()));

  private final double[] mz;
  private final float[] rt; // parallel to mz; -1 when the calibrant has no retention time

  public CalibrantList(double[] mz, float[] rt) {
    if (mz.length != rt.length) {
      throw new IllegalArgumentException("mz and rt arrays must have the same length");
    }
    double[][] calibrants = IntStream.range(0, mz.length).mapToObj(i -> new double[]{mz[i], rt[i]})
        .sorted(Comparator.comparingDouble(a -> a[0])).toArray(double[][]::new);

    this.mz = Arrays.stream(calibrants).mapToDouble(a -> a[0]).toArray();
    this.rt = ArrayUtils.doubleToFloat(Arrays.stream(calibrants).mapToDouble(a -> a[1]).toArray());
  }

  /**
   * Read a calibrant list from a CSV/TSV file. The separator is auto-detected and the default
   * {@code mz}/{@code rt} columns are used.
   *
   * @return the calibrant list, or {@code null} (after logging) if the file could not be read
   */
  public static @Nullable CalibrantList fromCsv(File file) {
    final Character sep = CSVParsingUtils.autoDetermineSeparatorDefaultFallback(file);
    final CompoundDbLoadResult result = CSVParsingUtils.getAnnotationsFromCsvFile(file,
        sep.toString(), DEFAULT_IMPORT_TYPES, null);
    if (result.status() == TaskStatus.ERROR) {
      logger.warning("Could not read calibrant list: " + result.errorMessage());
      return null;
    }

    final List<double[]> rows = new ArrayList<>(); // [mz, rt]
    for (CompoundDBAnnotation annotation : result.annotations()) {
      final Double m = annotation.getPrecursorMZ();
      if (m == null) {
        continue;
      }
      final Float r = annotation.getRT();
      rows.add(new double[]{m, r != null ? r : -1f});
    }
    // sort by ascending mz
    rows.sort(Comparator.comparingDouble(a -> a[0]));

    final double[] mz = new double[rows.size()];
    final float[] rt = new float[rows.size()];
    for (int i = 0; i < rows.size(); i++) {
      mz[i] = rows.get(i)[0];
      rt[i] = (float) rows.get(i)[1];
    }
    return new CalibrantList(mz, rt);
  }

  /**
   * Load a calibrant list from a {@link CalibrantListSource}: either the bundled resource or, for
   * {@link CalibrantListSource#CUSTOM_FILE}, the given custom file.
   *
   * @param source     the selected source
   * @param customFile the custom file (used only when {@code source == CUSTOM_FILE})
   * @return the calibrant list, or {@code null} (after logging) if it could not be read
   */
  public static @Nullable CalibrantList fromSource(@NotNull CalibrantListSource source,
      @Nullable File customFile) {
    if (source == CalibrantListSource.CUSTOM_FILE) {
      if (customFile == null) {
        logger.warning("No custom calibrant file selected.");
        return null;
      }
      return fromCsv(customFile);
    }
    return switch (source.format()) {
      case MZ_TABLE -> fromResource(source.resourcePath());
      case UNIVERSAL_CALIBRANTS -> fromUniversalCalibrantResource(source.resourcePath());
    };
  }

  /**
   * Read a bundled calibrant list from a classpath resource. The resource is copied to a temporary
   * file so the same {@link CSVParsingUtils} path as {@link #fromCsv(File)} is used.
   *
   * @return the calibrant list, or {@code null} (after logging) if the resource is
   * missing/unreadable
   */
  public static @Nullable CalibrantList fromResource(String resourcePath) {
    try (InputStream is = CalibrantList.class.getClassLoader().getResourceAsStream(resourcePath)) {
      if (is == null) {
        logger.warning("Bundled calibrant list not found: " + resourcePath);
        return null;
      }
      final File tmp = FileAndPathUtil.createTempFile("mzcalibrants", ".csv");
      tmp.deleteOnExit();
      Files.copy(is, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
      return fromCsv(tmp);
    } catch (IOException e) {
      logger.warning(
          "Could not read bundled calibrant list %s: %s".formatted(resourcePath, e.getMessage()));
      return null;
    }
  }

  /**
   * Read a bundled universal-calibrant list (the old module's format) from a classpath resource.
   * The first column holds the monoisotopic m/z; there is no retention time (RT-agnostic
   * matching).
   *
   * @return the calibrant list, or {@code null} (after logging) if the resource is
   * missing/unreadable
   */
  public static @Nullable CalibrantList fromUniversalCalibrantResource(String resourcePath) {
    try (InputStream is = CalibrantList.class.getClassLoader().getResourceAsStream(resourcePath)) {
      if (is == null) {
        logger.warning("Bundled calibrant list not found: " + resourcePath);
        return null;
      }
      final List<String[]> data;
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(is, StandardCharsets.UTF_8))) {
        data = CSVParsingUtils.readData(reader, ",");
      }
      if (data.size() < 2) {
        logger.warning("Bundled calibrant list is empty: " + resourcePath);
        return null;
      }
      final int mzCol = findMassColumn(data.getFirst());
      final DoubleArrayList mzValues = new DoubleArrayList();
      for (int i = 1; i < data.size(); i++) {
        final String[] row = data.get(i);
        if (mzCol >= row.length) {
          continue;
        }
        try {
          mzValues.add(Double.parseDouble(row[mzCol].trim()));
        } catch (NumberFormatException ignored) {
          // skip non-numeric rows
        }
      }
      final double[] mz = mzValues.toDoubleArray();
      Arrays.sort(mz);
      final float[] rt = new float[mz.length];
      Arrays.fill(rt, -1f); // universal calibrants have no retention time
      return new CalibrantList(mz, rt);
    } catch (Exception e) {
      logger.warning(
          "Could not read bundled calibrant list %s: %s".formatted(resourcePath, e.getMessage()));
      return null;
    }
  }

  /**
   * @return the index of the column holding the m/z (header contains "mass"), or 0 as a fallback.
   */
  private static int findMassColumn(String[] header) {
    for (int i = 0; i < header.length; i++) {
      if (header[i] != null && header[i].toLowerCase().contains("mass")) {
        return i;
      }
    }
    return 0;
  }

  public int size() {
    return mz.length;
  }

  public boolean isEmpty() {
    return mz.length == 0;
  }

  public SimpleDoubleRange getCalibrantsMzRange() {
    return new SimpleDoubleRange(mz[0], mz[mz.length - 1]);
  }

  public double getMz(int index) {
    return mz[index];
  }

  public float getRt(int index) {
    return rt[index];
  }
}
