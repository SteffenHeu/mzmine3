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

package io.github.mzmine.javafx.validation;

import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import org.controlsfx.control.decoration.Decoration;
import org.controlsfx.control.decoration.GraphicDecoration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Similar to {@link GraphicDecoration} but positions the graphic relative to the bounds of the
 * visible children of a {@link Pane} instead of the pane itself. Parameter components are often
 * panes that grow with the window, so the plain {@link GraphicDecoration} would place a
 * {@link Pos#TOP_RIGHT} graphic at the window edge instead of next to the actual input fields.
 * <p>
 * Only changes the position of the graphic and not the layout of the target.
 */
public class ContentAlignedGraphicDecoration extends Decoration {

  private final @NotNull Node decorationNode;
  private final @NotNull Pos pos;
  private final ChangeListener<Boolean> targetNeedsLayoutListener;
  private @Nullable Pane target;

  public ContentAlignedGraphicDecoration(@NotNull Node decorationNode, @NotNull Pos pos) {
    this.decorationNode = decorationNode;
    this.pos = pos;
    // position is computed manually
    decorationNode.setManaged(false);
    targetNeedsLayoutListener = (_, _, _) -> {
      if (target != null) {
        layoutGraphic(target);
      }
    };
  }

  @Override
  public Node applyDecoration(Node targetNode) {
    if (!(targetNode instanceof Pane pane)) {
      throw new IllegalArgumentException(
          "Target needs to be a Pane but was " + targetNode.getClass().getName());
    }
    target = pane;
    if (!pane.getChildren().contains(decorationNode)) {
      pane.getChildren().add(decorationNode);
    }
    layoutGraphic(pane);
    // in case already added
    pane.needsLayoutProperty().removeListener(targetNeedsLayoutListener);
    pane.needsLayoutProperty().addListener(targetNeedsLayoutListener);
    // decoration node is added to the target and not to the DecorationPane
    return null;
  }

  @Override
  public void removeDecoration(Node targetNode) {
    if (targetNode instanceof Pane pane) {
      pane.getChildren().remove(decorationNode);
      pane.needsLayoutProperty().removeListener(targetNeedsLayoutListener);
    }
    target = null;
  }

  private void layoutGraphic(@NotNull Pane pane) {
    // unmanaged node needs to be sized manually
    decorationNode.autosize();
    final Bounds decorationBounds = decorationNode.getLayoutBounds();
    final double width = decorationBounds.getWidth();
    final double height = decorationBounds.getHeight();
    final Bounds content = Objects.requireNonNullElse(computeContentBounds(pane),
        pane.getLayoutBounds());

    final double x = switch (pos.getHpos()) {
      case LEFT -> content.getMinX() - width / 2;
      case CENTER -> content.getMinX() + content.getWidth() / 2 - width / 2;
      case RIGHT -> content.getMaxX() - width / 2;
    };
    final double y = switch (pos.getVpos()) {
      case TOP -> content.getMinY() - height / 2;
      case CENTER -> content.getMinY() + content.getHeight() / 2 - height / 2;
      case BOTTOM -> content.getMaxY() - height / 2;
      case BASELINE -> pane.getBaselineOffset() - decorationNode.getBaselineOffset() - height / 2;
    };
    decorationNode.setLayoutX(x);
    decorationNode.setLayoutY(y);
  }

  /**
   * Nested panes often grow as well (e.g., a GridPane in the center of a BorderPane), so their
   * content is used instead of their own bounds.
   *
   * @return union of the visible and managed children in the local coordinates of pane or null if
   * there are none
   */
  private @Nullable Bounds computeContentBounds(@NotNull Pane pane) {
    double minX = Double.POSITIVE_INFINITY;
    double minY = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    double maxY = Double.NEGATIVE_INFINITY;
    for (final Node child : pane.getChildren()) {
      if (child == decorationNode || !child.isVisible() || !child.isManaged()) {
        continue;
      }
      final Bounds bounds;
      if (child instanceof Pane childPane) {
        final Bounds childContent = computeContentBounds(childPane);
        if (childContent == null) {
          // empty pane has no content to align to
          continue;
        }
        bounds = childPane.localToParent(childContent);
      } else {
        bounds = child.getBoundsInParent();
      }
      minX = Math.min(minX, bounds.getMinX());
      minY = Math.min(minY, bounds.getMinY());
      maxX = Math.max(maxX, bounds.getMaxX());
      maxY = Math.max(maxY, bounds.getMaxY());
    }
    if (minX > maxX) {
      return null;
    }
    return new BoundingBox(minX, minY, maxX - minX, maxY - minY);
  }
}
