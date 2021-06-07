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

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.types.DataType;
import io.github.mzmine.datamodel.features.types.modifiers.GraphicalColumType;
import io.github.mzmine.modules.dataprocessing.id_pfas_annotation.PfasMatch;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javax.annotation.Nonnull;

public class MatchedBlocksType extends DataType<ObjectProperty<PfasMatch>> implements GraphicalColumType<PfasMatch> {

  @Override
  public Node getCellNode(TreeTableCell<ModularFeatureListRow, PfasMatch> cell,
      TreeTableColumn<ModularFeatureListRow, PfasMatch> coll, PfasMatch cellData, RawDataFile raw) {
    return new MatchedBlocksNode(cellData);
  }

  @Override
  public double getColumnWidth() {
    return 60;
  }

  @Nonnull
  @Override
  public String getHeaderString() {
    return "Matched";
  }

  @Override
  public ObjectProperty<PfasMatch> createProperty() {
    return new SimpleObjectProperty<>();
  }
}
