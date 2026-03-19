/*
 * Copyright (c) 2004-2026 The mzmine Development Team
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.modules.dataprocessing.filter_lipidannotationcleanup;

import io.github.mzmine.parameters.impl.IonMobilitySupport;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.BooleanParameter;
import io.github.mzmine.parameters.parametertypes.ComboParameter;
import io.github.mzmine.parameters.parametertypes.IonizationPreferenceParameter;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsParameter;
import org.jetbrains.annotations.NotNull;

/**
 * Parameters for {@link LipidAnnotationCleanupModule}. The module removes duplicate lipid
 * annotations that appear on multiple feature list rows, keeping the best candidate per
 * annotation.
 *
 * <p>Winner selection is driven by the {@link IonizationPreferenceParameter}: if an
 * {@link IonizationPreference} rule matches a lipid class, that class uses ionization-first
 * comparison. Classes with no matching rule default to highest-score selection.
 */
public class LipidAnnotationCleanupParameters extends SimpleParameterSet {

  public static final FeatureListsParameter featureLists = new FeatureListsParameter();

  public static final IonizationPreferenceParameter ionizationPreferences = new IonizationPreferenceParameter();

  public static final ComboParameter<MultiRowAnnotationCleanupRowHandlingMode> rowHandlingMode = new ComboParameter<>(
      "Row handling mode",
      "Defines how remaining annotations on a row are treated when one of its lipid "
          + "annotations is removed during the multi-row cleanup.",
      MultiRowAnnotationCleanupRowHandlingMode.values(),
      MultiRowAnnotationCleanupRowHandlingMode.DISCARD_LOWER_THAN_REMOVED);

  public static final BooleanParameter includeRetentionTimeInScoring = new BooleanParameter(
      "Include retention time in scoring",
      "Whether retention time elution-order analysis factors into the combined "
          + "annotation quality score used for winner selection.", true);

  public LipidAnnotationCleanupParameters() {
    super(featureLists, ionizationPreferences, rowHandlingMode, includeRetentionTimeInScoring);
  }

  @Override
  public @NotNull IonMobilitySupport getIonMobilitySupport() {
    return IonMobilitySupport.SUPPORTED;
  }
}
