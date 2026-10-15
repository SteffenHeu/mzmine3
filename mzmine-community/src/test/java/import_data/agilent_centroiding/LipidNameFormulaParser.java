package import_data.agilent_centroiding;

import io.github.mzmine.datamodel.IonizationType;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.identification.SpeciesLevelAnnotation;
import io.github.mzmine.modules.dataprocessing.id_lipidid.common.lipids.LipidClasses;
import io.github.mzmine.modules.dataprocessing.id_lipidid.utils.LipidFactory;
import io.github.mzmine.util.FormulaUtils;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Converts the lipid names used in the benchmark target lists (e.g. {@code PC 34:1},
 * {@code PC(16:0_18:1)}, {@code LPC(0:0/16:0)}, {@code SM d42:2}, {@code Cer(d18:1/24:0)},
 * {@code PE O-38:5}) into a neutral formula via {@link LipidFactory}. Only the lipid classes that
 * occur in the benchmark data are mapped; everything else returns null and is skipped.
 */
public final class LipidNameFormulaParser {

  private static final LipidFactory LIPID_FACTORY = new LipidFactory();

  // class token, e.g. PC, LPC, Cer_NS
  private static final Pattern CLASS_PATTERN = Pattern.compile("^([A-Za-z]+(?:_[A-Za-z]+)?)");
  private static final Pattern CHAIN_PATTERN = Pattern.compile("^[dtm]?(\\d+):(\\d+)$");

  private LipidNameFormulaParser() {
  }

  /**
   * @param lipidClass resolved mzmine lipid class
   * @param carbons    total number of carbons in all chains (incl. sphingoid base)
   * @param dbes       total number of double bonds in all chains
   * @param formula    neutral formula
   */
  public record ParsedLipid(@NotNull LipidClasses lipidClass, int carbons, int dbes,
                            @NotNull String formula) {

  }

  /**
   * @return the parsed lipid or null if the name is not supported (unknown class, deuterated
   * standard, oxidized chains, ...)
   */
  public static @Nullable ParsedLipid parse(@NotNull final String lipidName) {
    // decision: names with alternatives "PC(14:0_18:1);PC(16:0_16:1)" share the sum composition
    String name = lipidName.split(";")[0].trim();
    // assumption: deuterated internal standards like "PC 33:1(d7)" are skipped
    if (name.matches(".*\\(d\\d+\\)$")) {
      return null;
    }

    final Matcher classMatcher = CLASS_PATTERN.matcher(name);
    if (!classMatcher.find()) {
      return null;
    }
    final String classToken = classMatcher.group(1);
    String rest = name.substring(classToken.length()).trim();
    if (rest.startsWith("(") && rest.endsWith(")")) {
      rest = rest.substring(1, rest.length() - 1).trim();
    }

    // ether lipids: O- alkyl, P- alkenyl (= alkyl with one additional double bond)
    boolean ether = false;
    int extraDbe = 0;
    if (rest.startsWith("O-")) {
      ether = true;
      rest = rest.substring(2);
    } else if (rest.startsWith("P-")) {
      ether = true;
      extraDbe = 1;
      rest = rest.substring(2);
    }

    int carbons = 0;
    int dbes = extraDbe;
    for (final String chain : rest.split("[_/]")) {
      final Matcher chainMatcher = CHAIN_PATTERN.matcher(chain.trim());
      if (!chainMatcher.matches()) {
        return null;
      }
      carbons += Integer.parseInt(chainMatcher.group(1));
      dbes += Integer.parseInt(chainMatcher.group(2));
    }
    if (carbons == 0) {
      return null;
    }

    final LipidClasses lipidClass = mapClass(classToken, ether);
    if (lipidClass == null) {
      return null;
    }
    final SpeciesLevelAnnotation annotation = LIPID_FACTORY.buildSpeciesLevelLipid(lipidClass,
        carbons, dbes, 0);
    if (annotation == null || annotation.getMolecularFormula() == null) {
      return null;
    }
    return new ParsedLipid(lipidClass, carbons, dbes,
        FormulaUtils.getFormulaString(annotation.getMolecularFormula(), false));
  }

  private static @Nullable LipidClasses mapClass(@NotNull final String classToken,
      final boolean ether) {
    return switch (classToken.toUpperCase(Locale.ROOT)) {
      case "PC" -> ether ? LipidClasses.ALKYLACYLGLYCEROPHOSPHOCHOLINES
          : LipidClasses.DIACYLGLYCEROPHOSPHOCHOLINES;
      case "LPC" -> ether ? LipidClasses.MONOALKYLGLYCEROPHOSPHOCHOLINES
          : LipidClasses.MONOACYLGLYCEROPHOSPHOCHOLINES;
      case "PE" -> ether ? LipidClasses.ALKYLACYLGLYCEROPHOSPHOETHANOLAMINES
          : LipidClasses.DIACYLGLYCEROPHOSPHOETHANOLAMINES;
      case "LPE" -> ether ? LipidClasses.MONOALKYLGLYCEROPHOSPHOETHANOLAMINES
          : LipidClasses.MONOACYLGLYCEROPHOSPHOETHANOLAMINES;
      case "PS" -> ether ? LipidClasses.ALKYLACYLGLYCEROPHOSPHOSERINES
          : LipidClasses.DIACYLGLYCEROPHOSPHOSERINES;
      case "PG" -> ether ? LipidClasses.ALKYLACYLGLYCEROPHOSPHOGLYCEROLS
          : LipidClasses.DIACYLGLYCEROPHOSPHOGLYCEROLS;
      case "DG" -> ether ? LipidClasses.ALKYLACYLGLYCEROLS : LipidClasses.DIACYLGLYCEROLS;
      case "TG" -> ether ? LipidClasses.ALKYLDIACYLGLYCEROLS : LipidClasses.TRIACYLGLYCEROLS;
      case "SM" -> ether ? null : LipidClasses.CERAMIDEPHOSPHOCHOLINES;
      case "CER", "CER_NS" -> ether ? null : LipidClasses.NACYLSPHINGOSINESANDNACYLSPHINGANINES;
      case "CE" -> ether ? null : LipidClasses.CHOLESTEROLESTERS;
      case "AC", "ACAR", "CAR" -> ether ? null : LipidClasses.FATTYACYLCARNITINES;
      case "ANA", "NAE" -> ether ? null : LipidClasses.NACYLETHANOLAMINES;
      default -> null;
    };
  }

  /**
   * Plausible adducts in decreasing order of likelihood for a lipid class and polarity (ammonium
   * acetate/formate mobile phases).
   */
  public static @NotNull List<IonizationType> adductCandidates(
      @NotNull final LipidClasses lipidClass, @NotNull final PolarityType polarity) {
    if (polarity == PolarityType.NEGATIVE) {
      return switch (lipidClass.getAbbr()) {
        case "PC", "LPC", "SM", "Cer" ->
            List.of(IonizationType.ACETATE, IonizationType.FORMATE, IonizationType.NEGATIVE_HYDROGEN);
        default -> List.of(IonizationType.NEGATIVE_HYDROGEN);
      };
    }
    return switch (lipidClass.getAbbr()) {
      case "DG", "TG", "CE" -> List.of(IonizationType.AMMONIUM, IonizationType.SODIUM);
      // the mzmine carnitine formula is already the quaternary ammonium cation
      case "CAR" -> List.of(IonizationType.POSITIVE);
      default -> List.of(IonizationType.POSITIVE_HYDROGEN, IonizationType.SODIUM);
    };
  }
}
