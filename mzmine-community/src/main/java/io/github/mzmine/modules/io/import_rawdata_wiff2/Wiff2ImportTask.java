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

package io.github.mzmine.modules.io.import_rawdata_wiff2;

import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.RawDataImportTask;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.otherdetectors.OtherDataFile;
import io.github.mzmine.modules.io.import_rawdata_all.AllSpectralDataImportParameters;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.Experiment;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.Sample;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.Spectrum;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.project.impl.RawDataFileImpl;
import io.github.mzmine.taskcontrol.AbstractRawDataFileTask;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.StringUtils;
import io.github.mzmine.util.date.LocalDateTimeParser;
import io.github.mzmine.util.files.FileAndPathUtil;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Wiff2ImportTask extends AbstractRawDataFileTask implements RawDataImportTask {

  private static final Logger logger = Logger.getLogger(Wiff2ImportTask.class.getName());

  private final File file;
  private final MZmineProject project;
  private final List<RawDataFile> files = new ArrayList<>();
  private double progress = 0;

  public Wiff2ImportTask(@Nullable MemoryMapStorage storage, @NotNull Instant moduleCallDate,
      final File file, ParameterSet parameters, MZmineProject project) {
    super(storage, moduleCallDate, parameters, Wiff2ImportModule.class);
    this.file = file;
    this.project = project;
  }

  @Override
  public @Nullable RawDataFile getImportedRawDataFile() {
    return files.getFirst();
  }

  @Override
  public String getTaskDescription() {
    return "Importing file " + file;
  }

  @Override
  public double getFinishedPercentage() {
    return progress;
  }

  @Override
  protected void process() {

    try (Wiff2DataAccess access = new Wiff2DataAccess(file,
        parameters.getValue(AllSpectralDataImportParameters.applyVendorCentroiding))) {

      final List<Sample> samples = access.getSamples();

      for (Sample sample : samples) {
        final int sampleIndex = samples.indexOf(sample);
        final double sampleProgress = (double) sampleIndex / samples.size();

        final RawDataFileImpl rawDataFile = new RawDataFileImpl(getDataFileName(sample, samples),
            file.getAbsolutePath(), getMemoryMapStorage());

        final List<Scan> scans = new ArrayList<>();
        final String startTimestamp = sample.getStartTimestamp();
        rawDataFile.setStartTimeStamp(LocalDateTimeParser.parseAnyFirstDate(startTimestamp));

        final List<Experiment> experiments = access.getExperiments(sample);
        for (Experiment experiment : experiments) {

          final Iterator<Spectrum> spectra = access.getSpectrumIterator(sample, experiment);
          while (spectra.hasNext()) {
            final Spectrum spectrum = spectra.next();
            final Scan scan = access.spectrumToMzmineScan(rawDataFile, sample, experiment,
                spectrum);
            scans.add(scan);
          }

          final int experimentIndex = experiments.indexOf(experiment);
          final double experimentProgress = (double) experimentIndex / experiments.size();
          progress = sampleProgress + (1d / samples.size()) * experimentProgress;
        }

        scans.sort(Scan::compareTo);
        scans.forEach(rawDataFile::addScan);

        final List<@NotNull OtherDataFile> otherDataFiles = access.getAnalogTraces(sample,
            rawDataFile);
        rawDataFile.addOtherDataFiles(otherDataFiles);

        files.add(rawDataFile);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    files.forEach(project::addFile);
  }

  private @NotNull String getDataFileName(Sample sample, List<Sample> samples) {
    String sampleName = sample.getSampleName();
    String userSampleID = sample.getUserSampleId();
    String filename = FileAndPathUtil.eraseFormat(file.getName());

    if(samples.size() <= 1) {
      return filename;
    }

    StringBuilder b = new StringBuilder();
    b.append(filename).append("_").append(sampleName);
    if(!StringUtils.isBlank(userSampleID)) {
      b.append("_").append(userSampleID);
    }

    return b.append(".wiff2").toString();
  }

  @Override
  protected @NotNull List<RawDataFile> getProcessedDataFiles() {
    return List.of(getImportedRawDataFile());
  }
}
