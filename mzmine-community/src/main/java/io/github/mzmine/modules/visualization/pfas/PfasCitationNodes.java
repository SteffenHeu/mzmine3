package io.github.mzmine.modules.visualization.pfas;

import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasAnnotation;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasCitation;
import io.github.mzmine.datamodel.features.compoundannotations.pfas.PfasConfidenceLevel;
import io.github.mzmine.gui.DesktopService;
import io.github.mzmine.javafx.components.factories.FxLabels;
import io.github.mzmine.javafx.components.factories.FxTextFlows;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Numbered literature references of a PFAS annotation, e.g. "Known [1] [2]". Numbers follow the
 * first appearance of a source in {@link PfasAnnotation#getCitations()}, so the candidate and the
 * rules share them. Each number shows its citation as tooltip and opens the link if available.
 */
final class PfasCitationNodes {

  private PfasCitationNodes() {
  }

  /**
   * @param annotation selected annotation, or null
   * @return the PFAS level, always citing the publication of the levels; empty without level, e.g.
   * for in-source fragments
   */
  static @NotNull List<Node> level(final @Nullable PfasAnnotation annotation) {
    if (annotation == null || annotation.getConfidenceLevel() == null) {
      return List.of();
    }
    final TextFlow flow = FxTextFlows.newTextFlow(
        new Text("PFAS level: " + annotation.getConfidenceLevel()));
    flow.getChildren().addAll(references(annotation, c -> c == PfasConfidenceLevel.CITATION));
    return List.of(flow);
  }

  /**
   * @param annotation selected annotation, or null
   * @return the candidate source with its references, empty without annotation
   */
  static @NotNull List<Node> candidateSource(final @Nullable PfasAnnotation annotation) {
    if (annotation == null || annotation.getCandidateSource() == null) {
      return List.of();
    }
    final TextFlow flow = FxTextFlows.newTextFlow(
        new Text("Candidate source: " + annotation.getCandidateSource()));
    flow.getChildren().addAll(references(annotation,
        c -> c.isCandidate() && c != PfasConfidenceLevel.CITATION));
    return List.of(flow);
  }

  /**
   * @param annotation selected annotation, or null
   * @return the matched fragmentation rules with their references, empty without matched rules
   */
  static @NotNull List<Node> rules(final @Nullable PfasAnnotation annotation) {
    if (annotation == null) {
      return List.of();
    }
    final List<String> ruleIds = annotation.getMatchedPeaks().stream()
        .flatMap(peak -> peak.ruleIds().stream()).distinct().toList();
    if (ruleIds.isEmpty()) {
      return List.of();
    }
    final List<Node> nodes = new ArrayList<>();
    nodes.add(FxLabels.newBoldLabel("Fragmentation rules"));
    for (final String ruleId : ruleIds) {
      final TextFlow flow = FxTextFlows.newTextFlow(new Text(ruleId));
      flow.getChildren().addAll(references(annotation, c -> ruleId.equals(c.ruleId())));
      flow.setPadding(new Insets(0, 0, 0, 6));
      nodes.add(flow);
    }
    return nodes;
  }

  /**
   * @param annotation annotation with citations
   * @param filter     citations to show
   * @return one " [n]" node per cited source, in reference number order
   */
  private static @NotNull List<Node> references(final @NotNull PfasAnnotation annotation,
      final @NotNull Predicate<PfasCitation> filter) {
    // the level publication is shown first, so it is always [1]
    final List<PfasCitation> all = new ArrayList<>();
    all.add(PfasConfidenceLevel.CITATION);
    all.addAll(annotation.getCitations());
    final List<String> numbering = all.stream().map(PfasCitation::sourceId).distinct().toList();
    // one reference per source, a source cited for several blocks lists every context
    final Map<String, List<PfasCitation>> bySource = all.stream().filter(filter).collect(
            Collectors.groupingBy(PfasCitation::sourceId, LinkedHashMap::new,
                Collectors.toList()));
    // insertion order of the filtered citations is the reference number order
    final List<Node> nodes = new ArrayList<>();
    bySource.forEach((sourceId, citations) -> {
      nodes.add(new Text(" "));
      nodes.add(reference(numbering.indexOf(sourceId) + 1, citations));
    });
    return nodes;
  }

  /**
   * @param number    reference number
   * @param citations citations of one source
   * @return "[n]" as hyperlink if the source has a link, else as label, with the citations as
   * tooltip
   */
  private static @NotNull Control reference(final int number,
      final @NotNull List<PfasCitation> citations) {
    final String text = "[%d]".formatted(number);
    final String link = citations.getFirst().link();
    final Control reference;
    if (link != null) {
      final Hyperlink hyperlink = new Hyperlink(text);
      hyperlink.setOnAction(_ -> DesktopService.getDesktop().openWebPage(link));
      hyperlink.setPadding(Insets.EMPTY);
      reference = hyperlink;
    } else {
      reference = new Label(text);
    }
    final Tooltip tooltip = new Tooltip(citations.stream().map(PfasCitation::describe).distinct()
        .collect(Collectors.joining("\n")) + (link == null ? "" : "\n" + link));
    tooltip.setShowDelay(Duration.millis(200));
    tooltip.setWrapText(true);
    tooltip.setMaxWidth(420);
    reference.setTooltip(tooltip);
    return reference;
  }
}
