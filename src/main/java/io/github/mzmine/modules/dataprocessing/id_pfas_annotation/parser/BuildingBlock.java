package io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser;

import io.github.mzmine.datamodel.PolarityType;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class BuildingBlock {

  private static final Logger logger = Logger.getLogger(BuildingBlock.class.getName());
  /**
   * Only used for {link #toString}.
   */
  private static final NumberFormat mzFormat = new DecimalFormat("0.000000",
      new DecimalFormatSymbols(Locale.US));

  private final String name;
  private final String generalFormula;
  private final BlockClass blockClass;
  private final String types;
  private final String smiles;

  private final List<String> requires = new ArrayList<>();

  private final List<String> neutralLossFormulasPos = new ArrayList<>();
  private final List<Double> neutralLossMassesPos = new ArrayList<>();
  private final List<String> neutralLossReqPos = new ArrayList<>();
  private final List<String> fragmentFormulasPos = new ArrayList<>();
  private final List<Double> fragmentMassesPos = new ArrayList<>();
  private final List<String> fragmentReqPos = new ArrayList<>();
  private final List<String> neutralLossFormulasNeg = new ArrayList<>();

  private final List<Double> neutralLossMassesNeg = new ArrayList<>();
  private final List<String> neutralLossReqNeg = new ArrayList<>();
  private final List<String> fragmentFormulasNeg = new ArrayList<>();
  private final List<Double> fragmentMassesNeg = new ArrayList<>();
  private final List<String> fragmentReqNeg = new ArrayList<>();

  public BuildingBlock(@Nonnull final String name, @Nonnull final String generalFormula,
      @Nonnull final BlockClass blockClass, @Nullable String types, @Nullable final String smiles) {
    this.name = name;
    this.generalFormula = generalFormula;
    this.blockClass = blockClass;
    this.smiles = smiles;
    this.types = types;
  }

  public String getName() {
    return name;
  }

  public String getGeneralFormula() {
    return generalFormula;
  }

  public BlockClass getBlockClass() {
    return blockClass;
  }

  public void addRequires(String req) {
    if (!req.isEmpty()) {
      requires.add(req);
    }
  }

  public List<String> getRequires() {
    return Collections.unmodifiableList(requires);
  }

  public List<String> getNeutralLossFormulasPos() {
    return Collections.unmodifiableList(neutralLossFormulasPos);
  }

  public List<Double> getNeutralLossMassesPos() {
    return Collections.unmodifiableList(neutralLossMassesPos);
  }

  public List<String> getFragmentFormulasPos() {
    return Collections.unmodifiableList(fragmentFormulasPos);
  }

  public List<Double> getFragmentMassesPos() {
    return Collections.unmodifiableList(fragmentMassesPos);
  }

  public List<String> getNeutralLossFormulasNeg() {
    return Collections.unmodifiableList(neutralLossFormulasNeg);
  }

  public List<Double> getNeutralLossMassesNeg() {
    return Collections.unmodifiableList(neutralLossMassesNeg);
  }

  public List<String> getFragmentFormulasNeg() {
    return Collections.unmodifiableList(fragmentFormulasNeg);
  }

  public List<Double> getFragmentMassesNeg() {
    return Collections.unmodifiableList(fragmentMassesNeg);
  }

  public List<String> getNeutralLossReqPos() {
    return neutralLossReqPos;
  }

  public List<String> getFragmentReqPos() {
    return fragmentReqPos;
  }

  public List<String> getNeutralLossReqNeg() {
    return neutralLossReqNeg;
  }

  public List<String> getFragmentReqNeg() {
    return fragmentReqNeg;
  }

  public boolean addNegativeFragment(@Nullable final String str, @Nullable final Double exactMass,
      @Nullable final String req) {
    if (str != null && fragmentFormulasNeg.contains(str)) {
      logger.warning(
          () -> blockClass.name() + " already contains a negative fragment with formula " + str);
      return false;
    }

    if (exactMass != null && fragmentMassesNeg.stream().filter(d -> d != null)
        .anyMatch(d -> Double.compare(d, exactMass) == 0)) {
      logger.warning(
          () -> blockClass.name() + " already contains a negative fragment with mass " + exactMass);
    }

    fragmentFormulasNeg.add(str);
    fragmentMassesNeg.add(exactMass);
    fragmentReqNeg.add(req);
    return true;
  }

  public boolean addNegativeNeutralLoss(@Nullable final String str,
      @Nullable final Double exactMass, @Nullable final String req) {
    if (str != null && neutralLossFormulasNeg.contains(str)) {
      logger.warning(
          () -> blockClass.name() + " already contains a negative neutral loss with formula "
              + str);
      return false;
    }

    if (exactMass != null && neutralLossMassesNeg.stream().filter(d -> d != null)
        .anyMatch(d -> Double.compare(d, exactMass) == 0)) {
      logger.warning(
          () -> blockClass.name() + " already contains a negative neutral loss with mass "
              + exactMass);
    }

    neutralLossFormulasNeg.add(str);
    neutralLossMassesNeg.add(exactMass);
    neutralLossReqNeg.add(req);
    return true;
  }

  public boolean addPositiveFragment(@Nullable final String str, @Nullable final Double exactMass,
      @Nullable final String req) {
    if (str != null && fragmentFormulasPos.contains(str)) {
      logger.warning(
          () -> blockClass.name() + " already contains a positive fragment with formula " + str);
      return false;
    }

    if (exactMass != null && fragmentMassesPos.stream().filter(d -> d != null)
        .anyMatch(d -> Double.compare(d, exactMass) == 0)) {
      logger.warning(
          () -> blockClass.name() + " already contains a positive fragment with mass " + exactMass);
    }

    fragmentFormulasPos.add(str);
    fragmentMassesPos.add(exactMass);
    fragmentReqPos.add(req);
    return true;
  }

  public boolean addPositiveNeutralLoss(@Nullable final String str,
      @Nullable final Double exactMass, @Nullable final String req) {
    if (str != null && neutralLossFormulasPos.contains(str)) {
      logger.warning(
          () -> blockClass.name() + " already contains a positive neutral loss with formula "
              + str);
      return false;
    }

    if (exactMass != null && neutralLossMassesPos.stream().filter(d -> d != null)
        .anyMatch(d -> Double.compare(d, exactMass) == 0)) {
      logger.warning(
          () -> blockClass.name() + " already contains a positive neutral loss with mass "
              + exactMass);
    }

    neutralLossFormulasPos.add(str);
    neutralLossMassesPos.add(exactMass);
    neutralLossReqPos.add(req);
    return true;
  }

  public List<Double> getFragmentMasses(PolarityType polarityType) {
    return polarityType == PolarityType.POSITIVE ? getFragmentMassesPos() : getFragmentMassesNeg();
  }

  public List<Double> getNeutralLossMasses(PolarityType polarityType) {
    return polarityType == PolarityType.POSITIVE ? getNeutralLossMassesPos()
        : getNeutralLossMassesNeg();
  }

  public List<String> getNeutralLossFormulas(PolarityType polarityType) {
    return polarityType == PolarityType.POSITIVE ? getNeutralLossFormulasPos()
        : getNeutralLossFormulasNeg();
  }

  public List<String> getFragmentFormulas(PolarityType polarityType) {
    return polarityType == PolarityType.POSITIVE ? getFragmentFormulasPos()
        : getFragmentFormulasNeg();
  }

  public List<String> getNeutralLossReqs(PolarityType polarityType) {
    return polarityType == PolarityType.POSITIVE ? getNeutralLossReqPos() : getNeutralLossReqNeg();
  }

  public List<String> getFragmentReqs(PolarityType polarityType) {
    return polarityType == PolarityType.POSITIVE ? getFragmentReqPos() : getFragmentReqNeg();
  }

  @Nullable
  public String getTypes() {
    return types;
  }

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    b = b.append(blockClass).append(", ").append(name).append(", ").append(generalFormula);
    for (int i = 0; i < neutralLossFormulasNeg.size(); i++) {
      b.append(", NL(-) ").append(i).append(": [").append(neutralLossFormulasNeg.get(i));
      double mass = Objects.requireNonNullElse(neutralLossMassesNeg.get(i), Double.NaN);
      b.append(", ").append(mzFormat.format(mass)).append(", ").append(neutralLossReqNeg.get(i))
          .append("]");
    }
    for (int i = 0; i < fragmentFormulasNeg.size(); i++) {
      b.append(", F(-) ").append(i).append(": [").append(fragmentFormulasNeg.get(i));
      double mass = Objects.requireNonNullElse(fragmentMassesNeg.get(i), Double.NaN);
      b.append(", ").append(mzFormat.format(mass)).append(", ").append(fragmentReqNeg.get(i))
          .append("]");
    }
    for (int i = 0; i < neutralLossFormulasPos.size(); i++) {
      b.append(", NL(+) ").append(i).append(": [").append(neutralLossFormulasPos.get(i));
      double mass = Objects.requireNonNullElse(neutralLossMassesPos.get(i), Double.NaN);
      b.append(", ").append(mzFormat.format(mass)).append(", ").append(neutralLossReqPos.get(i))
          .append("]");
    }
    for (int i = 0; i < fragmentFormulasPos.size(); i++) {
      b.append(", F(-) ").append(i).append(": [").append(fragmentFormulasPos.get(i));
      double mass = Objects.requireNonNullElse(fragmentMassesPos.get(i), Double.NaN);
      b.append(", ").append(mzFormat.format(mass)).append(", ").append(fragmentReqPos.get(i))
          .append("]");
    }
    return b.toString();
  }
}
