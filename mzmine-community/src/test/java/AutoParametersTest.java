/*
 * Copyright (c) 2004-2022 The MZmine Development Team
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

import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.modules.io.import_rawdata_all.spectral_processor.ScanImportProcessorConfig;
import io.github.mzmine.modules.io.import_rawdata_bruker_tdf.TDFImportModule;
import io.github.mzmine.modules.io.import_rawdata_bruker_tdf.TDFImportParameters;
import io.github.mzmine.modules.io.import_rawdata_bruker_tdf.TDFImportTask;
import io.github.mzmine.modules.io.import_rawdata_mzml.MSDKmzMLImportModule;
import io.github.mzmine.modules.io.import_rawdata_mzml.MSDKmzMLImportParameters;
import io.github.mzmine.modules.io.import_rawdata_mzml.MSDKmzMLImportTask;
import io.github.mzmine.modules.tools.auto_parameters.AutoParametersTask;
import io.github.mzmine.project.impl.IMSRawDataFileImpl;
import io.github.mzmine.project.impl.MZmineProjectImpl;
import io.github.mzmine.util.MemoryMapStorage;
import java.io.File;
import java.time.Instant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class AutoParametersTest {

  private static RawDataFile mzmlFile;
  private static MZmineProject project = new MZmineProjectImpl();
  private static IMSRawDataFile tdfFile;

  @BeforeAll
  static void importFiles() {
    final File fMzml = new File(AutoParametersTest.class.getClassLoader()
        .getResource("rawdatafiles/20210623_12_5B_1uL.mzML").getFile());
    final MSDKmzMLImportTask task = new MSDKmzMLImportTask(project, fMzml, ScanImportProcessorConfig.createDefault(),
        MSDKmzMLImportModule.class, new MSDKmzMLImportParameters(), Instant.now(), null);
    task.run();
    mzmlFile = project.getDataFiles()[0];

    final File tdf = new File(
        AutoParametersTest.class.getClassLoader().getResource("rawdatafiles/piper_05_RA5_1_1646.d")
            .getFile());
    final TDFImportTask tdfImportTask = new TDFImportTask(project, tdf, MemoryMapStorage.forRawDataFile(), TDFImportModule.class,
        new TDFImportParameters(), Instant.now());
    tdfImportTask.run();
  }

  @Test
  void testChromatogramsMzml() {
    var task = new AutoParametersTask(null, Instant.now(), mzmlFile);
    task.run();
  }

  @Test
  void testChromatogramsTdf() {
    var task = new AutoParametersTask(null, Instant.now(), tdfFile);
    task.run();
  }
}
