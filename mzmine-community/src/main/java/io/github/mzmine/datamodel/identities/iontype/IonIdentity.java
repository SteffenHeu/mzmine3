/*
 * Copyright (c) 2004-2026 The mzmine Development Team
 *
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

package io.github.mzmine.datamodel.identities.iontype;

import io.github.mzmine.modules.dataprocessing.group_metacorrelate.corrgrouping.CorrelateGroupingTask;
import io.github.mzmine.modules.dataprocessing.id_ion_identity_networking.formula.prediction.FormulaPredictionIonNetworkModule;
import io.github.mzmine.modules.io.export_features_gnps.fbmn.GnpsFbmnExportAndSubmitModule;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * IonIdentities are connected to {@link IonNetwork}s and represent different ion species (M+H,
 * M+Na, 2M+H, ...) for the same molecule. Typically {@link CorrelateGroupingTask} is performed
 * before identifying ion identities. They can be used to predict molecular formulas in
 * {@link FormulaPredictionIonNetworkModule} and they are part of the Ion Identity Molecular
 * Networking workflow on https://gnps.ucsd.edu/, which is accessible through
 * {@link GnpsFbmnExportAndSubmitModule}.
 * <p>
 * IonIdentity is intentionally narrow: it holds the adduct type and a back-reference to its
 * owning network. All other per-ion state (notably candidate molecular formulas) now lives on
 * {@link IonNetwork} so that ions in the same network share a single source of truth and the
 * network can be persisted once instead of duplicated across every member row.
 */
public class IonIdentity implements Comparable<IonIdentity> {

  @NotNull
  private final IonType ionType;
  private IonNetwork network;

  /**
   * Create the identity.
   *
   * @param ionType type of adduct.
   */
  public IonIdentity(@NotNull IonType ionType) {
    super();
    this.ionType = ionType;
  }

  /**
   * Get adduct type
   *
   * @return
   */
  @NotNull
  public IonType getIonType() {
    return ionType;
  }

  @Override
  public String toString() {
    return ionType.toString();
  }

  public boolean equalsIonType(IonType ion) {
    return Objects.equals(ion, ionType);
  }

  /**
   * Network number
   *
   * @return -1 if not part of a network
   */
  public int getNetID() {
    return network == null ? -1 : network.getID();
  }

  public @Nullable IonNetwork getNetwork() {
    return network;
  }

  /**
   * Set by {@link IonNetwork#put(io.github.mzmine.datamodel.features.FeatureListRow, IonIdentity)}
   * / {@link IonNetwork#remove(io.github.mzmine.datamodel.features.FeatureListRow)} when the ion is
   * attached to or detached from a network. Callers outside the network code should not invoke
   * this directly.
   */
  public void setNetwork(@Nullable IonNetwork net) {
    network = net;
  }

  /**
   * Score is the network size
   *
   * @return
   */
  public int getScore() {
    if (network == null) {
      return 0;
    }
    return network.size();
  }

  @Override
  public int compareTo(@NotNull IonIdentity ion) {
    return ionType.compareTo(ion.ionType);
  }

  /**
   * @deprecated Use {@link #getScore()} instead.
   */
  @Deprecated
  public int getLikelyhood() {
    return getScore();
  }
}
