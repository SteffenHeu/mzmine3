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

package io.github.mzmine.modules.io.import_rawdata_all;

import io.github.mzmine.util.RawDataFileType;
import io.github.mzmine.util.RawDataFileTypeDetector;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * OpenLab CDS result sets (.rslt folders) expand to their .dx injections with MS data.
 */
class OpenLabResultSetExpansionTest {

  @TempDir
  Path tmp;

  @Test
  void expandsDirectInjectionsWithMsData() throws IOException {
    final Path rslt = Files.createDirectory(tmp.resolve("TH_20240213 140807.rslt"));
    final File ms = Files.createFile(rslt.resolve("20240213 140811.dx")).toFile();
    Files.createFile(rslt.resolve("20240213 140811.MSScan.bin"));
    Files.createFile(rslt.resolve("20240213 140811.MSPeak.bin"));
    // injection without MS data (e.g. UV only)
    final File noMs = Files.createFile(rslt.resolve("20240213 144641.dx")).toFile();
    // nested result sets are not expanded
    final Path nested = Files.createDirectory(rslt.resolve("nested.rslt"));
    Files.createFile(nested.resolve("1.dx"));
    Files.createFile(nested.resolve("1.MSScan.bin"));

    Assertions.assertEquals(List.of(ms),
        AllSpectralDataImportModule.expandOpenLabResultSet(rslt.toFile()).toList());
    Assertions.assertEquals(RawDataFileType.AGILENT_RSLT_DX,
        RawDataFileTypeDetector.detectDataFileType(ms));
    Assertions.assertFalse(RawDataFileTypeDetector.isAgilentOpenLabInjection(noMs));
    Assertions.assertEquals(
        List.of(rslt.resolve("20240213 140811.MSScan.bin").toFile().getAbsoluteFile(),
            rslt.resolve("20240213 140811.MSPeak.bin").toFile().getAbsoluteFile()),
        RawDataFileTypeDetector.getAgilentOpenLabBinaries(ms));
    // other files pass unchanged
    Assertions.assertEquals(List.of(ms),
        AllSpectralDataImportModule.expandOpenLabResultSet(ms).toList());
  }

  @Test
  void keepsResultSetWithoutInjections() throws IOException {
    final File rslt = Files.createDirectory(tmp.resolve("empty.rslt")).toFile();
    Assertions.assertEquals(List.of(rslt),
        AllSpectralDataImportModule.expandOpenLabResultSet(rslt).toList());
  }
}
