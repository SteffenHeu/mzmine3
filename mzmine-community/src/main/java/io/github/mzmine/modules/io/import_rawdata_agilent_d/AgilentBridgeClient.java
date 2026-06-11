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

package io.github.mzmine.modules.io.import_rawdata_agilent_d;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.mzmine.util.files.FileAndPathUtil;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thin subprocess transport for the C# AgilentBridge. Speaks the bridge wire protocol: each request
 * is one line of JSON written to the bridge's stdin; each response is one line of JSON (the
 * "header") on stdout, optionally followed by little-endian {@code float64} binary blobs.
 * <p>
 * This is the Agilent equivalent of {@code MassLynxLib} (which binds an in-process native library);
 * here the vendor code lives in a separate Windows process and we talk to it over stdio.
 */
final class AgilentBridgeClient implements AutoCloseable {

  private static final Logger logger = Logger.getLogger(AgilentBridgeClient.class.getName());

  /**
   * Path relative to the {@code external_tools} directory.
   */
  private static final String RELATIVE_EXE = "agilent_bridge/AgilentBridge.exe";

  private final Process process;
  private final OutputStream stdin;
  private final BufferedInputStream stdout;
  // the bridge's serializer emits NaN/Infinity as bare tokens (non-standard JSON), e.g. an
  // unavailable frame TIC -> "tic":NaN; accept them rather than failing the whole response.
  private final ObjectMapper mapper = JsonMapper.builder()
      .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS).build();

  AgilentBridgeClient() {
    final File exe = locateExecutable();
    try {
      final ProcessBuilder builder = new ProcessBuilder(exe.getAbsolutePath());
      builder.directory(exe.getParentFile()); // so the vendor DLLs next to the exe are found
      process = builder.start();
    } catch (IOException e) {
      throw new RuntimeException("Could not launch AgilentBridge at " + exe.getAbsolutePath(), e);
    }
    stdin = process.getOutputStream();
    stdout = new BufferedInputStream(process.getInputStream());
    pumpStderr(process.getErrorStream());
  }

  /**
   * Send a request and return the parsed JSON response header. Any binary blobs that follow must be
   * read by the caller via {@link #readBlob(int)} using counts from the header.
   */
  @NotNull JsonNode send(@NotNull Map<String, Object> request) {
    try {
      final byte[] line = (mapper.writeValueAsString(request) + "\n").getBytes(
          StandardCharsets.UTF_8);
      stdin.write(line);
      stdin.flush();
    } catch (IOException e) {
      throw new RuntimeException("Failed writing request to AgilentBridge: " + request, e);
    }

    final String header = readHeaderLine();
    if (header == null) {
      throw new RuntimeException(
          "AgilentBridge closed the stream while awaiting a response to " + request.get("op"));
    }
    try {
      final JsonNode node = mapper.readTree(header);
      final JsonNode error = node.get("error");
      if (error != null && !error.isNull()) {
        throw new RuntimeException("AgilentBridge error: " + error.asText());
      }
      return node;
    } catch (IOException e) {
      throw new RuntimeException("Failed parsing AgilentBridge response: " + header, e);
    }
  }

  /**
   * Read {@code numPoints} little-endian float64 values from the response stream.
   */
  double[] readBlob(int numPoints) {
    if (numPoints <= 0) {
      return new double[0];
    }
    final byte[] bytes = readFully(numPoints * Double.BYTES);
    final double[] out = new double[numPoints];
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asDoubleBuffer().get(out);
    return out;
  }

  private @Nullable String readHeaderLine() {
    final ByteArrayOutputStream buffer = new ByteArrayOutputStream(256);
    try {
      int b;
      while ((b = stdout.read()) != -1) {
        if (b == '\n') {
          return buffer.toString(StandardCharsets.UTF_8);
        }
        if (b != '\r') {
          buffer.write(b);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed reading AgilentBridge response header", e);
    }
    return buffer.size() > 0 ? buffer.toString(StandardCharsets.UTF_8) : null;
  }

  private byte[] readFully(int n) {
    final byte[] buf = new byte[n];
    int read = 0;
    try {
      while (read < n) {
        final int r = stdout.read(buf, read, n - read);
        if (r == -1) {
          throw new RuntimeException(
              "AgilentBridge stream ended after %d/%d blob bytes".formatted(read, n));
        }
        read += r;
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed reading AgilentBridge blob", e);
    }
    return buf;
  }

  private static void pumpStderr(InputStream err) {
    final Thread t = new Thread(() -> {
      try (var reader = new java.io.BufferedReader(
          new java.io.InputStreamReader(err, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          logger.finest("AgilentBridge: " + line);
        }
      } catch (IOException ignored) {
        // stream closed on shutdown
      }
    }, "AgilentBridge-stderr");
    t.setDaemon(true);
    t.start();
  }

  private static @NotNull File locateExecutable() {
    final File exe = FileAndPathUtil.resolveInExternalToolsDir(RELATIVE_EXE);
    if (exe != null && exe.isFile()) {
      return exe;
    }
    throw new RuntimeException("Could not find external_tools/" + RELATIVE_EXE);
  }

  @Override
  public void close() {
    try {
      stdin.close();
    } catch (IOException ignored) {
    }
    process.destroy();
  }
}
