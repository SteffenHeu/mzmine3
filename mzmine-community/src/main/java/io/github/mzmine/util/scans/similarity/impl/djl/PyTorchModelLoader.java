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

package io.github.mzmine.util.scans.similarity.impl.djl;

import ai.djl.MalformedModelException;
import ai.djl.engine.EngineException;
import ai.djl.repository.zoo.ModelNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Loads DJL PyTorch models and retries once on CPU if DJL auto-selects an unusable CUDA runtime.
 */
public final class PyTorchModelLoader {

  private static final Object CPU_FALLBACK_LOCK = new Object();
  private static final String DJL_CACHE_DIRECTORY = ".djl.ai";
  private static final String PYTORCH_CACHE_DIRECTORY = "pytorch";
  private static final String PYTORCH_FLAVOR_PROPERTY = "PYTORCH_FLAVOR";
  private static final String CPU_FLAVOR = "cpu";
  private static final String TORCH_CPU_DLL = "torch_cpu.dll";
  private static final String TORCH_CUDA_DLL = "torch_cuda.dll";
  private static final String USER_HOME_PROPERTY = "user.home";
  private static final Set<String> configuredWindowsRuntimeDirectories = ConcurrentHashMap.newKeySet();

  private PyTorchModelLoader() {
  }

  public static <T> @NotNull T loadWithCpuFallback(@NotNull final String modelLabel,
      @NotNull final Logger logger, @NotNull final ModelLoadAction<T> loadAction)
      throws ModelNotFoundException, MalformedModelException, IOException {
    configureDefaultFlavor(modelLabel, logger);
    configureWindowsGpuRuntimeLibraryPath(modelLabel, logger);
    try {
      return loadAction.load();
    } catch (EngineException | UnsatisfiedLinkError e) {
      return handleLoadFailure(modelLabel, logger, loadAction, e);
    }
  }

  private static void configureDefaultFlavor(@NotNull final String modelLabel,
      @NotNull final Logger logger) {
    if (!isWindows()) {
      return;
    }

    final String configuredFlavor = getConfiguredPyTorchFlavor();
    if (configuredFlavor != null && !configuredFlavor.isBlank()) {
      return;
    }

    synchronized (CPU_FALLBACK_LOCK) {
      final String effectiveFlavor = getConfiguredPyTorchFlavor();
      if (effectiveFlavor != null && !effectiveFlavor.isBlank()) {
        return;
      }

      logger.info("No explicit PyTorch runtime configured for " + modelLabel
          + " on Windows. Defaulting to CPU inference (PYTORCH_FLAVOR=cpu)."
          + " Set PYTORCH_FLAVOR=cu128 before starting mzmine to opt into GPU inference.");
      System.setProperty(PYTORCH_FLAVOR_PROPERTY, CPU_FLAVOR);
    }
  }

  private static <T> @NotNull T handleLoadFailure(@NotNull final String modelLabel,
      @NotNull final Logger logger, @NotNull final ModelLoadAction<T> loadAction,
      @NotNull final Throwable failure)
      throws ModelNotFoundException, MalformedModelException, IOException {
    if (!isPyTorchNativeLoadFailure(failure)) {
      throw createRuntimeLoadException(modelLabel, failure, false, getConfiguredPyTorchFlavor());
    }

    final String configuredFlavor = getConfiguredPyTorchFlavor();
    if (configuredFlavor != null && !configuredFlavor.isBlank() && !CPU_FLAVOR.equalsIgnoreCase(
        configuredFlavor) && configureWindowsGpuRuntimeLibraryPath(modelLabel, logger)) {
      return retryWithConfiguredFlavor(modelLabel, loadAction, configuredFlavor);
    }

    if (configuredFlavor != null && !configuredFlavor.isBlank()) {
      throw createRuntimeLoadException(modelLabel, failure, false, configuredFlavor);
    }

    synchronized (CPU_FALLBACK_LOCK) {
      final String effectiveFlavor = getConfiguredPyTorchFlavor();
      if (CPU_FLAVOR.equalsIgnoreCase(effectiveFlavor)) {
        return retryOnCpu(modelLabel, loadAction);
      }

      logger.log(Level.WARNING, "Failed to load the PyTorch native runtime for " + modelLabel
          + ". Retrying with CPU inference (PYTORCH_FLAVOR=cpu).", failure);
      System.setProperty(PYTORCH_FLAVOR_PROPERTY, CPU_FLAVOR);
      return retryOnCpu(modelLabel, loadAction);
    }
  }

