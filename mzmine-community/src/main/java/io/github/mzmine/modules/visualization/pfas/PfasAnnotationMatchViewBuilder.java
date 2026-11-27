package io.github.mzmine.modules.visualization.pfas;

import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasAnnotation;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasBlockEvidence;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasExplainedSignals;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasExplainedSignalsMode;
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
import io.github.mzmine.javafx.components.LabeledProgressBar;
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
import javafx.scene.Node;
import javafx.scene.control.Accordion;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
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

  private static final double SIDE_WIDTH = 340;

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

    // spectrum in the center, name, structure and metadata on the right
    final VBox side = createSidePanel();
    side.setPadding(new Insets(0, 4, 0, 0));
    final ScrollPane sideScroll = FxLayout.newScrollPane(side);
    sideScroll.setFitToWidth(true);
    sideScroll.setHbarPolicy(ScrollBarPolicy.NEVER);
    sideScroll.setMinWidth(SIDE_WIDTH);
    sideScroll.setPrefWidth(SIDE_WIDTH);
    final BorderPane card = new BorderPane(createSpectrum());
    card.setTop(selector);
    card.setRight(sideScroll);
    BorderPane.setMargin(selector, new Insets(0, 0, 8, 0));
    BorderPane.setMargin(sideScroll, new Insets(0, 0, 0, 12));
    card.setPadding(new Insets(12));

    final Label empty = FxLabels.newLabel("No PFAS annotation for the selected row");
    card.visibleProperty().bind(annotation.map(Objects::nonNull).orElse(false));
    empty.visibleProperty().bind(card.visibleProperty().not());
    return FxLayout.newStackPane(card, empty);
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
    pane.setMinWidth(300);
    // decision: the chart fills the center, its default max height would leave a gap below
    chart.setMaxHeight(Double.MAX_VALUE);
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
   * Name with explained intensity, structure, classification and the precursor and signal details
   * in an accordion, all bound to the selected annotation.
   *
   * @return the side panel next to the spectrum
   */
  private @NotNull VBox createSidePanel() {
    final NumberFormats formats = ConfigService.getGuiFormats();
    final ObservableValue<@Nullable PfasAnnotation> annotation = model.selectedAnnotationProperty();
    final ObservableValue<@Nullable PfasExplainedSignals> subFormulas = model.signalsProperty()
        .map(PfasMatchSignals::subFormulas);

    final Label name = FxLabels.newBoldTitle(annotation.map(
        a -> Objects.requireNonNullElse(a.getCompoundName(), "PFAS annotation")));
    name.setWrapText(true);
    name.setMinHeight(Region.USE_PREF_SIZE);
    name.setMaxWidth(Double.MAX_VALUE);
    final ObservableValue<@Nullable Float> explainedValue = annotation.map(
        a -> a.get(ExplainedIntensityPercentType.class));
    final Region explained = percentBar("Explained intensity: ",
        explainedValue.map(Float::doubleValue), 175,
        "Share of the MS/MS signal intensity explained by the annotation");
    // the bar stays right aligned, a long name wraps
    final HBox header = FxLayout.newHBox(Pos.TOP_LEFT, Insets.EMPTY, 8, name, explained);
    HBox.setHgrow(name, Priority.ALWAYS);

    final StackPane structurePane = depiction(createStructureView(), 160);
    bindShown(structurePane, annotation.map(a -> a.getStructure() != null).orElse(false));
    // caption with the origin of the structure, hidden for building blocks without structure
    final Label structureCaption = FxLabels.bindVisibleToText(
        FxLabels.newItalicLabel(annotation.map(
            a -> a.getStructure() == null ? null : a.isInSourceFragment() ? "Parent structure"
                : a.isProposedStructure() ? "Proposed structure" : "Catalogued structure")),
        true);
    final Tooltip proposedTooltip = structureTooltip("""
        Proposed structure, not a definitive identification. It is assembled computationally from \
        building blocks that match fragment ions, not from a reference standard or library \
        spectrum.""");
    final Tooltip cataloguedTooltip = structureTooltip("""
        Structure of a literature-reported compound in the PFAS catalogue. The annotation is \
        based on exact mass and fragment ions, not confirmed by a reference standard or library \
        spectrum.""");
    structureCaption.tooltipProperty().bind(
        annotation.map(a -> a.isProposedStructure() ? proposedTooltip : cataloguedTooltip));
    // HF loss or H rearrangement between the highlighted substructure and the selected fragment
    final Label highlightNote = FxLabels.bindVisibleToText(
        FxLabels.newBoldLabel(model.highlightNoteProperty()), true);
    // highlighted with the glow color of the substructure in the structure view
    highlightNote.setPadding(new Insets(1, 5, 1, 5));
    highlightNote.backgroundProperty().bind(model.highlightColorProperty().map(
        color -> new Background(new BackgroundFill(color, new CornerRadii(3), Insets.EMPTY))));
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

    final VBox precursor = detailBox(detail("Formula", annotation, PfasAnnotation::getFormula),
        detail("Ion", annotation, PfasAnnotation::getAdductType),
        detail("Observed precursor m/z", model.selectedRowProperty(),
            row -> formats.mz(row.getAverageMZ())),
        detail("Calculated precursor m/z", annotation,
            a -> a.get(MzPpmDifferenceType.class) == null ? null : formats.mz(a.getPrecursorMZ())),
        detail("Mass error (ppm)", annotation, a -> formats.ppm(a.get(MzPpmDifferenceType.class))),
        detail("Scan", annotation, PfasAnnotationMatchViewBuilder::scanLabel));
    // cumulative explained intensity: rules, + substructures, + sub-formulas; null without settings
    final VBox signals = detailBox(signalRow(detail("Rule-based signals", annotation,
                a -> a.getMatchedPeaks().isEmpty() ? null : a.getMatchedPeaks().size()),
            subFormulas.map(s -> explained(s, PfasExplainedSignalsMode.RULES))),
        signalRow(detail("Substructure signals", subFormulas, s -> s.subFormulaPeaks().stream()
                .filter(PfasExplainedSignals.SubFormulaPeak::isSubstructure).count()),
            subFormulas.map(s -> explained(s, PfasExplainedSignalsMode.RULES_AND_SUBSTRUCTURES))),
        signalRow(detail("Sub-formula signals", subFormulas, s -> s.subFormulaPeaks().stream()
                .filter(p -> !p.isSubstructure()).count()),
            subFormulas.map(s -> explained(s, PfasExplainedSignalsMode.ALL))), rules);
    final Accordion accordion = FxLayout.newAccordion(true,
        FxLayout.newTitledPane("Precursor", precursor),
        FxLayout.newTitledPane("Signals", signals));

    final VBox side = FxLayout.newVBox(Pos.TOP_LEFT, Insets.EMPTY, true, header, structurePane,
        structureCaption, highlightNote, blocks, level, candidateSource,
        detail("PFAS class", annotation, a -> a.get(PfasClassType.class)),
        detail("MS/MS parent row", annotation, PfasAnnotation::getInSourceParentRowId),
        detail("Refinement", annotation,
            a -> a.getFlags().stream().map(PfasRefinementFlag::toString)
                .collect(Collectors.joining(", "))), accordion,
        detail("Notes", annotation, a -> a.get(CommentType.class)));
    side.setSpacing(4);
    VBox.setMargin(accordion, new Insets(6, 0, 4, 0));
    return side;
  }

  /**
   * @param signals explained signals with the total intensity
   * @param mode    signals that count as explained
   * @return explained fraction of the total intensity, null if there is no counted intensity
   */
  private static @Nullable Double explained(final @NotNull PfasExplainedSignals signals,
      final @NotNull PfasExplainedSignalsMode mode) {
    return signals.totalIntensity() > 0 ? signals.explained(mode) : null;
  }

  /**
   * @param detail   detail label of one signal type, the row hides with it
   * @param fraction explained fraction of the total intensity, no bar if null
   * @return the label with the explained intensity as right aligned bar
   */
  private static @NotNull HBox signalRow(final @NotNull Label detail,
      final @NotNull ObservableValue<@Nullable Double> fraction) {
    final Region bar = percentBar("", fraction, 70,
        "Share of the MS/MS signal intensity explained by these and the signal types above");
    final HBox row = FxLayout.newHBox(Pos.CENTER_LEFT, Insets.EMPTY, 8, detail, bar);
    detail.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(detail, Priority.ALWAYS);
    row.visibleProperty().bind(detail.visibleProperty());
    row.managedProperty().bind(detail.visibleProperty());
    return row;
  }

  /**
   * @param prefix   text before the percentage
   * @param fraction value from 0 to 1, hides the bar if null
   * @param width    bar width
   * @param tooltip  explains the value
   * @return a bar with the formatted percentage on it
   */
  private static @NotNull Region percentBar(final @NotNull String prefix,
      final @NotNull ObservableValue<@Nullable Double> fraction, final double width,
      final @NotNull String tooltip) {
    final ExplainedIntensityPercentType type = DataTypes.get(ExplainedIntensityPercentType.class);
    final LabeledProgressBar bar = new LabeledProgressBar(
        fraction.map(v -> Math.clamp(v, 0d, 1d)).orElse(0d), Bindings.createStringBinding(
        () -> fraction.getValue() == null ? ""
            : prefix + type.getFormattedString(fraction.getValue().floatValue()), fraction));
    bar.visibleProperty().bind(fraction.map(Objects::nonNull).orElse(false));
    bar.setMinWidth(width);
    bar.setPrefWidth(width);
    bar.setMaxWidth(width);
    bar.setMaxHeight(22);
    Tooltip.install(bar, new Tooltip(tooltip));
    return bar;
  }

  /**
   * @param origin how the structure was obtained
   * @return tooltip of the structure caption with the branched isomer warning
   */
  private static @NotNull Tooltip structureTooltip(final @NotNull String origin) {
    final Tooltip tooltip = new Tooltip(origin + """

        The chain is drawn linear: branched chain isomers have the same fragments and cannot be \
        distinguished here.""");
    tooltip.setWrapText(true);
    tooltip.setMaxWidth(420);
    return tooltip;
  }

  /**
   * @param nodes detail rows
   * @return the rows in a vertical box for a titled pane
   */
  private static @NotNull VBox detailBox(final @NotNull Node... nodes) {
    final VBox box = FxLayout.newVBox(Pos.TOP_LEFT, new Insets(6), true, nodes);
    box.setSpacing(4);
    return box;
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
        : "%s - #%s".formatted(scan.getDataFile().getName(), scan.getScanDefinition());
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
      return text.isBlank() ? null : field + ": " + text;
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
      final Label detail = FxLabels.newLabel(field + ": " + value);
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
