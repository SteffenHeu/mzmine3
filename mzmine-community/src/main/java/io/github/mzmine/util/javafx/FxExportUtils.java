package io.github.mzmine.util.javafx;

import io.github.mzmine.util.files.FileAndPathUtil;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javax.imageio.ImageIO;
import org.jetbrains.annotations.NotNull;

public class FxExportUtils {

  private static final Logger logger = Logger.getLogger(FxExportUtils.class.getName());

  /**
   * Exports an image of the selected pane. The pane must apply css stylesheets and layout already.
   *
   * @param pane
   * @param file
   */
  public static void export(@NotNull final Pane pane, @NotNull final File file) {
    final WritableImage image = new WritableImage((int) pane.getPrefWidth(),
        (int) pane.getPrefHeight());
    pane.snapshot(null, image);

    // Convert to BufferedImage and save as a file
    final File noFormat = FileAndPathUtil.eraseFormat(file);
    try {
      ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", noFormat);
    } catch (IOException e) {
      logger.log(Level.WARNING, "Unable to save image. " + e.getMessage(), e);
    }
  }

  /**
   * Exports the given pane and applies style sheets and layout, e.g. if the pane is not displayed
   * in a GUI.
   *
   */
  public static void export(@NotNull final Pane pane, @NotNull final File file,
      @NotNull List<String> stylesheets) {
    pane.getStylesheets().setAll(stylesheets);
    pane.layout();
    export(pane, file);
  }
}
