package io.github.mzmine.modules.io.import_library_sqlite.cfm_sqlite;


import io.github.mzmine.datamodel.DataPoint;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.impl.SimpleDataPoint;
import io.github.mzmine.modules.io.import_library_sqlite.sqlite_datamodel.SQLiteDataColumn;
import io.github.mzmine.modules.io.import_library_sqlite.sqlite_datamodel.SQLiteTable;
import io.github.mzmine.util.FormulaUtils;
import io.github.mzmine.util.spectraldb.entry.DBEntryField;
import io.github.mzmine.util.spectraldb.entry.SpectralDBEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IMolecularFormula;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.tools.manipulator.MolecularFormulaManipulator;

public class JoinedDataTable extends SQLiteTable<String> {

  private static final Logger logger = Logger.getLogger(JoinedDataTable.class.getName());

  public static final String FRAGMENTS_POSTIVE_TABLE = "cfm_pos";
  public static final String FRAGMENTS_NEGATIVE_TABLE = "cfm_neg";
  public static final String MOLECULES_TABLE = "molecules";

  public static final String ID_COL_NAME = "ID";
  public static final String INCHI_KEY_COL_NAME = "InChIKey";
  public static final String INCHI_COL_NAME = "InChI";
  public static final String SMILES_COL_NAME = "SMILES";
  public static final String SPECTRUM_COL_NAME = "spectrum";

  private static SQLiteDataColumn<String> idColumn;
  private static final SQLiteDataColumn<String> inchiKeyColumn = new SQLiteDataColumn<>(
      INCHI_KEY_COL_NAME);
  private static final SQLiteDataColumn<String> inchiColumn = new SQLiteDataColumn<>(
      INCHI_COL_NAME);
  private static final SQLiteDataColumn<String> smilesColumn = new SQLiteDataColumn<>(
      SMILES_COL_NAME);
  private static final SQLiteDataColumn<String> positiveSpectrumColumn = new SQLiteDataColumn<>(
      SPECTRUM_COL_NAME + "_positve");
  private static final SQLiteDataColumn<String> negativeSpectrumColumn = new SQLiteDataColumn<>(
      SPECTRUM_COL_NAME + "_negative");

  public JoinedDataTable() {
    super("JoinedDataTable", ID_COL_NAME);
    idColumn = keyList;

    columns.addAll(List.of(inchiKeyColumn, inchiColumn, smilesColumn, positiveSpectrumColumn,
        negativeSpectrumColumn));
  }

  @Override
  protected String getColumnHeadersForQuery() {
    final String compoundTable = MOLECULES_TABLE;
    final String positive = FRAGMENTS_POSTIVE_TABLE;
    final String negative = FRAGMENTS_NEGATIVE_TABLE;

    return compoundTable + "." + ID_COL_NAME + ", " //
        + compoundTable + "." + INCHI_KEY_COL_NAME + ", "//
        + compoundTable + "." + INCHI_COL_NAME + ", "//
        + compoundTable + "." + SMILES_COL_NAME + ", "//
        + positive + "." + SPECTRUM_COL_NAME + ", "//
        + negative + "." + SPECTRUM_COL_NAME;
  }

  @Override
  protected String getQueryText(String columnHeadersForQuery) {
    String compoundTable = MOLECULES_TABLE;
    String positive = FRAGMENTS_POSTIVE_TABLE;
    String negative = FRAGMENTS_NEGATIVE_TABLE;

    return "SELECT " + columnHeadersForQuery + " " + //
        "FROM " + MOLECULES_TABLE + " " + //
        "LEFT JOIN " + positive + //
        " ON " + compoundTable + "." + ID_COL_NAME + //
        "=" + positive + "." + ID_COL_NAME + " " + //
        "LEFT JOIN " + negative + //
        " ON " + compoundTable + "." + ID_COL_NAME + //
        "=" + negative + "." + ID_COL_NAME + //
        " ORDER BY " + compoundTable + "." + ID_COL_NAME;
  }

