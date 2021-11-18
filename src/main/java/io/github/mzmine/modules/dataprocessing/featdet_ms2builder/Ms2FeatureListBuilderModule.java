package io.github.mzmine.modules.dataprocessing.featdet_ms2builder;

import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.modules.MZmineModuleCategory;
import io.github.mzmine.modules.MZmineRunnableModule;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.util.ExitCode;
import io.github.mzmine.util.MemoryMapStorage;
import java.time.Instant;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Ms2FeatureListBuilderModule implements MZmineRunnableModule {

  @Override
  public @NotNull String getName() {
    return "PRM-PASEF feature list builder";
  }

  @Override
  public @Nullable Class<? extends ParameterSet> getParameterSetClass() {
    return Ms2FeatureListBuilderParameters.class;
  }

  @Override
  public @NotNull String getDescription() {
    return "Builds feature lists for PRM-PASEF experiments based on the MS2 events.";
  }

  @Override
  public @NotNull ExitCode runModule(@NotNull MZmineProject project,
      @NotNull ParameterSet parameters, @NotNull Collection<Task> tasks,
      @NotNull Instant moduleCallDate) {

    final MemoryMapStorage memoryMapStorage = MemoryMapStorage.forFeatureList();
    for (RawDataFile file : parameters.getParameter(Ms2FeatureListBuilderParameters.files)
        .getValue().getMatchingRawDataFiles()) {
      tasks.add(new Ms2FeatureListBuilderTask(project, memoryMapStorage, moduleCallDate, file, parameters));
    }

    return ExitCode.OK;
  }

  @Override
  public @NotNull MZmineModuleCategory getModuleCategory() {
    return MZmineModuleCategory.EIC_DETECTION;
  }
}
