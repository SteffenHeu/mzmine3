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

package io.github.mzmine.modules;

public enum MZmineModuleCategory {

  PROJECTIO("Project I/O"), //
  PROJECT("Project"), //
  RAWDATA("Raw data methods"), //
  RAWDATAFILTERING("Raw data filtering"), //
  PEAKPICKING("Peak picking"), //
  GAPFILLING("Gap filling"), //
  ISOTOPES("Isotopes"), //
  FEATURELIST("Feature list methods"), //
  FEATURELISTPICKING("Feature list processing"), //
  SPECTRALDECONVOLUTION("Spectral deconvolution"), //
  FEATURELISTFILTERING("Feature list filtering"), //
  ALIGNMENT("Alignment"), //
  NORMALIZATION("Normalization"), //
  IDENTIFICATION("Identification"), //
  FEATURELISTEXPORT("Feature list export"), //
  FEATURELISTIMPORT("Feature list import"), //
  VISUALIZATIONRAWDATA("Visualization"), //
  VISUALIZATIONFEATURELIST("Visualization feature list"), //
  DATAANALYSIS("Data analysis"), //
  HELPSYSTEM("Help"), //
  TOOLS("Tools"); //

  private final String name;

  MZmineModuleCategory(String name) {
    this.name = name;
  }

  public String toString() {
    return name;
  }

  public static boolean isRawDataProcessingModule(MZmineModuleCategory category) {
    switch (category) {
      case RAWDATA:
      case RAWDATAFILTERING:
        return true;
      default:
        return false;
    }
  }

  public static boolean isFeatureListProcessingModule(MZmineModuleCategory category) {
    switch (category) {
      case GAPFILLING:
      case ISOTOPES:
      case FEATURELIST:
      case FEATURELISTPICKING:
      case FEATURELISTFILTERING:
      case ALIGNMENT:
      case NORMALIZATION:
      case IDENTIFICATION:
      case FEATURELISTEXPORT:
      case FEATURELISTIMPORT:
        return true;
      default:
        return false;
    }
  }

  public static boolean isVisualizationModule(MZmineModuleCategory category) {
    switch (category) {
      case VISUALIZATIONRAWDATA:
      case VISUALIZATIONFEATURELIST:
        return true;
      default:
        return false;
    }
  }
}
