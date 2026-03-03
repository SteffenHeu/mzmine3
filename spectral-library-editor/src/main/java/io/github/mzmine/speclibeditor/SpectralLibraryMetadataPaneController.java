/*
 * Copyright (c) 2004-2026 The mzmine Development Team
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

package io.github.mzmine.speclibeditor;

import io.github.mzmine.javafx.mvci.FxController;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.util.spectraldb.entry.DBEntryField;
import javafx.application.Platform;
import org.jetbrains.annotations.NotNull;

/**
 * Controls the metadata and structure pane MVCI.
 */
public final class SpectralLibraryMetadataPaneController extends
    FxController<SpectralLibraryEditorModel> {

  private final @NotNull SpectralLibraryEditorController editorController;
  private final @NotNull SpectralLibraryMetadataPaneViewBuilder viewBuilder;

  /**
   * Creates the metadata pane controller.
   *
   * @param model shared editor model.
   * @param editorController parent editor controller handling metadata commits.
   */
  public SpectralLibraryMetadataPaneController(@NotNull final SpectralLibraryEditorModel model,
      @NotNull final SpectralLibraryEditorController editorController) {
    super(model);
    this.editorController = editorController;
    viewBuilder = new SpectralLibraryMetadataPaneViewBuilder(model, this);
  }

  /**
   * Commits one metadata field edit to the parent editor controller.
   *
   * @param field metadata field that should be committed.
   */
  public void onMetadataCommit(@NotNull final DBEntryField field) {
    if (!Platform.isFxApplicationThread()) {
      onGuiThread(() -> onMetadataCommit(field));
      return;
    }

    editorController.onMetadataCommit(field);
  }

  /**
   * Returns the metadata pane view builder.
   *
   * @return metadata pane view builder.
   */
  @Override
  protected @NotNull FxViewBuilder<SpectralLibraryEditorModel> getViewBuilder() {
    return viewBuilder;
  }
}

