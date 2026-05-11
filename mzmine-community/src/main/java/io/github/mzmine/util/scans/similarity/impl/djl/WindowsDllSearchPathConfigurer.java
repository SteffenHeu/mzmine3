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

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import java.io.IOException;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Configures the Windows DLL lookup path for native libraries that are unpacked into the DJL
 * cache.
 */
final class WindowsDllSearchPathConfigurer {

  private WindowsDllSearchPathConfigurer() {
  }

  static void configure(@NotNull final Path runtimeDirectory) throws IOException {
    final String normalizedDirectory = runtimeDirectory.toAbsolutePath().normalize().toString();
    final StringBuilder failures = new StringBuilder();

    final boolean pathConfigured = updateProcessPath(normalizedDirectory, failures);
    final boolean dllDirectoryConfigured = setDllDirectory(normalizedDirectory, failures);
    if (!pathConfigured && !dllDirectoryConfigured) {
      throw new IOException(
          "Failed to add " + normalizedDirectory + " to the Windows DLL search path." + failures);
    }
  }

  private static boolean updateProcessPath(@NotNull final String runtimeDirectory,
      @NotNull final StringBuilder failures) {
    final String currentPath = System.getenv("PATH");
    if (containsPathSegment(currentPath, runtimeDirectory)) {
      return true;
    }

    final String updatedPath = currentPath == null || currentPath.isBlank() ? runtimeDirectory
        : runtimeDirectory + ';' + currentPath;
    if (Kernel32.INSTANCE.SetEnvironmentVariable("PATH", updatedPath)) {
      return true;
    }

    appendFailure(failures, "SetEnvironmentVariable(PATH)", Native.getLastError());
    return false;
  }

  private static boolean setDllDirectory(@NotNull final String runtimeDirectory,
      @NotNull final StringBuilder failures) {
    if (Kernel32DllDirectory.INSTANCE.SetDllDirectoryW(runtimeDirectory)) {
      return true;
    }

    appendFailure(failures, "SetDllDirectoryW", Native.getLastError());
    return false;
  }

  private static boolean containsPathSegment(@Nullable final String currentPath,
      @NotNull final String runtimeDirectory) {
    if (currentPath == null || currentPath.isBlank()) {
      return false;
    }

    for (final String entry : currentPath.split(";")) {
      if (normalizePath(entry).equalsIgnoreCase(runtimeDirectory)) {
        return true;
      }
    }
    return false;
  }

  private static @NotNull String normalizePath(@Nullable final String path) {
    if (path == null) {
      return "";
    }

    final String trimmed = path.trim();
    if (trimmed.isEmpty()) {
      return "";
    }

    try {
      return Path.of(trimmed).toAbsolutePath().normalize().toString();
    } catch (RuntimeException e) {
      return trimmed;
    }
  }

  private static void appendFailure(@NotNull final StringBuilder failures,
      @NotNull final String operation, final int errorCode) {
    failures.append(' ').append(operation).append(" failed");
    if (errorCode != 0) {
      failures.append(" (").append(formatErrorCode(errorCode)).append(')');
    }
    failures.append('.');
  }

  private static @NotNull String formatErrorCode(final int errorCode) {
    try {
      return errorCode + ": " + Kernel32Util.formatMessageFromLastErrorCode(errorCode).trim();
    } catch (RuntimeException e) {
      return Integer.toString(errorCode);
    }
  }

  private interface Kernel32DllDirectory extends StdCallLibrary {

    Kernel32DllDirectory INSTANCE = Native.load("kernel32", Kernel32DllDirectory.class,
        W32APIOptions.UNICODE_OPTIONS);

    boolean SetDllDirectoryW(@NotNull String lpPathName);
  }
}
