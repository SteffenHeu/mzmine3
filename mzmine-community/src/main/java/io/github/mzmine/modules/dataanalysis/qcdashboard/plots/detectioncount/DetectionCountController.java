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

package io.github.mzmine.modules.dataanalysis.qcdashboard.plots.detectioncount;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.javafx.mvci.FxController;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import java.util.List;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Controller of the detection-count plot (Plot 4). Recomputes off the FX thread via
 * {@link DetectionCountUpdateTask} when the feature list or QC files change.
 */
public class DetectionCountController extends FxController<DetectionCountModel> {

  private final DetectionCountViewBuilder builder;

  public DetectionCountController() {
    super(new DetectionCountModel());
    builder = new DetectionCountViewBuilder(model);

    model.featureListProperty().addListener((_, _, _) -> scheduleUpdate());
    model.qcFilesProperty().addListener((_, _, _) -> scheduleUpdate());
  }

  private void scheduleUpdate() {
    onTaskThreadDelayed(new DetectionCountUpdateTask(model));
  }

  @Override
  protected @NotNull FxViewBuilder<DetectionCountModel> getViewBuilder() {
    return builder;
  }

  public ObjectProperty<@Nullable ModularFeatureList> featureListProperty() {
    return model.featureListProperty();
  }

  public ObjectProperty<List<RawDataFile>> qcFilesProperty() {
    return model.qcFilesProperty();
  }

  public DoubleProperty goodQualityFractionProperty() {
    return model.goodQualityFractionProperty();
  }

  public DoubleProperty warwickFractionProperty() {
    return model.warwickFractionProperty();
  }
}
