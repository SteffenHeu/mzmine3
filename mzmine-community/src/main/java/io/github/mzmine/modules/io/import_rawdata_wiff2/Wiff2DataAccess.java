package io.github.mzmine.modules.io.import_rawdata_wiff2;

import io.github.mzmine.modules.io.import_rawdata_wiff2.api.Clearcore2SampleDataGrpcContracts;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.ListSamplesRequest;
import java.io.File;
import org.jetbrains.annotations.NotNull;

public class Wiff2DataAccess {

  public Wiff2DataAccess(@NotNull final File file) {
    ListSamplesRequest samplesRequest = ListSamplesRequest.newBuilder()
        .setAbsolutePathToWiffFile(file.getAbsolutePath()).setSkipCorrupted(true).build();


  }
}
