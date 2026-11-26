package io.github.mzmine.datamodel.features.compoundannotations.pfas;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A literature source cited by a PFAS annotation, either for the candidate (compound, class or
 * building block) or for one fragmentation rule. Stored with the annotation so the citation stays
 * available if the catalogue changes.
 *
 * @param sourceId id of the catalogue source, citations with the same id share one reference number
 * @param citation human-readable citation
 * @param link     web link, e.g. the DOI resolver link, or null
 * @param ruleId   id of the cited fragmentation rule, null if the candidate is cited
 * @param context  what the source reports if it is not the candidate itself, e.g. "Sulfonic acid
 *                 was reported in", or null
 */
public record PfasCitation(@NotNull String sourceId, @NotNull String citation,
                           @Nullable String link, @Nullable String ruleId,
                           @Nullable String context) {

  /**
   * @return true if this citation supports the candidate and not a fragmentation rule
   */
  @JsonIgnore
  public boolean isCandidate() {
    return ruleId == null;
  }

  /**
   * @return the citation with its context, e.g. for a tooltip
   */
  public @NotNull String describe() {
    return context == null ? citation : context + ": " + citation;
  }
}
