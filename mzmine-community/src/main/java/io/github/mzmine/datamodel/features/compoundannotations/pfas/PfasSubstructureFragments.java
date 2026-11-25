package io.github.mzmine.datamodel.features.compoundannotations.pfas;

import java.util.ArrayDeque;
import io.github.mzmine.util.FormulaUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.graph.Cycles;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.interfaces.IIsotope;
import org.openscience.cdk.interfaces.IMolecularFormula;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;

/**
 * Element compositions of all connected substructures of a molecule that remain after cutting up
 * to {@value #MAX_CUTS} non-ring bonds, like the bond disconnection of MetFrag. Used to prefer
 * sub-formulas of the precursor that are a substructure of the annotated structure. Structures with
 * a CH next to a CF, where fluorotelomers eliminate HF, also accept a substructure minus n HF. Per
 * composition the substructure with the fewest cut bonds is kept with its atoms for depictions.
 */
public final class PfasSubstructureFragments {

  /**
   * Hydrogen shifts during fragment ion formation, e.g. the deprotonated precursor or H transfer
   */
  public static final int H_TOLERANCE = 2;
  // the nested loops of of(String) cut up to three bonds
  private static final int MAX_CUTS = 3;
  private static final PfasSubstructureFragments EMPTY = new PfasSubstructureFragments(null,
      Map.of(), false);
  private static final Logger logger = Logger.getLogger(PfasSubstructureFragments.class.getName());

  /**
   * A substructure that explains a fragment.
   *
   * @param atoms    indices of the substructure atoms in {@link #molecule()}
   * @param cutBonds indices of the bonds cut to release the substructure
   * @param hfLoss   number of HF eliminated from the substructure
   */
  public record Match(@NotNull List<Integer> atoms, @NotNull List<Integer> cutBonds,
                      int hfLoss) {

    public Match {
      atoms = List.copyOf(atoms);
      cutBonds = List.copyOf(cutBonds);
    }
  }

  private final @Nullable IAtomContainer molecule;
  // heavy atom composition, e.g. C3F7, to the first substructure per hydrogen count
  private final @NotNull Map<String, Map<Integer, Match>> fragments;
  // most H of any fragment, bounds the HF eliminations
  private final int maxHydrogens;
  private final boolean hfElimination;

  private PfasSubstructureFragments(@Nullable IAtomContainer molecule,
      @NotNull Map<String, Map<Integer, Match>> fragments, boolean hfElimination) {
    this.molecule = molecule;
    this.fragments = fragments;
    this.hfElimination = hfElimination;
    maxHydrogens = fragments.values().stream().flatMap(m -> m.keySet().stream())
        .mapToInt(Integer::intValue).max().orElse(0);
  }

  /**
   * @param smiles structure, or null
   * @return the fragments of the structure, empty if there is none or it cannot be parsed
   */
  public static @NotNull PfasSubstructureFragments of(@Nullable String smiles) {
    if (smiles == null || smiles.isBlank()) {
      return EMPTY;
    }
    final IAtomContainer molecule;
    try {
      molecule = AtomContainerManipulator.suppressHydrogens(
          new SmilesParser(SilentChemObjectBuilder.getInstance()).parseSmiles(smiles));
    } catch (InvalidSmilesException e) {
      logger.fine(() -> "Cannot parse PFAS structure %s: %s".formatted(smiles, e.getMessage()));
      return EMPTY;
    }
    Cycles.markRingAtomsAndBonds(molecule);
    // decision: terminal halogens stay attached, losing single F atoms is no disconnection; F count
    // changes only come from HF elimination
    final List<IBond> cuttable = StreamSupport.stream(molecule.bonds().spliterator(), false)
        .filter(b -> !b.isInRing() && !isTerminalHalogen(b.getBegin()) && !isTerminalHalogen(
            b.getEnd())).toList();

    final Map<String, Map<Integer, Match>> fragments = new HashMap<>();
    final Set<IBond> cut = new HashSet<>();
    final int n = cuttable.size();
    for (int i = 0; i < n; i++) {
      cut.add(cuttable.get(i));
      addComponents(molecule, cut, fragments);
      for (int j = i + 1; j < n; j++) {
        cut.add(cuttable.get(j));
        addComponents(molecule, cut, fragments);
        for (int k = j + 1; k < n; k++) {
          cut.add(cuttable.get(k));
          addComponents(molecule, cut, fragments);
          cut.remove(cuttable.get(k));
        }
        cut.remove(cuttable.get(j));
      }
      cut.remove(cuttable.get(i));
    }
    return new PfasSubstructureFragments(molecule, fragments, hasHfJunction(molecule));
  }

  /**
   * @return the parsed structure with suppressed hydrogens that the atom and bond indices of
   * {@link Match} refer to, or null if there is no structure
   */
  public @Nullable IAtomContainer molecule() {
    return molecule;
  }

