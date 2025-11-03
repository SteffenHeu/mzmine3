package io.github.mzmine.modules.io.import_rawdata_wiff2;

import io.github.mzmine.modules.io.import_rawdata_wiff2.api.Clearcore2SampleDataGrpcContracts;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.DataProviderGrpc;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.DataProviderGrpc.DataProviderBlockingStub;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.DataProviderGrpc.DataProviderBlockingV2Stub;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.ListSamplesRequest;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.Sample;
import io.github.mzmine.util.StringUtils;
import io.github.mzmine.util.files.FileAndPathUtil;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.BlockingClientCall;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.apache.commons.collections4.IteratorUtils;
import org.jetbrains.annotations.NotNull;

public class Wiff2DataAccess implements AutoCloseable {

  private static final Logger logger = Logger.getLogger(Wiff2DataAccess.class.getName());

  private final ManagedChannel channel;
  private final DataProviderBlockingStub dataProvider;
//  private final Process server;

  public Wiff2DataAccess(@NotNull final File file) throws IOException {

//    final File dataAccessExe = FileAndPathUtil.resolveInExternalToolsDir(
//        "sciex_wiff2/Server-win10-x64/Clearcore2.SampleData.DataAccessApi.exe");
//    ProcessBuilder b = new ProcessBuilder(dataAccessExe.getAbsolutePath(),
//        "--console");
//    server = b.start();

    ManagedChannel tempChannel = null;
    int tryCount = 0;
    while (tempChannel == null) {
      try {
        TimeUnit.MILLISECONDS.sleep(1000);
        tempChannel = ManagedChannelBuilder.forAddress("127.0.0.1", 50051).usePlaintext()
            .keepAliveTimeout(90, TimeUnit.SECONDS).maxInboundMessageSize(1024 * 1024 * 5)
            .maxRetryAttempts(3).build();
        break;
      } catch (StatusRuntimeException | InterruptedException e) {
        logger.info("Could not connect to wiff2 server. Try %d/10".formatted(tryCount));
      }
      if(tryCount > 10) {
        throw new RuntimeException("Could not connect to wiff2 server after 10 tries.");
      }
      tryCount++;
    }
    channel = tempChannel;
    dataProvider = DataProviderGrpc.newBlockingStub(channel);

    ListSamplesRequest samplesRequest = ListSamplesRequest.newBuilder()
        .setAbsolutePathToWiffFile(file.getAbsolutePath()).setSkipCorrupted(true).build();

    final Iterator<Sample> samplesDescriptions = dataProvider.getSamplesDescriptions(
        samplesRequest);
    samplesDescriptions.forEachRemaining(c -> logger.info(c.toString()));
  }

  static void main() throws Exception {
    try (Wiff2DataAccess access = new Wiff2DataAccess(new File(
        "D:\\OneDrive - mzio GmbH\\mzio\\Example data\\SCIEX\\ZenoTOF\\RawData\\3_Feces_DDA\\Pos\\20230406_blank_POS_1.wiff2"))) {

    }
  }

  @Override
  public void close() throws Exception {
    channel.shutdown();
//    server.destroyForcibly();
  }

}
