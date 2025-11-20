package io.github.mzmine.modules.io.import_rawdata_wiff2;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.impl.DDAMsMsInfoImpl;
import io.github.mzmine.datamodel.msms.ActivationMethod;
import io.github.mzmine.datamodel.msms.DIAMsMsInfoImpl;
import io.github.mzmine.datamodel.msms.MsMsInfo;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.DataProviderGrpc;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.DataProviderGrpc.DataProviderBlockingStub;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.Experiment;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.GetExperimentsRequest;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.GetSpectraRequest;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.ListSamplesRequest;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.Precursor;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.Sample;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.ScanWindow;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.Spectrum;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.TimeRange;
import io.github.mzmine.project.impl.RawDataFileImpl;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.RangeUtils;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.apache.commons.collections4.IteratorUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Wiff2DataAccess implements AutoCloseable {

  private static final Logger logger = Logger.getLogger(Wiff2DataAccess.class.getName());

  private final ManagedChannel channel;
  private final DataProviderBlockingStub dataProvider;
  @NotNull
  private final File file;
  @Nullable
  private final MemoryMapStorage storage;

  public Wiff2DataAccess(@NotNull final File file, @Nullable final MemoryMapStorage storage)
      throws IOException {
    this.file = file;
    this.storage = storage;

    ClearcoreServer server = ClearcoreServer.getOrStart();

    ManagedChannel tempChannel = null;
    int tryCount = 0;
    while (tempChannel == null) {
      try {
        TimeUnit.MILLISECONDS.sleep(1000);
        tempChannel = ManagedChannelBuilder.forAddress(server.address(), server.port())
            .usePlaintext().keepAliveTimeout(90, TimeUnit.SECONDS)
            .maxInboundMessageSize(1024 * 1024 * 5).maxRetryAttempts(3).build();
        break;
      } catch (StatusRuntimeException | InterruptedException e) {
        logger.info("Could not connect to wiff2 server. Try %d/10".formatted(tryCount));
      }
      if (tryCount > 10) {
        throw new RuntimeException("Could not connect to wiff2 server after 10 tries.");
      }
      tryCount++;
    }
    channel = tempChannel;
    dataProvider = DataProviderGrpc.newBlockingStub(channel);
  }

  static void main() throws Exception {
    try (Wiff2DataAccess access = new Wiff2DataAccess(new File(
        // zt scan
//        "D:\\OneDrive - mzio GmbH\\mzio\\Example data\\SCIEX\\ZenoTof 8600\\Adriano_confidential\\raw\\038_ZTScan_Zenoon_400msec_150-300mz_30msecMS1_498Da-S_5Da_AT10ms_CE30_2.wiff2"),
        // DDA
//        "D:\\OneDrive - mzio GmbH\\mzio\\Example data\\SCIEX\\ZenoTOF\\RawData\\3_Feces_DDA\\Pos\\20230406_blank_POS_1.wiff2"),
        // swath
        "D:\\OneDrive - mzio GmbH\\mzio\\Example data\\SCIEX\\ZenoTOF\\RawData\\4_Feces_SWATH-DIA\\Pos\\20230406_feces_SWATH_1-2_POS.wiff2"),
        MemoryMapStorage.forRawDataFile())) {

      List<Sample> samples = access.getSamples();

      for (Sample sample : samples) {
//        logger.info(sample.getId() + ":");
        final RawDataFileImpl rawDataFile = new RawDataFileImpl(sample.getSampleName(),
            access.file.getAbsolutePath(), access.storage);
        List<Experiment> experiments = access.getExperiments(sample);

        Iterator<Spectrum> spectra = access.getSpectra(sample, experiments.get(2));

        access.spectrumToMzmineScan(rawDataFile, sample, experiments.get(1), spectra.next());
        logger.info(spectra.toString());
      }
    }
  }

  private @NotNull List<Sample> getSamples() {

    final ListSamplesRequest samplesRequest = ListSamplesRequest.newBuilder()
        .setAbsolutePathToWiffFile(file.getAbsolutePath()).setSkipCorrupted(true).build();
    final Iterator<Sample> samplesDescriptions = dataProvider.getSamplesDescriptions(
        samplesRequest);

    List<Sample> samples = IteratorUtils.toList(samplesDescriptions);
    return samples;
  }

  private List<Experiment> getExperiments(Sample sample) {
    final GetExperimentsRequest r = GetExperimentsRequest.newBuilder().setSampleId(sample.getId())
        .build();
    final List<Experiment> experiments = IteratorUtils.toList(dataProvider.getExperiments(r));
//    logger.info(experiments.toString());
    return experiments;
  }

  private Iterator<Spectrum> getSpectra(@NotNull final Sample sample,
      @NotNull final Experiment experiment) {
    GetSpectraRequest r = GetSpectraRequest.newBuilder().setSampleId(sample.getId())
        .setExperimentId(experiment.getId())
        .setRange(TimeRange.newBuilder().setStart(0d).setEnd(Double.MAX_VALUE))
        .setConvertToCentroid(true).build();
    return dataProvider.getSpectra(r);
  }

  private Scan spectrumToMzmineScan(@NotNull final RawDataFile file, @NotNull Sample sample,
      @NotNull Experiment experiment, @NotNull final Spectrum spectrum) {

    final int scanId = Integer.parseInt(spectrum.getId());
    final int msLevel = experiment.getMsLevel();
    final float rt = (float) spectrum.getScanStartTime();
    final @Nullable Precursor precursor = spectrum.getPrecursor();

    final MsMsInfo msmsInfo = getMsMsInfo(precursor, experiment);

//    new SimpleScan(file, scanId, msLevel, rt, )
    return null;
  }

  @Nullable
  private MsMsInfo getMsMsInfo(@Nullable Precursor precursor, @NotNull Experiment experiment) {
    if (experiment.getMsLevel() < 2 || precursor == null) {
      return null;
    }

    final var isolationWindow = precursor.getIsolationWindow();
    final var ce = precursor.getCollisionEnergy();

    final ActivationMethod activationMethod = ActivationMethod.fromCvAccession(
        precursor.getDissociationMethod().getAccession());
    final float averageCe =
        (float) (ce.getCollisionEnergyRampStart() + ce.getCollisionEnergyRampEnd()) / 2;
    final double isolationTarget = isolationWindow.getIsolationWindowTarget();

    if (Double.compare(isolationTarget, 0) == 0) {
      // ZT scan: no isolation window target set
      // Need to get range from experiment
      final ScanWindow isolationRange = experiment.getMassRanges(0).getIsolationWindow();
      final Range<Double> isolation = Range.closed(isolationRange.getStart(),
          isolationRange.getEnd());
      return new DIAMsMsInfoImpl(
          (float) (ce.getCollisionEnergyRampStart() + ce.getCollisionEnergyRampEnd()) / 2, null,
          experiment.getMsLevel(), activationMethod, isolation);
    }

    if (Double.compare(isolationTarget, 0) != 0
        && Double.compare(isolationWindow.getLowerOffset(), 0) == 0) {
      // DDA: isolation target set, offsets not set.
      // need to get offset from experiment and re-center around m/z. Is this actually correct?
      final ScanWindow isolationRange = experiment.getMassRanges(0).getIsolationWindow();
      final Range<Double> isolation = RangeUtils.rangeAround(isolationTarget,
          isolationRange.getEnd() - isolationRange.getStart());

      return new DDAMsMsInfoImpl(isolationTarget,
          precursor.getPrecursorChargeState() == 0 ? null : precursor.getPrecursorChargeState(),
          averageCe, null, null, experiment.getMsLevel(), activationMethod, isolation);
    }

    if (Double.compare(isolationTarget, 0) != 0
        && Double.compare(isolationWindow.getLowerOffset(), 0) != 0) {
      // ZENO SWATH: isolation offset and isolation target set.
      return new DIAMsMsInfoImpl(averageCe, null, experiment.getMsLevel(), activationMethod,
          Range.closed(isolationWindow.getLowerOffset(), isolationWindow.getUpperOffset()));
    }

    logger.info("Unkown MSMS type in sciex data.");
    return null;
  }

  @Override
  public void close() throws Exception {
    channel.shutdown();
    ClearcoreServer.terminateSeverIfRunning();
  }
}
