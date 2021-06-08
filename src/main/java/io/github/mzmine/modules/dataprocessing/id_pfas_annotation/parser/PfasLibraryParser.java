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

package io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import io.github.mzmine.util.FormulaUtils;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PfasLibraryParser {

  private final char sep = ',';
  private final String innerSep = ";";

  private static final int CLASS = 0;
  private static final int NAME = 1;
  private static final int REQ = 2;
  private static final int GEN_FORM = 3;
  private static final int TYPES = 4;
  private static final int SMILES = 5;

  private static final int NL_FORMULA_NEG = 6;
  private static final int NL_MASS_NEG = 7;
  private static final int NL_REQ_NEG = 8;
  private static final int FRAG_FORMULA_NEG = 9;
  private static final int FRAG_MASS_NEG = 10;
  private static final int FRAG_REQ_NEG = 11;

  private static final int NL_FORMULA_POS = 12;
  private static final int NL_MASS_POS = 13;
  private static final int NL_REQ_POS = 14;
  private static final int FRAG_FORMULA_POS = 15;
  private static final int FRAG_MASS_POS = 16;
  private static final int FRAG_REQ_POS = 17;

  private static final Logger logger = Logger.getLogger(PfasLibraryParser.class.getName());

  private List<BuildingBlock> entries;

  public PfasLibraryParser() {
  }

  public boolean read(File file) {

    logger.finest(() -> "Reading pfas compound library from " + file.getAbsolutePath());

    final List<BuildingBlock> blocks = new ArrayList<>();
    try {
      final CSVParser parser = new CSVParserBuilder().withSeparator(sep).build();
      final CSVReader reader = new CSVReaderBuilder(new FileReader(file)).withCSVParser(parser)
          .withSkipLines(1).build();

      String[] line;
      while ((line = reader.readNext()) != null) {
        if (line.length != FRAG_REQ_POS + 1) {
          logger.warning(() -> "Illegal number of columns in line " + reader.getLinesRead() + ".");
          continue;
        }
        if (line[CLASS].length() == 0) {
          continue;
        }

        final BlockClass c = BlockClass.get(line[CLASS]);
        if (c == null) {
          String[] finalLine = line;
          logger.severe(() -> "Line " + reader.getLinesRead() + ": Unknown class in line "
              + finalLine[CLASS]);
          continue;
        }

        final String name = line[NAME].trim();
        final String req = line[REQ].trim();
        final String form = line[GEN_FORM].trim();
        final String types = line[TYPES].trim().length() > 0 ? line[TYPES].trim() : null;
        final String smiles = line[SMILES].trim().length() > 0 ? line[SMILES].trim() : null;
        if (name.isEmpty() || form.isEmpty()) {
          logger.severe(
              () -> "Line: " + reader.getLinesRead() + ": Illegal name or formula: " + name + ", "
                  + form);
        }

        final BuildingBlock block = new BuildingBlock(name, form, c, types, smiles);

        if (req.length() != 0 && req.split(innerSep).length != 0) {
          Arrays.stream(req.split(innerSep)).forEach(block::addRequires);
        }
        if (!parseFragmentsOrLosses(line, block, reader.getLinesRead(), NL_FORMULA_NEG, NL_MASS_NEG,
            NL_REQ_NEG)) {
          continue;
        }
        if (!parseFragmentsOrLosses(line, block, reader.getLinesRead(), FRAG_FORMULA_NEG,
            FRAG_MASS_NEG, FRAG_REQ_NEG)) {
          continue;
        }
        if (!parseFragmentsOrLosses(line, block, reader.getLinesRead(), NL_FORMULA_POS, NL_MASS_POS,
            NL_REQ_POS)) {
          continue;
        }
        if (!parseFragmentsOrLosses(line, block, reader.getLinesRead(), FRAG_FORMULA_POS,
            FRAG_MASS_POS, FRAG_REQ_POS)) {
          continue;
        }

        blocks.add(block);
      }
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
    entries = blocks;

    logger.finest(() -> "Loaded " + entries.size() + " building blocks from file.");

    return true;
  }

  private boolean parseFragmentsOrLosses(@Nonnull final String[] line,
      @Nonnull final BuildingBlock block, long linesRead, int formIndex, int massIndex,
      int reqIndex) {
    // -1 to keep empty
    String[] formulas = line[formIndex].split(innerSep, -1);
    String[] masses = line[massIndex].split(innerSep, -1);
    String[] reqs = line[reqIndex].split(innerSep, -1);

    if (formulas.length != masses.length || masses.length != reqs.length) {
      throw new IllegalStateException(
          "Line " + linesRead + ": Number of sub columns in columns" + formIndex + ", " + massIndex
              + ", " + reqIndex + " " + "do not match: " + formulas.length + ", " + masses.length
              + ", " + reqs.length);
    }

    if (formulas.length == 1 && formulas[0].isEmpty() && masses[0].isEmpty() && reqs[0].isEmpty()) {
      // nothing to do
      return true;
    }

    for (int i = 0; i < formulas.length; i++) {
      final String formula = formulas[i].isEmpty() ? null : formulas[i];
      Double mass = masses[i].isEmpty() ? null : Double.parseDouble(masses[i]);
      final String req = reqs[i].isEmpty() ? null : reqs[i];

      if (formula == null && mass == null && req != null) {
        logger.warning(
            "Line " + linesRead + ": Formula AND massIndex " + i + " for columns " + formIndex
                + ", " + massIndex + " is empty although a reqIndex is given.");
      }

      if (block.getBlockClass() != BlockClass.BACKBONE && mass == null && formula != null) {
        if (formula.contains("+") || formula.contains("-")) {
          mass = FormulaUtils.calculateMzRatio(formula);
        } else {
          mass = FormulaUtils.calculateExactMass(formula);
        }
      }

      if (formIndex == NL_FORMULA_NEG && massIndex == NL_MASS_NEG && reqIndex == NL_REQ_NEG) {
        block.addNegativeNeutralLoss(formula, mass, req);
      } else if (formIndex == FRAG_FORMULA_NEG && massIndex == FRAG_MASS_NEG
          && reqIndex == FRAG_REQ_NEG) {
        block.addNegativeFragment(formula, mass, req);
      } else if (formIndex == NL_FORMULA_POS && massIndex == NL_MASS_POS
          && reqIndex == NL_REQ_POS) {
        block.addPositiveNeutralLoss(formula, mass, req);
      } else if (formIndex == FRAG_FORMULA_POS && massIndex == FRAG_MASS_POS
          && reqIndex == FRAG_REQ_POS) {
        block.addPositiveFragment(formula, mass, req);
      } else {
        logger.warning("Invalid column indices given");
        return false;
      }
    }
    return true;
  }

  @Nullable
  public List<BuildingBlock> getEntries() {
    return entries;
  }
}