  /**
   * @param formula fragment ion formula without charge, e.g. C7F15
   * @return the substructure of {@link #find(Map)}, or null if there is none or the formula cannot
   * be parsed
   */
  public @Nullable Match find(@NotNull String formula) {
    final IMolecularFormula parsed = FormulaUtils.parse(formula);
    if (parsed == null) {
      return null;
    }
    final Map<String, Integer> counts = new HashMap<>();
    for (final IIsotope isotope : parsed.isotopes()) {
      counts.merge(isotope.getSymbol(), parsed.getIsotopeCount(isotope), Integer::sum);
    }
    return find(counts);
  }

  /**
   * @param elementCounts fragment ion formula as element counts, H included
   * @return true if {@link #find(Map)} finds a substructure
   */
  public boolean contains(@NotNull Map<String, Integer> elementCounts) {
    return find(elementCounts) != null;
  }

  /**
   * @param elementCounts fragment ion formula as element counts, H included
   * @return a substructure with the same heavy atoms and a hydrogen count within
   * {@link #H_TOLERANCE}, or if the structure has a CH-CF junction a substructure minus n HF; null
   * if there is none
   */
  public @Nullable Match find(@NotNull Map<String, Integer> elementCounts) {
    final Map<String, Integer> heavy = new TreeMap<>(elementCounts);
    final int h = heavy.getOrDefault("H", 0);
    heavy.remove("H");
    // decision: HF eliminations only where the HF ladder rule applies, at CH next to CF
    final int maxHf = hfElimination ? maxHydrogens : 0;
    for (int hf = 0; hf <= maxHf; hf++) {
      final Map<String, Integer> lost = new TreeMap<>(heavy);
      lost.merge("F", hf, Integer::sum);
      final Map<Integer, Match> byHydrogens = fragments.get(key(lost));
      if (byHydrogens == null) {
        continue;
      }
      for (int d = -H_TOLERANCE; d <= H_TOLERANCE; d++) {
        final Match match = byHydrogens.get(h + hf + d);
        if (match != null) {
          return hf == 0 ? match : new Match(match.atoms(), match.cutBonds(), hf);
        }
      }
    }
    return null;
  }

  /**
   * @return true if a carbon with H is bonded to a carbon with F, the junction where fluorotelomers
   * eliminate HF
   */
  private static boolean hasHfJunction(@NotNull IAtomContainer molecule) {
    for (final IBond bond : molecule.bonds()) {
      final IAtom a = bond.getBegin();
      final IAtom b = bond.getEnd();
      if (isCarbonWithH(a) && isCarbonWithF(b) || isCarbonWithH(b) && isCarbonWithF(a)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isTerminalHalogen(@NotNull IAtom atom) {
    return atom.getBondCount() == 1 && switch (atom.getSymbol()) {
      case "F", "Cl", "Br", "I" -> true;
      default -> false;
    };
  }

  private static boolean isCarbonWithH(@NotNull IAtom atom) {
    return "C".equals(atom.getSymbol()) && atom.getImplicitHydrogenCount() != null
        && atom.getImplicitHydrogenCount() > 0;
  }

  private static boolean isCarbonWithF(@NotNull IAtom atom) {
    if (!"C".equals(atom.getSymbol())) {
      return false;
    }
    for (final IBond bond : atom.bonds()) {
      if ("F".equals(bond.getOther(atom).getSymbol())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Adds every connected component that remains after removing the cut bonds, if its composition
   * and hydrogen count are new.
   */
  private static void addComponents(@NotNull IAtomContainer molecule, @NotNull Set<IBond> cut,
      @NotNull Map<String, Map<Integer, Match>> fragments) {
    final Set<IAtom> visited = new HashSet<>();
    for (final IAtom start : molecule.atoms()) {
      if (!visited.add(start)) {
        continue;
      }
      final Map<String, Integer> heavy = new TreeMap<>();
      final List<IAtom> component = new ArrayList<>();
      int h = 0;
      final ArrayDeque<IAtom> queue = new ArrayDeque<>(List.of(start));
      while (!queue.isEmpty()) {
        final IAtom atom = queue.poll();
        component.add(atom);
        heavy.merge(atom.getSymbol(), 1, Integer::sum);
        h += atom.getImplicitHydrogenCount() == null ? 0 : atom.getImplicitHydrogenCount();
        for (final IBond bond : atom.bonds()) {
          final IAtom other = bond.getOther(atom);
          if (!cut.contains(bond) && visited.add(other)) {
            queue.add(other);
          }
        }
      }
      // decision: per composition the substructure released by the fewest cut bonds
      final List<IBond> ownCuts = cut.stream()
          .filter(b -> component.contains(b.getBegin()) || component.contains(b.getEnd()))
          .toList();
      final Map<Integer, Match> byHydrogens = fragments.computeIfAbsent(key(heavy),
          _ -> new HashMap<>());
      final Match existing = byHydrogens.get(h);
      if (existing == null || existing.cutBonds().size() > ownCuts.size()) {
        byHydrogens.put(h, new Match(component.stream().map(molecule::indexOf).toList(),
            ownCuts.stream().map(molecule::indexOf).toList(), 0));
      }
    }
  }

  private static @NotNull String key(@NotNull Map<String, Integer> sortedHeavy) {
    final StringBuilder key = new StringBuilder();
    sortedHeavy.forEach((element, count) -> {
      if (count > 0) {
        key.append(element).append(count);
      }
    });
    return key.toString();
  }
}
