/*
 * Copyright 2006-2020 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */

package io.github.mzmine.datamodel.impl;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

import io.github.mzmine.datamodel.PeakList.PeakListAppliedMethod;
import io.github.mzmine.parameters.ParameterSet;

public class SimplePeakListAppliedMethod implements PeakListAppliedMethod {

  private String description;
  private ParameterSet parameters;
  private String strParameters;
  private List<String> summary;

  public SimplePeakListAppliedMethod(String description, @Nonnull ParameterSet parameters) {
    assert parameters != null;

    this.description = description;
    this.parameters = parameters;
    summary = new ArrayList<>();
  }

  public SimplePeakListAppliedMethod(String description, @Nonnull String parameters) {
    assert parameters != null;

    this.description = description;
    this.strParameters = parameters;

    summary = new ArrayList<>();
  }

  public SimplePeakListAppliedMethod(String description) {
    this.description = description;
  }

  public @Nonnull
  String getDescription() {
    return description;
  }

  public String toString() {
    return description;
  }

  public @Nonnull
  String getParameters() {
    return parameters.toString();
  }

  /**
   * Adds a new line to the summary part of the processing report.
   *
   * @param newLine
   */
  @Override
  public void appendLine(String newLine) {
    summary.add(newLine);
  }
}
