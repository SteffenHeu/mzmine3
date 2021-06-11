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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PfasLibraryParser2 {

  private static final int CLASS = 0;
  private static final int NAME = 1;
  private static final int REQ = 2;
  private static final int GEN_FORM = 3;
  private static final int TYPES = 4;
  private static final int SMILES = 5;
  private static final int NEG_NL = 6;
  private static final int NEG_FRAG = 7;
  private static final int POS_NL = 8;
  private static final int POS_FRAG = 9;

  private static final Logger logger = Logger.getLogger(PfasLibraryParser2.class.getName());
  private final char sep = ';';
  private final String innerSep = ";";
  private List<BuildingBlock> entries;

  public PfasLibraryParser2() {
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
        if (line.length < POS_FRAG + 1) {
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
        if (!parseFragmentsOrLosses(line, block, reader.getLinesRead(), NEG_NL)) {
          continue;
        }
        if (!parseFragmentsOrLosses(line, block, reader.getLinesRead(), NEG_FRAG)) {
          continue;
        }
        if (!parseFragmentsOrLosses(line, block, reader.getLinesRead(), POS_NL)) {
          continue;
        }
        if (!parseFragmentsOrLosses(line, block, reader.getLinesRead(), POS_FRAG)) {
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
      @Nonnull final BuildingBlock block, long linesRead, int index) {
    // -1 to keep empty

    final String col = line[index];
    if (col.length() == 0) {
      return true;
    }

    final String[] entries = col.split("\\}(\\s+)?\\{");
    if (entries == null) {
      return false;
    }
    // remove first and last bracket
    entries[0] = entries[0].replaceAll("\\{", "");
    entries[entries.length - 1] = entries[entries.length - 1].replaceAll("\\}", "");

    final Pattern pairPattern = Pattern.compile("(\\S+)?;(\\S+)?;(\\S+)?");
    for (int i = 0; i < entries.length; i++) {
      String entry = entries[i];

      final Matcher matcher = pairPattern.matcher(entry);
      if (!matcher.matches()) {
        logger.info(() -> "Entry pattern not found in line " + linesRead + " \"" + entry + "\".");
        continue;
      }

      final String formula = matcher.group(1).isEmpty() ? null : matcher.group(1);
      Double mass = matcher.group(2) == null || matcher.group(2).isEmpty() ? null
          : Double.parseDouble(matcher.group(2));
      final String req =
          matcher.group(3) == null || matcher.group(3).isEmpty() ? null : matcher.group(3);

/*      if (formulas.length == 1 && formulas[0].isEmpty() && masses[0].isEmpty() && reqs[0].isEmpty()) {
        // nothing to do
        return true;
      }*/

      if (formula == null && mass == null && req != null) {
        logger.warning("Line " + linesRead + ": Formula AND mass " + i + " for entry " + entry
            + " is empty although a reqIndex is given.");
      }

      if (block.getBlockClass() != BlockClass.BACKBONE && mass == null && formula != null) {
        // the mass calculated here may be "null", in case the formula was invalid the the time.
        // some functional group fragments require the backbone to calculate the m/z of fragments
        if (formula.contains("+") || formula.contains("-")) {
          mass = FormulaUtils.calculateMzRatio(formula);
        } else {
          mass = FormulaUtils.calculateExactMass(formula);
        }
      }

      switch (index) {
        case NEG_NL -> block.addNegativeNeutralLoss(formula, mass, req);
        case NEG_FRAG -> block.addNegativeFragment(formula, mass, req);
        case POS_NL -> block.addPositiveNeutralLoss(formula, mass, req);
        case POS_FRAG -> block.addPositiveFragment(formula, mass, req);
        default -> {
          logger.warning("Invalid column indices given");
          return false;
        }
      }
    }

    return true;
  }

  @Nullable
  public List<BuildingBlock> getEntries() {
    return entries;
  }
}
