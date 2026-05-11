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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.djl.engine.EngineException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PyTorchModelLoaderTest {

  private static final Logger logger = Logger.getLogger(PyTorchModelLoaderTest.class.getName());
  private static final String OS_NAME_PROPERTY = "os.name";
  private static final String PYTORCH_FLAVOR_PROPERTY = "PYTORCH_FLAVOR";
  private static final String USER_HOME_PROPERTY = "user.home";

  @Nullable
  private final String originalFlavor = System.getProperty(PYTORCH_FLAVOR_PROPERTY);
  @Nullable
  private final String originalOsName = System.getProperty(OS_NAME_PROPERTY);
  @Nullable
  private final String originalUserHome = System.getProperty(USER_HOME_PROPERTY);

  @AfterEach
  void restoreSystemProperties() {
    if (originalFlavor == null) {
      System.clearProperty(PYTORCH_FLAVOR_PROPERTY);
    } else {
      System.setProperty(PYTORCH_FLAVOR_PROPERTY, originalFlavor);
    }

    if (originalOsName == null) {
      System.clearProperty(OS_NAME_PROPERTY);
    } else {
      System.setProperty(OS_NAME_PROPERTY, originalOsName);
    }

    if (originalUserHome == null) {
      System.clearProperty(USER_HOME_PROPERTY);
    } else {
      System.setProperty(USER_HOME_PROPERTY, originalUserHome);
    }
  }

  @Test
  void defaultsToCpuOnWindowsBeforeFirstLoad() throws Exception {
    System.clearProperty(PYTORCH_FLAVOR_PROPERTY);
    System.setProperty(OS_NAME_PROPERTY, "Windows 11");

    final AtomicInteger attempts = new AtomicInteger();
    final Object expected = new Object();

    final Object loaded = PyTorchModelLoader.loadWithCpuFallback("MS2Deepscore", logger, () -> {
      attempts.incrementAndGet();
      assertEquals("cpu", System.getProperty(PYTORCH_FLAVOR_PROPERTY));
      return expected;
    });

    assertSame(expected, loaded);
    assertEquals(1, attempts.get());
    assertEquals("cpu", System.getProperty(PYTORCH_FLAVOR_PROPERTY));
  }

  @Test
  void retriesWithCpuAfterNativeLoadFailureOnNonWindows() throws Exception {
    System.clearProperty(PYTORCH_FLAVOR_PROPERTY);
    System.setProperty(OS_NAME_PROPERTY, "Linux");

    final AtomicInteger attempts = new AtomicInteger();
    final Object expected = new Object();

    final Object loaded = PyTorchModelLoader.loadWithCpuFallback("MS2Deepscore", logger, () -> {
      if (attempts.getAndIncrement() == 0) {
        throw nativeLoadFailure();
      }
      return expected;
    });

    assertSame(expected, loaded);
    assertEquals(2, attempts.get());
    assertEquals("cpu", System.getProperty(PYTORCH_FLAVOR_PROPERTY));
  }

  @Test
  void findsLatestWindowsRuntimeDirectoryForFlavor(@TempDir final Path tempHome)
      throws IOException {
    System.setProperty(USER_HOME_PROPERTY, tempHome.toString());

    final Path pytorchCacheDirectory = tempHome.resolve(".djl.ai").resolve("pytorch");
    final Path olderRuntime = Files.createDirectories(
        pytorchCacheDirectory.resolve("2.7.0-cu128-win-x86_64"));
    Files.createFile(olderRuntime.resolve("torch_cuda.dll"));

    final Path newerRuntime = Files.createDirectories(
        pytorchCacheDirectory.resolve("2.7.1-cu128-win-x86_64"));
    Files.createFile(newerRuntime.resolve("torch_cuda.dll"));

    final Path runtimeDirectory = PyTorchModelLoader.findWindowsRuntimeDirectory("cu128");
    assertNotNull(runtimeDirectory);
    assertEquals(newerRuntime.toAbsolutePath().normalize(),
        runtimeDirectory.toAbsolutePath().normalize());
  }

  @Test
  void doesNotOverrideExplicitPyTorchFlavor() {
    System.setProperty(PYTORCH_FLAVOR_PROPERTY, "cu128");
    System.setProperty(OS_NAME_PROPERTY, "Windows 11");

    final AtomicInteger attempts = new AtomicInteger();

    final IOException exception = assertThrows(IOException.class,
        () -> PyTorchModelLoader.loadWithCpuFallback("MS2Deepscore", logger, () -> {
          attempts.incrementAndGet();
          throw nativeLoadFailure();
        }));

    assertEquals(1, attempts.get());
    assertEquals("cu128", System.getProperty(PYTORCH_FLAVOR_PROPERTY));
    assertTrue(exception.getMessage().contains("PYTORCH_FLAVOR=cu128"));
  }

  @Test
  void surfacesCpuRetryFailureAsCheckedException() {
    System.clearProperty(PYTORCH_FLAVOR_PROPERTY);
    System.setProperty(OS_NAME_PROPERTY, "Linux");

    final AtomicInteger attempts = new AtomicInteger();

    final IOException exception = assertThrows(IOException.class,
        () -> PyTorchModelLoader.loadWithCpuFallback("MS2Deepscore", logger, () -> {
          attempts.incrementAndGet();
          throw nativeLoadFailure();
        }));

    assertEquals(2, attempts.get());
    assertEquals("cpu", System.getProperty(PYTORCH_FLAVOR_PROPERTY));
    assertTrue(exception.getMessage().contains("Retrying with CPU inference"));
  }

  private static EngineException nativeLoadFailure() {
    return new EngineException("Failed to load PyTorch native library",
        new UnsatisfiedLinkError("cublas64_12.dll: Can't find dependent libraries"));
  }
}
