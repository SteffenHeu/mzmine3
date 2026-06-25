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

package io.github.mzmine.modules.dataanalysis.qcdashboard.plots.perfile;

import io.github.mzmine.datamodel.AbundanceMeasure;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.gui.framework.fx.SelectedAbundanceMeasureBinding;
import io.github.mzmine.javafx.mvci.FxController;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import java.util.List;
import java.util.Map;
import javafx.beans.property.ObjectProperty;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Controller of a per-file aggregate plot (feature count or summed intensity, see
 * {@link FileAggregateKind}). Recomputes off the FX thread via {@link PerFileAggregateUpdateTask}
 * whenever the feature list, files, colors or abundance change.
 */
public class PerFileAggregateController extends FxController<PerFileAggregateModel> implements
    SelectedAbundanceMeasureBinding {

  private final PerFileAggregateViewBuilder builder;

  public PerFileAggregateController(FileAggregateKind kind) {
    super(new PerFileAggregateModel(kind));
    builder = new PerFileAggregateViewBuilder(model);

    model.featureListProperty().addListener((_, _, _) -> scheduleUpdate());
    model.orderedFilesProperty().addListener((_, _, _) -> scheduleUpdate());
    model.fileColorsProperty().addListener((_, _, _) -> scheduleUpdate());
    model.abundanceMeasureProperty().addListener((_, _, _) -> scheduleUpdate());
  }

  private void scheduleUpdate() {
    onTaskThreadDelayed(new PerFileAggregateUpdateTask(model));
  }

  @Override
  protected @NotNull FxViewBuilder<PerFileAggregateModel> getViewBuilder() {
    return builder;
  }

  @Override
  public ObjectProperty<AbundanceMeasure> abundanceMeasureProperty() {
    return model.abundanceMeasureProperty();
  }

  public ObjectProperty<@Nullable ModularFeatureList> featureListProperty() {
    return model.featureListProperty();
  }

  public ObjectProperty<List<RawDataFile>> orderedFilesProperty() {
    return model.orderedFilesProperty();
  }

  public ObjectProperty<Map<RawDataFile, Color>> fileColorsProperty() {
    return model.fileColorsProperty();
  }
}
