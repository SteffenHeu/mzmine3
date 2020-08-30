package io.github.mzmine.gui.mainwindow.tabs.processingreport;

import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.MZmineModule;
import io.github.mzmine.modules.MZmineProcessingModule;
import io.github.mzmine.parameters.ParameterSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author SteffenHeu https://github.com/SteffenHeu / steffen.heuckeroth@uni-muenster.de
 */
public class ProcessingReport {

  protected final ParameterSet parameterSet;
  protected final MZmineModule module;
  protected final String dateCreated;

  protected final List<String> summary;

  public ProcessingReport(final ParameterSet parameterSet,
      final MZmineProcessingModule module) {
    this.parameterSet = parameterSet;
    this.module = module;

    dateCreated = MZmineCore.getConfiguration().getDateFormat().format(new Date());

    summary = new ArrayList<>();
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
}
