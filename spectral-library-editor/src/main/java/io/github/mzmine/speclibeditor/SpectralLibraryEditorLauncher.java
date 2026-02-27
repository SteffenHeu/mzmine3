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

package io.github.mzmine.speclibeditor;

import java.util.Locale;
import javafx.application.Application;
import org.jetbrains.annotations.NotNull;

/**
 * Dedicated launcher class to avoid Java launcher FX detection issues when directly starting a
 * class that extends {@link Application}.
 */
public final class SpectralLibraryEditorLauncher {

  /**
   * Prevents instantiation of the launcher utility class.
   */
  private SpectralLibraryEditorLauncher() {
  }

  /**
   * Starts the spectral library editor JavaFX application.
   *
   * @param args command line arguments passed to JavaFX.
   */
  public static void main(@NotNull final String[] args) {
    Application.launch(SpectralLibraryEditorApplication.class, args);
  }
}
