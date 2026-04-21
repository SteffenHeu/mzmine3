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

package io.github.mzmine.modules.io.import_rawdata_wiff2;

import com.sun.jna.Platform;
import io.github.mzmine.util.concurrent.CloseableReentrantReadWriteLock;
import io.github.mzmine.util.concurrent.CloseableResourceLock;
import io.github.mzmine.util.files.FileAndPathUtil;
import io.github.mzmine.util.io.JsonUtils;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import java.io.File;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout.OfByte;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClearcoreServer {

  private static final Logger logger = Logger.getLogger(ClearcoreServer.class.getName());
  private static final CloseableReentrantReadWriteLock startLock = new CloseableReentrantReadWriteLock();

  /**
   * current instance. may change if the server crashes or so.
   */
  private static ClearcoreServer server;
  private final ProcessHandle processHandle;

  private final int port;
  private final String address;

  private ClearcoreServer() throws IOException {
    final File dataAccessExe = FileAndPathUtil.resolveInExternalToolsDir(getDataAccessPath());

    // Write appsettings.json (non-secret) before the native call so the server
    // finds the license path on startup.

    // Read port/address from the file we just wrote.
    final File appsettings = FileAndPathUtil.resolveInExternalToolsDir(getAppSettingsPath());
    Map<String, String> o = JsonUtils.readValueOrThrow(appsettings);
    port = Integer.parseInt(o.get("Port"));
    address = o.get("Host");

    if (address == null) {
      throw new RuntimeException("Cannot determine address of SCIEX clearcore service.");
    }

//    ProcessBuilder b = new ProcessBuilder(dataAccessExe.getAbsolutePath(), "--console").inheritIO();
//    b.directory(dataAccessExe.getParentFile());
//    processHandle = b.start().toHandle();

    Arena arena = Arena.ofAuto();
    ByteArrayList bytes = new ByteArrayList(
        dataAccessExe.getAbsolutePath().getBytes(StandardCharsets.UTF_8));
    bytes.add((byte) 0);
    MemorySegment memorySegment = arena.allocateFrom(OfByte.JAVA_BYTE, bytes.toByteArray());

    // Decrypt + write the license file, spawn the server, delete the license.
    long handle = Wiff2LauncherLib_h.wiff2_launch_server(memorySegment);

    processHandle = ProcessHandle.of(handle)
        .orElseThrow(() -> new RuntimeException("Cannot launch wiff2 data access server."));
  }

  @NotNull
  public static ClearcoreServer getOrStart() {
    startServer();
    return server;
  }

  @Nullable
  public static ClearcoreServer getInstance() {
    return server;
  }

  public static void terminateSeverIfRunning() {
    if (getInstance() != null) {
      getInstance().terminateClearcoreInstance();
    }
  }

  private static @NotNull String getDataAccessPath() {
    if (Platform.isWindows()) {
      return getPathForOs("Clearcore2.SampleData.DataAccessApi.exe");
    }
    if (Platform.isLinux()) {
      return getPathForOs("Clearcore2.SampleData.DataAccessApi");
    }
    throw new RuntimeException(
        "Native SCIEX support is not available for your operating system. Please convert to mzML or switch to Windows/Linux.");
  }

  private static @NotNull String getAppSettingsPath() {
    return getPathForOs("appsettings.json");
  }

  private static @NotNull String getPathForOs(@NotNull final String filename) {
    if (Platform.isWindows()) {
      return "sciex_wiff2/Server-win10-x64/%s".formatted(filename);
    }
    if (Platform.isLinux()) {
      return "sciex_wiff2/Server-linux-x64/%s".formatted(filename);
    }
    throw new RuntimeException(
        "Native SCIEX support is not available for your operating system. Please convert to mzML or switch to Windows/Linux.");
  }

  private static void startServer() {
    try (CloseableResourceLock _ = startLock.lockRead()) {
      if (server != null && server.isAlive()) {
        return;
      }
    }

    try (var _ = startLock.lockWrite()) {
      if (server != null) {
        server.terminateClearcoreInstance();
      }
      server = new ClearcoreServer();
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Error while starting SCIEX Clearcore server.", e);
      throw new RuntimeException("Cannot start SCIEX Clearcore server.");
    }

    if (server == null || !server.isAlive()) {
      throw new RuntimeException("Unable to start SCIEX clearcore server.");
    }
  }

  public static boolean isInstanceAlive() {
    return server != null && server.isAlive();
  }

  private void terminateClearcoreInstance() {
    if (processHandle != null) {
//      logger.info("Terminating SCIEX clearcore service.");
      processHandle.destroy();
    }
  }

  private boolean isAlive() {
    return processHandle != null && processHandle.isAlive();
  }

  public int port() {
    return port;
  }

  public String address() {
    return address;
  }
}
