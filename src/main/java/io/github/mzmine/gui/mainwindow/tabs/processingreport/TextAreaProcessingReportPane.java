package io.github.mzmine.gui.mainwindow.tabs.processingreport;

import io.github.mzmine.datamodel.PeakList.PeakListAppliedMethod;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.MZmineModule;
import io.github.mzmine.modules.MZmineRunnableModule;
import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.ParameterSet;
import java.util.logging.Logger;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Font;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TextAreaProcessingReportPane extends BorderPane {

  public static final Logger logger = Logger.getLogger(ProcessingReportTab.class.getName());

  protected static final String REPORT_FONT_NAME = "Courier New";
  protected static final int REPORT_FONT_SIZE = 11;

  protected static final int TITLE_FONT_SIZE = 12;

  private final TextArea textArea;
  private final Font font;

  private TextAreaProcessingReportPane() {
    super();

    getStylesheets().addAll(MZmineCore.getDesktop().getMainWindow().getScene().getStylesheets());

    textArea = new TextArea();
    textArea.setEditable(false);
    font = new Font(REPORT_FONT_NAME, REPORT_FONT_SIZE);
    textArea.setFont(font);
    setCenter(textArea);
  }

  public TextAreaProcessingReportPane(Class<? extends MZmineModule> moduleClass,
      ParameterSet parameters) {
    this();
    assert parameters != null;

    MZmineModule inst = MZmineCore.getModuleInstance(moduleClass);
    Label title = new Label(inst.getName());
    title.setFont(new Font(TITLE_FONT_SIZE));
    setTop(title);
    appendParameters(parameters);
  }

  public TextAreaProcessingReportPane(PeakListAppliedMethod plam) {
    this();
//TODO
  }

  private void appendModuleInfo(@Nullable MZmineModule module) {
    if (module == null) {
      appendLine("Module: invalid");
      appendLine("no description");
      appendLine("Module category: not found");
      return;
    }

    appendLine("Module: " + module.getName());
    if (module instanceof MZmineRunnableModule) {
      MZmineRunnableModule runnableModule = (MZmineRunnableModule) module;
      appendLine(runnableModule.getDescription());
      appendLine("Module category: " + runnableModule.getModuleCategory());
    }
  }

  private void appendParameters(@Nonnull ParameterSet parameters) {
    for (Parameter<?> param : parameters.getParameters()) {
      appendLine(param.getName() + ": " + param.getValue());
    }
  }

  public void clear() {
    textArea.clear();
  }

  /**
   * Appends a line with a line break.
   *
   * @param line
   */
  public void appendLine(String line) {
    textArea.setText(textArea.getText() + line + "\n");
  }

  /**
   * Appends a string without a final line break.
   *
   * @param str
   */
  public void appendString(String str) {
    textArea.setText(textArea.getText() + str);
  }

  public String getReportText() {
    return textArea.getText();
  }

  public TextArea getTextArea() {
    return textArea;
  }

  public void setDemoText() {
    appendLine("Module: Isotopic peaks grouper");
    appendLine("Feature list method");
    appendLine("Processed feature list: demo feature list deconvoluted");
    appendLine("");

    appendLine("Processing parameters:");
    appendLine("m/z tolerance: " + 5 + " ppm or " + 0.001 + " m/z");
    appendLine("RT tolerance: " + 0.05 + " min");
    appendLine("Maximum charge state: " + 3);
    appendLine("Monotonic shape: " + true);
    appendLine("");

    appendLine("Total number of processed rows: " + 512);
    appendLine("Detected isotopic features: " + 115);
    appendLine("Remaining features: " + (512 - 115));
  }

//  public void setDemoText2() {
//    appendModuleInfo(MZmineCore.getModuleInstance(IsotopeGrouperModule.class));
//    appendParameters(MZmineCore.getConfiguration().getModuleParameters(IsotopeGrouperModule.class));
//
//    appendLine("Total number of processed rows: " + 512);
//    appendLine("Detected isotopic features: " + 115);
//    appendLine("Remaining features: " + (512 - 115));
//  }
}
