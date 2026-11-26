package io.github.mzmine.modules.visualization.pfas;

import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasAnnotation;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasBlockEvidence;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasExplainedSignals;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasMatchedPeak;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasRefinementFlag;
import io.github.mzmine.datamodel.features.types.DataTypes;
import io.github.mzmine.datamodel.features.types.annotations.CommentType;
import io.github.mzmine.datamodel.features.types.annotations.pfas.PfasClassType;
import io.github.mzmine.datamodel.features.types.numbers.MzPpmDifferenceType;
import io.github.mzmine.datamodel.features.types.numbers.scores.ExplainedIntensityPercentType;
import io.github.mzmine.datamodel.structures.HarmonizationOptions;
import io.github.mzmine.datamodel.structures.MolecularStructure;
import io.github.mzmine.datamodel.structures.StructureInputType;
import io.github.mzmine.datamodel.structures.StructureParser;
import io.github.mzmine.gui.preferences.NumberFormats;
import io.github.mzmine.javafx.components.factories.FxComboBox;
import io.github.mzmine.javafx.components.factories.FxLabels;
import io.github.mzmine.javafx.components.util.FxLayout;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.javafx.properties.PropertyUtils;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.modules.visualization.molstructure.Structure2DComponent;
import io.github.mzmine.modules.visualization.molstructure.Structure2DRenderer;
import io.github.mzmine.modules.visualization.molstructure.StructureRenderService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.renderer.generators.standard.StandardGenerator;

/**
 * Card with the spectrum, metadata and structure of the selected PFAS annotation, and a combo box
 * to select one of the row's annotations. All content follows the model.
 */
final class PfasAnnotationMatchViewBuilder extends FxViewBuilder<PfasAnnotationMatchModel> {

  private static final double METADATA_WIDTH = 310;

  private final @NotNull PfasMatchColors colors;

  /**
   * @param model  viewer model
   * @param colors dataset colors of the spectrum
   */
  PfasAnnotationMatchViewBuilder(final @NotNull PfasAnnotationMatchModel model,
      final @NotNull PfasMatchColors colors) {
    super(model);
    this.colors = colors;
  }

  @Override
  public @NotNull Region build() {
    final ObservableValue<@Nullable PfasAnnotation> annotation = model.selectedAnnotationProperty();
    final ComboBox<PfasAnnotation> selector = FxComboBox.createComboBox(
        "PFAS annotations of the selected row, best first", model.getAnnotations(),
        model.selectedAnnotationProperty());
    selector.setConverter(new PfasAnnotationConverter());
    selector.setMaxWidth(Double.MAX_VALUE);
    // a single annotation needs no selection
    selector.visibleProperty().bind(Bindings.size(model.getAnnotations()).greaterThan(1));
    selector.managedProperty().bind(selector.visibleProperty());

    final Label title = FxLabels.newBoldTitle(annotation.map(
        a -> Objects.requireNonNullElse(a.getCompoundName(), "PFAS annotation")));

    final Structure2DComponent structureView = createStructureView();
    final Region spectrum = createSpectrum();
    final VBox details = createMetadata(structureView);
    details.setMinWidth(METADATA_WIDTH);
    details.setPrefWidth(METADATA_WIDTH);
    details.setMaxWidth(METADATA_WIDTH);
    final HBox body = FxLayout.newHBox(Pos.TOP_LEFT, Insets.EMPTY, 14, spectrum, details);
    HBox.setHgrow(spectrum, Priority.ALWAYS);

    final VBox card = FxLayout.newVBox(Pos.TOP_LEFT, Insets.EMPTY, true, selector, title, body);
    card.getStyleClass().add("quality-card");
    card.setSpacing(8);
    final VBox content = FxLayout.newVBox(Pos.TOP_LEFT, new Insets(12), true, card);
    final var scroll = FxLayout.newScrollPane(content);
    final Label empty = FxLabels.newLabel("No PFAS annotation for the selected row");
    scroll.visibleProperty().bind(annotation.map(Objects::nonNull).orElse(false));
    empty.visibleProperty().bind(scroll.visibleProperty().not());
    return FxLayout.newStackPane(scroll, empty);
  }

