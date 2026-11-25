package io.github.mzmine.datamodel.features.compoundannotations.pfas;

import io.github.mzmine.datamodel.features.FeatureList.FeatureListAppliedMethod;
import io.github.mzmine.modules.MZmineModule;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.ParameterUtils;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A module that creates {@link PfasAnnotation}s. Lets the viewer read the matching settings from the
 * applied methods of a feature list without depending on the module itself.
 */
public interface PfasAnnotationMethod extends MZmineModule {

  /**
   * @param parameters parameters of an applied call of this module
   * @return the spectrum matching settings of that call
   */
  @NotNull PfasMatchSettings matchSettings(@NotNull ParameterSet parameters);

  /**
   * @param appliedMethods applied methods of a feature list
   * @return settings of the latest PFAS annotation call, or null if there is none or the module is
   * not available, e.g. in mzmine community
   */
  static @Nullable PfasMatchSettings latestSettings(
      @NotNull List<@Nullable FeatureListAppliedMethod> appliedMethods) {
    final FeatureListAppliedMethod call = ParameterUtils.getLatestModuleCall(appliedMethods,
        PfasAnnotationMethod.class);
    if (call == null || !(call.getModule() instanceof PfasAnnotationMethod method)) {
      return null;
    }
    return method.matchSettings(call.getParameters());
  }
}
