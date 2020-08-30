package io.github.mzmine.gui.mainwindow.tabs.processingreport;

import io.github.mzmine.datamodel.PeakList;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.MZmineModule;
import io.github.mzmine.modules.MZmineModuleCategory;
import io.github.mzmine.modules.MZmineProcessingModule;
import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.PeakListsParameter;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesParameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Report of a single module call.
 *
 * @author SteffenHeu https://github.com/SteffenHeu / steffen.heuckeroth@uni-muenster.de
 */
public class ProcessingStepReport {

  protected final ParameterSet parameterSet;
  protected final MZmineModule module;
  protected final String dateCreated;

  protected final List<RawDataFile> rawDataFiles;
  protected final List<PeakList> featureLists;

  protected final List<String> summary;

  public ProcessingStepReport(final ParameterSet parameterSet,
      final MZmineProcessingModule module) {
    this.parameterSet = parameterSet;
    this.module = module;

    dateCreated = MZmineCore.getConfiguration().getDateFormat().format(new Date());

    summary = new ArrayList<>();

    List<RawDataFile> rawDataFilesTemp = null;
    List<PeakList> featureListsTemp = null;

    // assign feature lists / raw data files from parameters
    if (getModule() instanceof MZmineProcessingModule) {
      MZmineProcessingModule procModule = (MZmineProcessingModule) getModule();

      if (MZmineModuleCategory.isRawDataProcessingModule(procModule.getModuleCategory())) {
        for (Parameter<?> parameter : parameterSet.getParameters()) {
          if (parameter instanceof RawDataFilesParameter) {
            RawDataFilesParameter rfp = (RawDataFilesParameter) parameter;
            if (rfp.getValue() != null && rfp.getValue().getMatchingRawDataFiles() != null) {
              rawDataFilesTemp = Arrays.asList(rfp.getValue().getMatchingRawDataFiles());
              break;
            }
          }
        }
      } else if (MZmineModuleCategory
          .isFeatureListProcessingModule(procModule.getModuleCategory())) {
        for (Parameter<?> parameter : parameterSet.getParameters()) {
          if (parameter instanceof PeakListsParameter) {
            PeakListsParameter plp = (PeakListsParameter) parameter;
            if (plp.getValue() != null && plp.getValue().getMatchingPeakLists() != null) {
              featureListsTemp = Arrays.asList(plp.getValue().getMatchingPeakLists());
              break;
            }
          }
        }
      }

      if (rawDataFilesTemp == null && featureListsTemp == null) {
        appendLine("Error while assigning raw data files or feature lists: ");
        appendLine("Module category is " + procModule.getModuleCategory()
            + " but no files/lists specified.");
        appendLine(
            "This might lead to unexpected behaviour when creating batch lists. Please contact the developers.");
      }
    }

    rawDataFiles = (rawDataFilesTemp == null) ? Collections.emptyList() : rawDataFilesTemp;
    featureLists = (featureListsTemp == null) ? Collections.emptyList() : featureListsTemp;
  }

  public ParameterSet getParameterSet() {
    return parameterSet;
  }

  public MZmineModule getModule() {
    return module;
  }

  /**
   * Adds a new line to the summary part of the processing report.
   *
   * @param newLine the line of text
   */
  public void appendLine(final String newLine) {
    summary.add(newLine);
  }

  public String getDateCreated() {
    return dateCreated;
  }

  public List<PeakList> getFeatureLists() {
    return featureLists;
  }

  public List<RawDataFile> getRawDataFiles() {
    return rawDataFiles;
  }

  public List<String> getSummary() {
    return summary;
  }
}
