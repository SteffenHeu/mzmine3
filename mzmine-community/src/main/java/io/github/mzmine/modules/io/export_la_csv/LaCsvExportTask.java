/*
 * Copyright (c) 2004-2022 The MZmine Development Team
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

package io.github.mzmine.modules.io.export_la_csv;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.featuredata.IonTimeSeries;
import io.github.mzmine.datamodel.featuredata.IonTimeSeriesUtils;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.NotNull;

public class LaCsvExportTask extends AbstractTask {

  //  Logger
  private static final Logger logger = Logger.getLogger(LaCsvExportTask.class.getName());

  //  Parameters
  private final FeatureList featureList;
  private final ArrayList<Integer> ids;
  private final File file;

  //Task Progress
  private int numRawDataFiles;
  private int numProcessedRawDataFiles;

  public LaCsvExportTask(ParameterSet parameters, @NotNull Instant moduleCallDate) {
    super(moduleCallDate);

    featureList = parameters.getValue(LaCsvExportParameters.featureList)
        .getMatchingFeatureLists()[0];
    file = parameters.getValue(LaCsvExportParameters.path).getAbsoluteFile();

    String stringOfIds = parameters.getValue(LaCsvExportParameters.features);
    ids = new ArrayList<>();

    Pattern pattern = Pattern.compile("\\d+");
    Matcher matcher = pattern.matcher(stringOfIds);

    while (matcher.find()) {
      try {
        ids.add(Integer.parseInt(matcher.group()));
      } catch (NumberFormatException e) {
        e.printStackTrace();
        logger.log(Level.WARNING, e.getMessage());
      }
    }
  }

  @Override
  public String getTaskDescription() {
    return "Export Features to CSV for LA-Data";
  }

  @Override
  public double getFinishedPercentage() {
    if (numRawDataFiles > 0) {
      return (double) numProcessedRawDataFiles / (double) numRawDataFiles;
    } else {
      return 0;
    }
  }

  @Override
  public void run() {

    setStatus(TaskStatus.PROCESSING);

    //Task progress variables
    numRawDataFiles = featureList.getRawDataFiles().size();
    numProcessedRawDataFiles = 0;

    //Create directory for output files
    Path filePath = Paths.get(FilenameUtils.removeExtension(file.getAbsolutePath()));
    try {
      Files.createDirectory(filePath);
    } catch (IOException e) {
      e.printStackTrace();
      logger.log(Level.WARNING, e.getMessage());
    }

    for (int j = 0; j < numRawDataFiles; j++) {

//      Raw data file
      RawDataFile file = featureList.getRawDataFile(j);

      //Lists for export data to be stored in
      List<Feature> features = new ArrayList<>();
      List<IonTimeSeries<Scan>> eics = new ArrayList<>();

//      Extracting all wanted features from the feature list for the current raw data file.
      for (Integer id : ids) {
        features.add(featureList.findRowByID(id).getFeature(file));
      }

      //Extracting EICs from selected features
      for (int i = 0; i < features.size(); i++) {

        Feature feature = features.get(i);

        if (feature != null) {
          List<Scan> scansMs1 = Arrays.asList(ScanSelection.MS1.getMatchingScans(file));
          IonTimeSeries<Scan> eic = IonTimeSeriesUtils.remapRtAxis(feature.getFeatureData(),
              scansMs1);
          eics.add(eic);
        } else {
          eics.add(null);
        }
      }

      //Number of scans for formatting csv file.
      List<Scan> ms1Scans = Arrays.asList(ScanSelection.MS1.getMatchingScans(file));
      int numScans = ScanSelection.MS1.getMatchingScans(file).length;

      try {

        //Writing file
        String rawDataFileName = file.getFileName();
        FileWriter writer = new FileWriter(filePath + File.separator + rawDataFileName + ".csv");

        //CSV file header
        writer.write("rt");
        writer.append(";");

        for (Integer id : ids) {

          double mz = featureList.findRowByID(id).getAverageMZ();
          DecimalFormat decimalFormat = new DecimalFormat("0.0000");

          writer.write(decimalFormat.format(mz) + "(ID" + id + ")");
          writer.append(";");
        }
        writer.append("\n");

        //Writing EIC data
        for (int i = 0; i < numScans; i++) {

          //RT for row
          float rt = ms1Scans.get(i).getRetentionTime();
          writer.write(String.valueOf(rt));
          writer.append(";");

          //Intensities for features
          for (int k = 0; k < features.size(); k++) {

            Feature feature = features.get(k);

            //Writing 0 if feature is not present in raw data file
            if (feature == null) {
              writer.write("0.0");
              writer.append(";");
            }
            //Writing intensity value otherwise
            else {
              double intensity = eics.get(k).getIntensity(i);
              writer.write(String.valueOf(intensity));
              writer.append(";");
            }
          }
          writer.append("\n");
        }

        writer.flush();
        writer.close();
      }

      //IOException des FileWriter.
      catch (IOException e) {
        e.printStackTrace();
        logger.log(Level.WARNING, e.getMessage());
      }

      //Task progress
      numProcessedRawDataFiles++;
    }

    setStatus(TaskStatus.FINISHED);
  }
}