  /**
   * @return the spectrum of the selected annotation; selecting a peak sets the selected m/z
   */
  private @NotNull Region createSpectrum() {
    final PfasMatchSpectrum chart = new PfasMatchSpectrum(colors);
    PropertyUtils.onChange(() -> chart.setAnnotation(model.selectedAnnotationProperty().get(),
        model.signalsProperty().get()), model.selectedAnnotationProperty(), model.signalsProperty());
    chart.cursorPositionProperty().subscribe(position -> model.selectedMzProperty()
        .set(position == null ? null : position.getDomainValue()));

    final Label noSpectrum = FxLabels.newLabel("No matched MS/MS spectrum");
    noSpectrum.visibleProperty().bind(model.selectedAnnotationProperty()
        .map(a -> a.getScan() == null && a.getMatchedPeaks().isEmpty()).orElse(true));
    chart.visibleProperty().bind(noSpectrum.visibleProperty().not());
    final StackPane pane = FxLayout.newStackPane(chart, noSpectrum);
    pane.setMinWidth(360);
    pane.setPrefHeight(210);
    return pane;
  }

  /**
   * @return depiction of the annotated structure, with the substructure of the selected peak
   * highlighted
   */
  private @NotNull Structure2DComponent createStructureView() {
    // own renderer: the outer glow of highlighted substructures must not change other views
    final Structure2DRenderer renderer = StructureRenderService.createRenderer(
        StructureRenderService.FONT);
    renderer.getRenderer2DModel()
        .set(StandardGenerator.Highlighting.class, StandardGenerator.HighlightStyle.OuterGlow);
    renderer.getRenderer2DModel().set(StandardGenerator.OuterGlowWidth.class, 4d);
    final Structure2DComponent view = new Structure2DComponent(null, renderer);
    PropertyUtils.onChange(() -> view.setMolecule(displayedMolecule()),
        model.selectedAnnotationProperty(), model.signalsProperty(),
        model.highlightedStructureProperty());
    return view;
  }

  /**
   * @return the highlighted structure, else the structure of the substructure index, else the
   * annotated structure
   */
  private @Nullable IAtomContainer displayedMolecule() {
    final IAtomContainer highlighted = model.highlightedStructureProperty().get();
    if (highlighted != null) {
      return highlighted;
    }
    final PfasMatchSignals signals = model.signalsProperty().get();
    if (signals != null && signals.molecule() != null) {
      return signals.molecule();
    }
    final PfasAnnotation annotation = model.selectedAnnotationProperty().get();
    final MolecularStructure structure = annotation == null ? null : annotation.getStructure();
    return structure == null ? null : structure.structure();
  }

