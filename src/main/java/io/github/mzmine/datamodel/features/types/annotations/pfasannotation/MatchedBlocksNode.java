/*
 *  Copyright 2006-2020 The MZmine Development Team
 *
 *  This file is part of MZmine.
 *
 *  MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 *  General Public License as published by the Free Software Foundation; either version 2 of the
 *  License, or (at your option) any later version.
 *
 *  MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 *  the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 *  Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with MZmine; if not,
 *  write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 *  USA
 */

package io.github.mzmine.datamodel.features.types.annotations.pfasannotation;

import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.PfasMatch;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser.BuildingBlock;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser.PfasFragment;
import io.github.mzmine.util.color.SimpleColorPalette;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import javafx.geometry.Point2D;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javax.annotation.Nonnull;

public class MatchedBlocksNode extends GridPane {

  private static final int WIDTH = 20;
  private static final int HEIGHT = 20;

  private int numSubstituents = 0;

  public MatchedBlocksNode(@Nonnull final PfasMatch match) {
    super();

    // get all blocks
    final Map<BuildingBlock, Boolean> matchedBlocks = new HashMap<>();
    for (BuildingBlock block : match.getCompound().getBlocks()) {
      matchedBlocks.put(block, false);
    }

    // check which blocks are confirmed
    for (final PfasFragment fragment : match.getMatchedFragments()) {
      matchedBlocks.put(fragment.block(), true);
    }

    final SimpleColorPalette palette = MZmineCore.getConfiguration().getDefaultColorPalette();

    for (Entry<BuildingBlock, Boolean> entry : matchedBlocks.entrySet()) {
      addBlockToPane(entry.getKey(), entry.getValue() ? palette.getPositiveColor() :
          palette.getNegativeColor());
    }

    for(int i = 0; i < getColumnCount(); i++) {
      getColumnConstraints().add(new ColumnConstraints(WIDTH));
    }

    getRowConstraints().add(new RowConstraints(HEIGHT));
    getRowConstraints().add(new RowConstraints(HEIGHT));
  }

  private void addBlockToPane(final BuildingBlock block, Color color) {
    final String shortName = block.getBlockClass().getShortName();
    final Rectangle rect = new Rectangle(WIDTH, HEIGHT, color);
    final Label label = new Label(shortName);
    final Tooltip tt = new Tooltip(block.getName() + ": " + block.getGeneralFormula());
    label.setTooltip(tt);

    final StackPane pn = new StackPane(rect, label);

    final Point2D location = getNodePosition(block);
    add(pn, (int) location.getX(), (int) location.getY());
  }

  private Point2D getNodePosition(final BuildingBlock block) {
    switch (block.getBlockClass()) {
      case BACKBONE -> {
        return new Point2D(0, 0);
      }
      case BACKBONE_LINKER -> {
        return new Point2D(0, 1);
      }
      case FUNCTIONAL_GROUP -> {
        return new Point2D(1, 0);
      }
      case SUBSTITUENT -> {
        // work out where the next label will be. standard layout:
        // B | F  | S2
        // L | S1 | S3
        Point2D p = new Point2D((int) (1 + (numSubstituents / 2)), (1 + numSubstituents) % 2);
        numSubstituents++;
        return p;
      }
    }
    return new Point2D(0, 0);
  }
}