  public List<SpectralDBEntry> getEntriesForCompound(int index) {
    List<SpectralDBEntry> entries = new ArrayList<>();

    try {
      final SmilesParser parser = new SmilesParser(SilentChemObjectBuilder.getInstance());
      final IAtomContainer iAtomContainer = parser.parseSmiles(smilesColumn.get(index));
      final IMolecularFormula molecularFormula = MolecularFormulaManipulator.getMolecularFormula(
          iAtomContainer);

      final Integer charge = molecularFormula.getCharge();

      // neutralise the molecular formula
      if (charge != null && charge != 0) {
        MolecularFormulaManipulator.adjustProtonation(molecularFormula, -charge);
      }
      String neutralFormula = MolecularFormulaManipulator.getString(molecularFormula);

      MolecularFormulaManipulator.adjustProtonation(molecularFormula, +1);
      final double posPrecursorMz = MolecularFormulaManipulator.getMass(molecularFormula,
          MolecularFormulaManipulator.MonoIsotopic) - FormulaUtils.electronMass;
      // remove protonation proton + deprotonate ([M+H]+ -> [M-H]-)
      MolecularFormulaManipulator.adjustProtonation(molecularFormula, -2);
      final double negPrecursorMz = MolecularFormulaManipulator.getMass(molecularFormula,
          MolecularFormulaManipulator.MonoIsotopic) + FormulaUtils.electronMass;

      /*if (Math.abs(negPrecursorMz - 569d) < 0.10d) {
        logger.finest(
            neutralFormula + " " + negPrecursorMz + " " + MolecularFormulaManipulator.getString(
                molecularFormula));
      }*/

      if (positiveSpectrumColumn.get(index) != null) {
        entries.addAll(getEntries(index, PolarityType.POSITIVE, posPrecursorMz, neutralFormula));
      }
      if (negativeSpectrumColumn.get(index) != null) {
        entries.addAll(getEntries(index, PolarityType.NEGATIVE, negPrecursorMz, neutralFormula));
      }

    } catch (InvalidSmilesException e) {
      e.printStackTrace();
      logger.log(Level.SEVERE, e.getMessage(), e);
    }
    return entries;
  }

  private List<SpectralDBEntry> getEntries(int index, PolarityType polarity, double precursorMz,
      String formula) {
    final List<List<DataPoint>> spectra = parseSpectra(
        polarity == PolarityType.POSITIVE ? positiveSpectrumColumn.get(index)
            : negativeSpectrumColumn.get(index));
    List<SpectralDBEntry> entries = new ArrayList<>();
    for (int i = 0; i < spectra.size(); i++) {
      Map<DBEntryField, Object> fields = new HashMap<>();
      fields.put(DBEntryField.INCHI, inchiColumn.get(index));
      fields.put(DBEntryField.INCHIKEY, inchiKeyColumn.get(index));
      fields.put(DBEntryField.SMILES, smilesColumn.get(index));
      fields.put(DBEntryField.ION_TYPE, polarity == PolarityType.POSITIVE ? "[M+H]+" : "[M-H]-");
      fields.put(DBEntryField.INSTRUMENT, "in silico (CFM-ID)");
      fields.put(DBEntryField.MS_LEVEL, "2");
      fields.put(DBEntryField.CHARGE, polarity.getSign());
      fields.put(DBEntryField.MZ, precursorMz);
      fields.put(DBEntryField.COLLISION_ENERGY, String.valueOf(i * 20));
      fields.put(DBEntryField.NUM_PEAKS, spectra.get(i).size());
      fields.put(DBEntryField.ION_MODE, polarity.toString());
      fields.put(DBEntryField.FORMULA, formula);

      SpectralDBEntry entry = new SpectralDBEntry(fields, spectra.get(i).toArray(DataPoint[]::new));
      entries.add(entry);
    }
    return entries;
  }

  /**
   * energy0 74.02365479 3.2863569 60 (3.2864) 501.9624512 17.27790283 0 (17.278) energy1
   * 30.03382555 1.593142171 100 (1.5931) 481.9562229 1.499593417 59 79 88 61 93 80 74 (0.37383
   * 0.27932 0.22598 0.21763 0.21317 0.138 0.051654) energy2 30.03382555 1.791918614 100 (1.7919)
   * 455.9569719 1.949027815 8 (1.949)
   * <p>
   * 0 501.9624512 O=C(O)C[NH2+]S(=O)(=O)C(F)(F)C(F)(F)OC(F)(C(F)(F)F)C(F)(F)OC(F)=C(F)F
   *
   * @param text
   * @return
   */
  private List<List<DataPoint>> parseSpectra(String text) {
    List<List<DataPoint>> spectra = new ArrayList<>();
    final String[] split = text.split("energy\\d\\n");
    final int numSpectra = split.length;
    for (int i = 1; i < numSpectra; i++) {
      final List<DataPoint> dps = new ArrayList<>();
      final String[] lines = split[i].split("\\n");

      for (int j = 0; j < lines.length; j++) {
        if (lines[j].trim().isEmpty()) {
          break; // one whitespace between high energy spectrum and fragments -> break
        }

        final String[] cols = lines[j].split(" ");
        if (cols.length < 2) {
          continue;
        }

        dps.add(new SimpleDataPoint(Double.parseDouble(cols[0]), Double.parseDouble(cols[1])));
      }

      spectra.add(dps);
    }

    if (spectra.size() != 3) {
      logger.finest(() -> "Less than 3 spectra. (" + spectra.size() + ")");
    }
    return spectra;
  }

}
