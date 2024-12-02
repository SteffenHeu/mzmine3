package io.github.mzmine.modules.dataprocessing.featdet_xic;

import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.modules.MZmineModuleCategory;
import io.github.mzmine.modules.MZmineProcessingModule;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.util.ExitCode;
import io.github.mzmine.util.MemoryMapStorage;
import java.time.Instant;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XICBuilderModule implements MZmineProcessingModule {

  @Override
  public @NotNull String getName() {
    return "XIC Builder";
  }

  @Override
  public @Nullable Class<? extends ParameterSet> getParameterSetClass() {
    return XICBuilderParameters.class;
  }

  @Override
  public @NotNull String getDescription() {
    return "null";
  }

  @Override
  public @NotNull ExitCode runModule(@NotNull MZmineProject project,
      @NotNull ParameterSet parameters, @NotNull Collection<Task> tasks,
      @NotNull Instant moduleCallDate) {
    final MemoryMapStorage storage = MemoryMapStorage.forFeatureList();

    final RawDataFile[] files = parameters.getValue(XICBuilderParameters.files)
        .getMatchingRawDataFiles();
    for (RawDataFile file : files) {
      tasks.add(new XICBuilderTask(storage, moduleCallDate, file, parameters, project));
    }
    return ExitCode.OK;
  }

  @Override
  public @NotNull MZmineModuleCategory getModuleCategory() {
    return MZmineModuleCategory.EIC_DETECTION;
  }
}
