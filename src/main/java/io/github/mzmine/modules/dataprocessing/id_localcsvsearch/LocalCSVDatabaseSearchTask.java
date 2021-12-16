/*
 * Copyright 2006-2021 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package io.github.mzmine.modules.dataprocessing.id_localcsvsearch;

import com.Ostermiller.util.CSVParser;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.SimpleFeatureListAppliedMethod;
import io.github.mzmine.datamodel.features.types.annotations.compounddb.CompoundAnnotationScoreType;
import io.github.mzmine.datamodel.features.compoundannotations.CompoundDBAnnotation;
import io.github.mzmine.datamodel.features.compoundannotations.SimpleCompoundDBAnnotation;
import io.github.mzmine.datamodel.features.types.DataType;
import io.github.mzmine.datamodel.features.types.annotations.CommentType;
import io.github.mzmine.datamodel.features.types.annotations.CompoundNameType;
import io.github.mzmine.datamodel.features.types.annotations.SmilesStructureType;
import io.github.mzmine.datamodel.features.types.annotations.compounddb.IonTypeType;
import io.github.mzmine.datamodel.features.types.annotations.formula.FormulaType;
import io.github.mzmine.datamodel.features.types.numbers.CCSType;
import io.github.mzmine.datamodel.features.types.numbers.MZType;
import io.github.mzmine.datamodel.features.types.numbers.MobilityType;
import io.github.mzmine.datamodel.features.types.numbers.NeutralMassType;
import io.github.mzmine.datamodel.features.types.numbers.RTType;
import io.github.mzmine.datamodel.identities.iontype.IonType;
import io.github.mzmine.modules.dataprocessing.id_ion_identity_networking.ionidnetworking.IonNetworkLibrary;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.ImportType;
import io.github.mzmine.parameters.parametertypes.ionidentity.IonLibraryParameterSet;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.mobilitytolerance.MobilityTolerance;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.io.File;
import java.io.FileReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;

public class LocalCSVDatabaseSearchTask extends AbstractTask {

  private static Logger logger = Logger.getLogger(LocalCSVDatabaseSearchTask.class.getName());

  private final MobilityTolerance mobTolerance;
  private final Double ccsTolerance;
  private final File dataBaseFile;
  private final String fieldSeparator;
  private final MZTolerance mzTolerance;
  private final RTTolerance rtTolerance;
  private final ParameterSet parameters;
  private final List<ImportType> importTypes;
  private final IonLibraryParameterSet ionLibraryParameterSet;
  private IonNetworkLibrary ionNetworkLibrary;

  private String[][] databaseValues;
  private int finishedLines = 0;
  private FeatureList peakList;

  LocalCSVDatabaseSearchTask(FeatureList peakList, ParameterSet parameters,
      @NotNull Instant moduleCallDate) {
    super(null, moduleCallDate); // no new data stored -> null

    this.peakList = peakList;
    this.parameters = parameters;

    dataBaseFile = parameters.getParameter(LocalCSVDatabaseSearchParameters.dataBaseFile)
        .getValue();
    fieldSeparator = parameters.getParameter(LocalCSVDatabaseSearchParameters.fieldSeparator)
        .getValue();
    importTypes = parameters.getParameter(LocalCSVDatabaseSearchParameters.columns).getValue();
    mzTolerance = parameters.getParameter(LocalCSVDatabaseSearchParameters.mzTolerance).getValue();
    rtTolerance = parameters.getParameter(LocalCSVDatabaseSearchParameters.rtTolerance).getValue();
    mobTolerance = parameters.getParameter(LocalCSVDatabaseSearchParameters.mobTolerance)
        .getValue();
    ccsTolerance = parameters.getParameter(LocalCSVDatabaseSearchParameters.ccsTolerance)
        .getValue();

    Boolean calcMz = parameters.getValue(LocalCSVDatabaseSearchParameters.ionLibrary);
    ionLibraryParameterSet = calcMz != null && calcMz ? parameters.getParameter(
        LocalCSVDatabaseSearchParameters.ionLibrary).getEmbeddedParameters() : null;
  }

  /**
   * @see io.github.mzmine.taskcontrol.Task#getFinishedPercentage()
   */
  @Override
  public double getFinishedPercentage() {
    if (databaseValues == null) {
      return 0;
    }
    return ((double) finishedLines) / databaseValues.length;
  }

  /**
   * @see io.github.mzmine.taskcontrol.Task#getTaskDescription()
   */
  @Override
  public String getTaskDescription() {
    return "Peak identification of " + peakList + " using database " + dataBaseFile;
  }

  /**
   * @see java.lang.Runnable#run()
   */
  @Override
  public void run() {

    setStatus(TaskStatus.PROCESSING);

    try {
      ionNetworkLibrary =
          ionLibraryParameterSet != null ? new IonNetworkLibrary(ionLibraryParameterSet,
              mzTolerance) : null;
      // read database contents in memory
      FileReader dbFileReader = new FileReader(dataBaseFile);
      databaseValues = CSVParser.parse(dbFileReader, fieldSeparator.charAt(0));

      List<ImportType> lineIds = findLineIds(importTypes, databaseValues[0]);

//      peakList.addRowType(new CompoundDatabaseMatchesType());
      finishedLines++;
      for (; finishedLines < databaseValues.length; finishedLines++) {
        if (isCanceled()) {
          dbFileReader.close();
          return;
        }
        try {
          processOneLine(databaseValues[finishedLines], lineIds);
        } catch (Exception e) {
          logger.log(Level.FINE, "Exception while processing csv line " + finishedLines, e);
        }
      }
      dbFileReader.close();

    } catch (Exception e) {
      logger.log(Level.WARNING, "Could not read file " + dataBaseFile, e);
      setStatus(TaskStatus.ERROR);
      setErrorMessage(e.toString());
      return;
    }

    // Add task description to peakList
    peakList.addDescriptionOfAppliedTask(
        new SimpleFeatureListAppliedMethod("Peak identification using database " + dataBaseFile,
            LocalCSVDatabaseSearchModule.class, parameters, getModuleCallDate()));

    setStatus(TaskStatus.FINISHED);

  }

  private void processOneLine(String values[], List<ImportType> linesWithIndices) {

    final CompoundDBAnnotation baseAnnotation = getCompoundFromLine(values, linesWithIndices);
    final List<CompoundDBAnnotation> annotations = new ArrayList<>();
    if (ionNetworkLibrary != null) {
      annotations.addAll(
          CompoundDBAnnotation.buildCompoundsWithAddcuts(baseAnnotation, ionNetworkLibrary));
    } else {
      annotations.add(baseAnnotation);
    }

    for (CompoundDBAnnotation annotation : annotations) {
      for (FeatureListRow peakRow : peakList.getRows()) {
        if (annotation.matches(peakRow, mzTolerance, rtTolerance, mobTolerance, ccsTolerance)) {
          final CompoundDBAnnotation clone = annotation.clone();
          clone.put(CompoundAnnotationScoreType.class, clone.getScore(peakRow, mzTolerance, rtTolerance, mobTolerance, ccsTolerance));
          peakRow.addCompoundAnnotation(clone);
          peakRow.getCompoundAnnotations().sort(Comparator.comparingDouble(CompoundDBAnnotation::getScore));
        }
      }
    }
  }

  @NotNull
  private CompoundDBAnnotation getCompoundFromLine(String[] values,
      List<ImportType> linesWithIndices) {
    var formulaType = new FormulaType();
    var compoundNameType = new CompoundNameType();
    var commentType = new CommentType();
    var mzType = new MZType();
    var rtType = new RTType();
    var mobType = new MobilityType();
    var ccsType = new CCSType();
    var smilesType = new SmilesStructureType();
    var adductType = new IonTypeType();
    var neutralMassType = new NeutralMassType();
    var ionTypeType = new IonTypeType();

    final Map<DataType<?>, String> entry = new HashMap<>();

    for (int i = 0; i < linesWithIndices.size(); i++) {
      var type = linesWithIndices.get(i);
      entry.put(type.getDataType(), values[type.getColumnIndex()]);
    }

//    lineID = entry.get();
    final String lineName = entry.get(compoundNameType);
    final String lineFormula = entry.get(formulaType);
    final String lineAdduct = entry.get(adductType);
    final Double lineMZ =
        (entry.get(mzType) != null) ? Double.parseDouble(entry.get(mzType)) : null;
    final Float lineRT = (entry.get(rtType) != null) ? Float.parseFloat(entry.get(rtType)) : null;
    final Float lineMob =
        (entry.get(mobType) != null) ? Float.parseFloat(entry.get(mobType)) : null;
    final Float lineCCS =
        (entry.get(ccsType) != null) ? Float.parseFloat(entry.get(ccsType)) : null;
    final Double neutralMass =
        entry.get(neutralMassType) != null ? Double.parseDouble(entry.get(neutralMassType)) : null;
    final String smiles = entry.get(smilesType);

    CompoundDBAnnotation a = new SimpleCompoundDBAnnotation();
    doIf(lineName != null, () -> a.put(compoundNameType, lineName));
    doIf(lineFormula != null, () -> a.put(formulaType, lineFormula));
    doIf(lineRT != null, () -> a.put(rtType, lineRT));
    doIf(lineMob != null, () -> a.put(mobType, lineMob));
    doIf(lineCCS != null, () -> a.put(ccsType, lineCCS));
    doIf(smiles != null, () -> a.put(smilesType, smiles));
    doIf(lineMZ != null, () -> a.put(MZType.class, lineMZ));
    doIf(neutralMass != null, () -> a.put(neutralMassType, neutralMass));
    doIf(IonType.parseFromString(lineAdduct) != null,
        () -> a.put(ionTypeType, IonType.parseFromString(lineAdduct)));
    return a;
  }

  private List<ImportType> findLineIds(List<ImportType> importTypes, String[] firstLine) {

    List<ImportType> lines = new ArrayList<>();
    for (ImportType importType : importTypes) {
      if (importType.isSelected()) {
        ImportType type = new ImportType(importType.isSelected(), importType.getCsvColumnName(),
            importType.getDataType());
        lines.add(type);
      }
    }

    for (ImportType importType : lines) {
      for (int i = 0; i < firstLine.length; i++) {
        String columnName = firstLine[i];
        if (columnName.equals(importType.getCsvColumnName())) {
          if (importType.getColumnIndex() != -1) {
            setErrorMessage(
                "Library file " + dataBaseFile.getAbsolutePath() + " contains two columns called \""
                    + columnName + "\".");
            setStatus(TaskStatus.ERROR);
          }
          importType.setColumnIndex(i);
        }
      }
    }

    final List<ImportType> nullMappings = lines.stream().filter(val -> val.getColumnIndex() == -1)
        .toList();
    if (!nullMappings.isEmpty()) {
      setErrorMessage("Did not find specified column " + Arrays.toString(
          nullMappings.stream().map(ImportType::getCsvColumnName).toArray()) + " in file "
          + dataBaseFile.getAbsolutePath());
      setStatus(TaskStatus.ERROR);
    }

    return lines;
  }

  private void doIf(boolean condition, Runnable r) {
    if (condition) {
      r.run();
    }
  }

}
