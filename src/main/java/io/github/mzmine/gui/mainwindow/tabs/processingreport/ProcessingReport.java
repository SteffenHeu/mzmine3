package io.github.mzmine.gui.mainwindow.tabs.processingreport;

import io.github.mzmine.modules.MZmineProcessingModule;
import io.github.mzmine.parameters.ParameterSet;
import java.util.ArrayList;
import java.util.List;

/**
 * @author SteffenHeu https://github.com/SteffenHeu / steffen.heuckeroth@uni-muenster.de
 */
public class ProcessingReport {

  protected final ParameterSet parameterSet;
  protected final MZmineProcessingModule module;

  protected List<String> summary;

  public ProcessingReport(ParameterSet parameterSet,
      MZmineProcessingModule module) {
    this.parameterSet = parameterSet;
    this.module = module;

    summary = new ArrayList<>();
  }

  public ParameterSet getParameterSet() {
    return parameterSet;
  }

  public MZmineProcessingModule getModule() {
    return module;
  }

  /**
   * Adds a new line to the summary part of the processing report.
   *
   * @param newLine
   */
  public void appedLine(String newLine) {
    summary.add(newLine);
  }


}