  /**
   * @param structureView depiction of the structure, highlights selected substructure peaks
   * @return metadata and structural evidence of the selected annotation, bound to the model
   */
  private @NotNull VBox createMetadata(final @NotNull Structure2DComponent structureView) {
    final NumberFormats formats = ConfigService.getGuiFormats();
    final ObservableValue<@Nullable PfasAnnotation> annotation = model.selectedAnnotationProperty();
    final ObservableValue<@Nullable PfasExplainedSignals> subFormulas = model.signalsProperty()
        .map(PfasMatchSignals::subFormulas);

    final Label structureLabel = FxLabels.newBoldLabel(annotation.map(
        a -> a.isInSourceFragment() ? "Parent structure"
            : a.isProposedStructure() ? "Proposed structure" : "Structure"));
    final StackPane structurePane = depiction(structureView, 140);
    final ObservableValue<Boolean> hasStructure = annotation.map(a -> a.getStructure() != null)
        .orElse(false);
    bindShown(structureLabel, hasStructure);
    bindShown(structurePane, hasStructure);

    final VBox blocks = FxLayout.newVBox(Pos.TOP_LEFT, Insets.EMPTY, true);
    blocks.setSpacing(4);
    annotation.subscribe(a -> blocks.getChildren().setAll(blocks(a)));
    // numbered literature references with links, rebuilt for each annotation
    final VBox level = FxLayout.newVBox(Pos.TOP_LEFT, Insets.EMPTY, true);
    annotation.subscribe(a -> level.getChildren().setAll(PfasCitationNodes.level(a)));
    final VBox candidateSource = FxLayout.newVBox(Pos.TOP_LEFT, Insets.EMPTY, true);
    annotation.subscribe(
        a -> candidateSource.getChildren().setAll(PfasCitationNodes.candidateSource(a)));
    final VBox rules = FxLayout.newVBox(Pos.TOP_LEFT, Insets.EMPTY, true);
    rules.setSpacing(2);
    annotation.subscribe(a -> rules.getChildren().setAll(PfasCitationNodes.rules(a)));

    final VBox details = FxLayout.newVBox(Pos.TOP_LEFT, Insets.EMPTY, true,
        level, candidateSource,
        detail("PFAS class", annotation, a -> a.get(PfasClassType.class)),
        detail("Formula", annotation, PfasAnnotation::getFormula),
        detail("Ion", annotation, PfasAnnotation::getAdductType),
        detail("Observed precursor m/z", model.selectedRowProperty(),
            row -> formats.mz(row.getAverageMZ())),
        detail("Calculated precursor m/z", annotation,
            a -> a.get(MzPpmDifferenceType.class) == null ? null : formats.mz(a.getPrecursorMZ())),
        detail("Mass error (ppm)", annotation, a -> formats.ppm(a.get(MzPpmDifferenceType.class))),
        detail("Scan", annotation, PfasAnnotationMatchViewBuilder::scanLabel),
        detail("Rule-based signals", annotation,
            a -> a.getMatchedPeaks().isEmpty() ? null : a.getMatchedPeaks().size()),
        detail("Substructure signals", subFormulas, s -> s.subFormulaPeaks().stream()
            .filter(PfasExplainedSignals.SubFormulaPeak::isSubstructure).count()),
        detail("Sub-formula signals", subFormulas, s -> s.subFormulaPeaks().stream()
            .filter(p -> !p.isSubstructure()).count()),
        detail("Explained intensity", annotation, a -> {
          final Float explained = a.get(ExplainedIntensityPercentType.class);
          return explained == null ? null
              : DataTypes.get(ExplainedIntensityPercentType.class).getFormattedString(explained);
        }),
        detail("MS/MS parent row", annotation, PfasAnnotation::getInSourceParentRowId),
        detail("Refinement", annotation,
            a -> a.getFlags().stream().map(PfasRefinementFlag::toString)
                .collect(Collectors.joining(", "))), rules, structureLabel, structurePane, blocks,
        detail("Notes", annotation, a -> a.get(CommentType.class)));
    details.setSpacing(4);
    return details;
  }

  /**
   * @param annotation selected annotation, or null
   * @return building block evidence if the annotation has no structure
   */
  private static @NotNull List<VBox> blocks(final @Nullable PfasAnnotation annotation) {
    if (annotation == null || annotation.getStructure() != null || annotation.getBuildingBlocks()
        .isEmpty()) {
      return List.of();
    }
    final String label =
        annotation.isInSourceFragment() ? "Parent building blocks" : "Building blocks";
    final Double candidateMz =
        annotation.get(MzPpmDifferenceType.class) != null ? annotation.getPrecursorMZ() : null;
    final VBox header = FxLayout.newVBox(Pos.TOP_LEFT, Insets.EMPTY, true,
        FxLabels.newBoldLabel(label));
    final List<VBox> boxes = new ArrayList<>();
    boxes.add(header);
    for (final PfasBlockEvidence block : annotation.getBuildingBlocks()) {
      boxes.add(blockDetails(annotation, block, candidateMz));
    }
    return boxes;
  }