  private static <T> @NotNull T retryOnCpu(@NotNull final String modelLabel,
      @NotNull final ModelLoadAction<T> loadAction)
      throws ModelNotFoundException, MalformedModelException, IOException {
    try {
      return loadAction.load();
    } catch (EngineException | UnsatisfiedLinkError retryFailure) {
      throw createRuntimeLoadException(modelLabel, retryFailure, true,
          getConfiguredPyTorchFlavor());
    }
  }

  private static <T> @NotNull T retryWithConfiguredFlavor(@NotNull final String modelLabel,
      @NotNull final ModelLoadAction<T> loadAction, @NotNull final String configuredFlavor)
      throws ModelNotFoundException, MalformedModelException, IOException {
    try {
      return loadAction.load();
    } catch (EngineException | UnsatisfiedLinkError retryFailure) {
      throw createRuntimeLoadException(modelLabel, retryFailure, false, configuredFlavor);
    }
  }

  private static boolean isPyTorchNativeLoadFailure(@NotNull final Throwable failure) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current instanceof UnsatisfiedLinkError) {
        return true;
      }

      final String message = current.getMessage();
      if (message != null && (message.contains("PyTorch native library") || message.contains(
          "Can't find dependent libraries"))) {
        return true;
      }
    }
    return false;
  }

  private static @Nullable String getConfiguredPyTorchFlavor() {
    final String systemProperty = System.getProperty(PYTORCH_FLAVOR_PROPERTY);
    if (systemProperty != null && !systemProperty.isBlank()) {
      return systemProperty;
    }

    final String environmentVariable = System.getenv(PYTORCH_FLAVOR_PROPERTY);
    if (environmentVariable != null && !environmentVariable.isBlank()) {
      return environmentVariable;
    }

    return null;
  }

  static @Nullable Path findWindowsRuntimeDirectory(@NotNull final String flavor) {
    final String userHome = System.getProperty(USER_HOME_PROPERTY);
    if (userHome == null || userHome.isBlank()) {
      return null;
    }

    final Path pytorchCacheDirectory = Path.of(userHome, DJL_CACHE_DIRECTORY,
        PYTORCH_CACHE_DIRECTORY);
    if (!Files.isDirectory(pytorchCacheDirectory)) {
      return null;
    }

    final String expectedSuffix = "-" + flavor.toLowerCase(Locale.ROOT) + "-win-x86_64";
    final String requiredLibrary =
        CPU_FLAVOR.equalsIgnoreCase(flavor) ? TORCH_CPU_DLL : TORCH_CUDA_DLL;

    try (var candidates = Files.list(pytorchCacheDirectory)) {
      return candidates.filter(Files::isDirectory).filter(path -> {
        final String directoryName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return directoryName.endsWith(expectedSuffix.toLowerCase(Locale.ROOT))
            && Files.isRegularFile(path.resolve(requiredLibrary));
      }).max(PyTorchModelLoader::compareRuntimeDirectories).orElse(null);
    } catch (IOException e) {
      return null;
    }
  }

  private static boolean configureWindowsGpuRuntimeLibraryPath(@NotNull final String modelLabel,
      @NotNull final Logger logger) {
    if (!isWindows()) {
      return false;
    }

    final String configuredFlavor = getConfiguredPyTorchFlavor();
    if (configuredFlavor == null || configuredFlavor.isBlank() || CPU_FLAVOR.equalsIgnoreCase(
        configuredFlavor)) {
      return false;
    }

    final Path runtimeDirectory = findWindowsRuntimeDirectory(configuredFlavor);
    if (runtimeDirectory == null) {
      return false;
    }

    final String normalizedDirectory = runtimeDirectory.toAbsolutePath().normalize().toString();
    if (!configuredWindowsRuntimeDirectories.add(normalizedDirectory)) {
      return false;
    }

    try {
      WindowsDllSearchPathConfigurer.configure(runtimeDirectory);
      logger.info(
          "Configured Windows DLL search path for " + modelLabel + " from " + normalizedDirectory
              + '.');
      return true;
    } catch (IOException e) {
      configuredWindowsRuntimeDirectories.remove(normalizedDirectory);
      logger.log(Level.WARNING,
          "Could not configure the Windows DLL search path for " + modelLabel + " from "
              + normalizedDirectory + '.', e);
      return false;
    }
  }

  private static int compareRuntimeDirectories(@NotNull final Path left,
      @NotNull final Path right) {
    return compareVersionStrings(extractRuntimeVersion(left), extractRuntimeVersion(right));
  }

  private static @NotNull String extractRuntimeVersion(@NotNull final Path runtimeDirectory) {
    final String directoryName = runtimeDirectory.getFileName().toString();
    final int separatorIndex = directoryName.indexOf('-');
    return separatorIndex < 0 ? directoryName : directoryName.substring(0, separatorIndex);
  }

  private static int compareVersionStrings(@NotNull final String left,
      @NotNull final String right) {
    final String[] leftTokens = left.split("\\.");
    final String[] rightTokens = right.split("\\.");
    final int maxLength = Math.max(leftTokens.length, rightTokens.length);

    for (int i = 0; i < maxLength; i++) {
      final int leftValue = i < leftTokens.length ? parseVersionComponent(leftTokens[i]) : 0;
      final int rightValue = i < rightTokens.length ? parseVersionComponent(rightTokens[i]) : 0;
      if (leftValue != rightValue) {
        return Integer.compare(leftValue, rightValue);
      }
    }

    return left.compareTo(right);
  }

  private static int parseVersionComponent(@NotNull final String token) {
    try {
      return Integer.parseInt(token);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static @NotNull IOException createRuntimeLoadException(@NotNull final String modelLabel,
      @NotNull final Throwable failure, final boolean attemptedCpuFallback,
      @Nullable final String configuredFlavor) {
    final StringBuilder message = new StringBuilder(
        "PyTorch runtime could not be initialized for ").append(modelLabel).append('.');

    if (attemptedCpuFallback) {
      message.append(" Retrying with CPU inference (PYTORCH_FLAVOR=cpu) also failed.");
    } else if (configuredFlavor != null && !configuredFlavor.isBlank()) {
      message.append(" DJL is configured with PYTORCH_FLAVOR=").append(configuredFlavor)
          .append('.');
    } else {
      message.append(" Set PYTORCH_FLAVOR=cpu before starting mzmine to force CPU inference.");
    }

    if (isWindows()) {
      message.append(
          " On Windows this is often caused by a missing Microsoft Visual C++ Redistributable or an unusable CUDA runtime.");
    }

    final String rootMessage = findRootCauseMessage(failure);
    if (rootMessage != null && !rootMessage.isBlank()) {
      message.append(" Root cause: ").append(rootMessage);
    }

    return new IOException(message.toString(), failure);
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").startsWith("Windows");
  }

  private static @Nullable String findRootCauseMessage(@NotNull final Throwable failure) {
    Throwable current = failure;
    String deepestMessage = failure.getMessage();
    while (current.getCause() != null) {
      current = current.getCause();
      if (current.getMessage() != null && !current.getMessage().isBlank()) {
        deepestMessage = current.getMessage();
      }
    }
    return deepestMessage;
  }

  @FunctionalInterface
  public interface ModelLoadAction<T> {

    @NotNull T load() throws ModelNotFoundException, MalformedModelException, IOException;
  }
}