  /**
   * @param annotation  owning annotation with matched fragment peaks
   * @param block       building block with optional open attachment points
   * @param candidateMz calculated candidate precursor, if available
   * @return depiction and a summary of the available evidence
   */
  private static @NotNull VBox blockDetails(final @NotNull PfasAnnotation annotation,
      final @NotNull PfasBlockEvidence block, final @Nullable Double candidateMz) {
    final VBox box = FxLayout.newVBox(Pos.TOP_LEFT, new Insets(5, 0, 5, 6), true,
        FxLabels.newBoldLabel(block.name()));
    box.setSpacing(3);
    addDetail(box, "Formula", block.formula());
    final MolecularStructure structure = StructureParser.silent()
        .parseStructure(block.smiles(), StructureInputType.SMILES, HarmonizationOptions.NONE);
    if (structure != null) {
      box.getChildren().add(depiction(new Structure2DComponent(structure.structure()), 85));
    }
    final NumberFormats formats = ConfigService.getGuiFormats();
    final List<PfasMatchedPeak> peaks = annotation.getMatchedPeaks();
    final String fragments = block.peakIndices().stream()
        .filter(index -> index >= 0 && index < peaks.size()).map(peaks::get)
        .map(peak -> "%s (m/z %s)".formatted(peak.ionLabel(), formats.mz(peak.observedMz())))
        .collect(Collectors.joining(", "));
    if (!fragments.isEmpty()) {
      addDetail(box, "Rule-matched fragments", fragments);
    } else if (annotation.isInSourceFragment()) {
      addDetail(box, "Evidence",
          "Linked to MS/MS parent row " + annotation.getInSourceParentRowId());
    } else if (candidateMz != null) {
      addDetail(box, "Evidence", "Candidate precursor m/z " + formats.mz(candidateMz));
    }
    return box;
  }

  /**
   * @param annotation annotation with an optional scan
   * @return raw data file and scan number, or null without scan
   */
  private static @Nullable String scanLabel(final @NotNull PfasAnnotation annotation) {
    final Scan scan = annotation.getScan();
    return scan == null ? null
        : "%s · #%d".formatted(scan.getDataFile().getName(), scan.getScanNumber());
  }

  /**
   * @param field  metadata label
   * @param source observed source of the value
   * @param value  extracts the value, may return null
   * @param <T>    source type
   * @return label bound to the value, hidden if the value is null or blank
   */
  private static <T> @NotNull Label detail(final @NotNull String field,
      final @NotNull ObservableValue<@Nullable T> source,
      final @NotNull Function<@NotNull T, @Nullable Object> value) {
    final Label label = FxLabels.newLabel(source.map(value).map(v -> {
      final String text = v.toString();
      return text.isBlank() ? null : field + "  " + text;
    }));
    label.setTextAlignment(TextAlignment.LEFT);
    label.setMinHeight(Region.USE_PREF_SIZE);
    return FxLabels.bindVisibleToText(label, true);
  }

  /**
   * @param details pane receiving the detail
   * @param field   metadata label
   * @param value   value to display when available
   */
  private static void addDetail(final @NotNull VBox details, final @NotNull String field,
      final @Nullable Object value) {
    if (value != null && !value.toString().isBlank()) {
      final Label detail = FxLabels.newLabel(field + "  " + value);
      detail.setTextAlignment(TextAlignment.LEFT);
      detail.setMinHeight(Region.USE_PREF_SIZE);
      details.getChildren().add(detail);
    }
  }

  /**
   * @param region region to show or hide
   * @param shown  true if the region is shown and takes layout space
   */
  private static void bindShown(final @NotNull Region region,
      final @NotNull ObservableValue<Boolean> shown) {
    region.visibleProperty().bind(shown);
    region.managedProperty().bind(shown);
  }

  /**
   * @param view   structure depiction
   * @param height fixed height
   * @return the depiction in a pane of this height
   */
  private static @NotNull StackPane depiction(final @NotNull Structure2DComponent view,
      final double height) {
    final StackPane pane = new StackPane(view);
    pane.setMinHeight(height);
    pane.setPrefHeight(height);
    pane.setMaxHeight(height);
    return pane;
  }
}
