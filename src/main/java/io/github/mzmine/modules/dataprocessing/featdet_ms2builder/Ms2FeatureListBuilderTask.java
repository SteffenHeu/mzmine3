package io.github.mzmine.modules.dataprocessing.featdet_ms2builder;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.FeatureStatus;
import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.MergedMsMsSpectrum;
import io.github.mzmine.datamodel.MobilityScan;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.data_access.BinningMobilogramDataAccess;
import io.github.mzmine.datamodel.featuredata.IonMobilitySeries;
import io.github.mzmine.datamodel.featuredata.IonMobilogramTimeSeries;
import io.github.mzmine.datamodel.featuredata.impl.IonMobilogramTimeSeriesFactory;
import io.github.mzmine.datamodel.featuredata.impl.SimpleIonMobilitySeries;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.SimpleFeatureListAppliedMethod;
import io.github.mzmine.datamodel.msms.DDAMsMsInfo;
import io.github.mzmine.datamodel.msms.PasefMsMsInfo;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.mobilitytolerance.MobilityTolerance;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.RangeUtils;
import io.github.mzmine.util.scans.SpectraMerging;
import io.github.mzmine.util.scans.SpectraMerging.MergingType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;

public class Ms2FeatureListBuilderTask extends AbstractTask {

  private static Logger logger = Logger.getLogger(Ms2FeatureListBuilderTask.class.getName());

  private final ModularFeatureList flist;
  private final MZTolerance mzTolerance;
  private final IMSRawDataFile file;
  private final boolean buildIntensityFromMS2TIC;
  private final MZmineProject project;
  private final ParameterSet parameters;
  private String featureListName;
  private final ScanSelection scanSelection;
  private final RTTolerance rtTolerance;
  private final MobilityTolerance mobTolerance;

  private double finished = 0d;

  public Ms2FeatureListBuilderTask(@NotNull MZmineProject project, MemoryMapStorage storage,
      Instant date, RawDataFile file, @NotNull ParameterSet parameters) {
    super(storage, date);
    this.project = project;
    this.parameters = parameters;
    buildIntensityFromMS2TIC = true;
    this.file = (IMSRawDataFile) file;
    mzTolerance = parameters.getParameter(Ms2FeatureListBuilderParameters.mzTolerance).getValue();
    ;
    rtTolerance = parameters.getParameter(Ms2FeatureListBuilderParameters.rtTolerance).getValue();
    scanSelection = parameters.getParameter(Ms2FeatureListBuilderParameters.scanSelection)
        .getValue();
    mobTolerance = parameters.getParameter(Ms2FeatureListBuilderParameters.mobTolerance).getValue();
    flist = new ModularFeatureList(file.getName(), getMemoryMapStorage(), file);
  }

  @Override
  public String getTaskDescription() {
    return null;
  }

  @Override
  public double getFinishedPercentage() {
    return finished;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);

    final BinningMobilogramDataAccess binningMobilogramDataAccess = new BinningMobilogramDataAccess(
        file, BinningMobilogramDataAccess.getRecommendedBinWidth(file));

    final List<Ms2ImsGap> gaps = createImsGaps(binningMobilogramDataAccess);

    if (isCanceled()) {
      return;
    }

//    DataTypeUtils.addDefaultIonMobilityTypeColumns(flist);
    final List<ModularFeatureListRow> rows = buildMs2Chromatograms(gaps,
        binningMobilogramDataAccess);
    rows.forEach(flist::addRow);

    if (isCanceled()) {
      return;
    }

    flist.getAppliedMethods().addAll(file.getAppliedMethods());
    flist.getAppliedMethods().add(
        new SimpleFeatureListAppliedMethod(Ms2FeatureListBuilderModule.class, parameters,
            getModuleCallDate()));
    project.addFeatureList(flist);
    finished = 1d;
    setStatus(TaskStatus.FINISHED);
  }

  @NotNull
  private List<Ms2ImsGap> createImsGaps(BinningMobilogramDataAccess binningMobilogramDataAccess) {

    final List<Frame> matchingScans = (List<Frame>) scanSelection.getMatchingScans(
        file.getFrames());
    flist.setSelectedScans(file, matchingScans);

    logger.finest(() -> "Getting PasefMsMsInfos from frames.");
    final List<PasefMsMsInfo> infos = matchingScans.stream()
        .flatMap(f -> f.getImsMsMsInfos().stream()).toList();
    logger.finest(() -> "Recieved " + infos.size() + " matching PasefMsMsInfos.");

    logger.finest(() -> "Building Ms2ImsGaps from " + infos.size() + " PasefMsMsinfos.");
    final List<Ms2ImsGap> gaps = new ArrayList<>();
    for (int i = 0; i < infos.size(); i++) {
      if (isCanceled()) {
        return null;
      }
      PasefMsMsInfo pasefMsMsInfo = infos.get(i);
      Optional<Ms2ImsGap> first = gaps.stream()
          .filter(gap -> gap.contains(pasefMsMsInfo, rtTolerance, mzTolerance, mobTolerance))
          .findFirst();
      if (first.isPresent()) {
        first.get().addInfo(pasefMsMsInfo);
      } else {
        final Frame msMsFrame = pasefMsMsInfo.getMsMsFrame();
        if (msMsFrame == null) {
          continue;
        }

        var infoMobilityRange = Range.singleton(msMsFrame.getMobilityForMobilityScanNumber(
            pasefMsMsInfo.getSpectrumNumberRange().lowerEndpoint())).span(Range.singleton(
            msMsFrame.getMobilityForMobilityScanNumber(
                pasefMsMsInfo.getSpectrumNumberRange().upperEndpoint())));
        gaps.add(
            new Ms2ImsGap(null, file, mzTolerance.getToleranceRange(pasefMsMsInfo.getIsolationMz()),
                rtTolerance.getToleranceRange(msMsFrame.getRetentionTime()),
                mobTolerance.getToleranceRange(
                    RangeUtils.rangeCenter(infoMobilityRange).floatValue()),
                binningMobilogramDataAccess, pasefMsMsInfo));
      }
      finished = 0.3 * i / (double) infos.size();
    }
    logger.finest(
        () -> "Built " + gaps.size() + " Ms2ImsGaps from " + infos.size() + " PasefMsMsinfos.");
    return gaps;
  }

  private List<ModularFeatureListRow> buildMs2Chromatograms(final List<Ms2ImsGap> gaps,
      BinningMobilogramDataAccess binningMobilogramDataAccess) {

    logger.finest(() -> "Building MSMS spectra and traces.");

    AtomicInteger processedGaps = new AtomicInteger(0);
    final int numGaps = gaps.size();

    final List<IonMobilogramTimeSeries> ionMobilogramTimeSeries = gaps.stream().map(gap -> {
      if (isCanceled()) {
        return null;
      }
      final MZTolerance mergeTol = new MZTolerance(0.005, 15);
      final List<MergedMsMsSpectrum> mergedMsMsSpectra = gap.getInfos().stream().map(
          info -> SpectraMerging.getMergedMsMsSpectrumForPASEF(info, mergeTol, MergingType.SUMMED,
              flist.getMemoryMapStorage(), null, null)).toList();
      final double[] intensities = mergedMsMsSpectra.stream()
          .mapToDouble(s -> Objects.requireNonNullElse(s.getTIC(), 0d)).toArray();
      final double[] mzs = mergedMsMsSpectra.stream()
          .mapToDouble(scan -> ((DDAMsMsInfo) scan.getMsMsInfo()).getIsolationMz()).toArray();

      final List<IonMobilitySeries> mobilograms = new ArrayList<>();
      for (int i = 0; i < mergedMsMsSpectra.size(); i++) {
        final MergedMsMsSpectrum msms = mergedMsMsSpectra.get(i);
        List<MobilityScan> sourceSpectra = (List<MobilityScan>) (List<? extends MassSpectrum>) msms.getSourceSpectra();
        final double[] mobIntensities = new double[sourceSpectra.size()];
        final double[] mobilities = new double[sourceSpectra.size()];
        final double[] mobMzs = new double[sourceSpectra.size()];
        Arrays.fill(mobMzs, mzs[i]);
        for (int j = 0; j < sourceSpectra.size(); j++) {
          final MobilityScan mobScan = sourceSpectra.get(j);
          mobIntensities[j] = mobScan.getTIC();
          mobilities[j] = mobScan.getMobility();
        }

        mobilograms.add(
            new SimpleIonMobilitySeries(flist.getMemoryMapStorage(), mobMzs, mobIntensities,
                sourceSpectra));
      }

      finished = 0.3 + 0.6 * processedGaps.getAndIncrement() / (double) numGaps;

      return IonMobilogramTimeSeriesFactory.of(flist.getMemoryMapStorage(), mzs, intensities,
          mobilograms, binningMobilogramDataAccess);
    }).toList();

    if (isCanceled()) {
      return null;
    }

    logger.finest(() -> "Building features.");
    final List<ModularFeature> features = new ArrayList<>(ionMobilogramTimeSeries.stream()
        .map(series -> new ModularFeature(flist, file, series, FeatureStatus.DETECTED)).toList());
    features.sort(Comparator.comparingDouble(ModularFeature::getMZ));

    logger.finest(() -> "Building rows.");
    final AtomicInteger id = new AtomicInteger(0);
    return features.stream().map(f -> new ModularFeatureListRow(flist, id.getAndIncrement(), f))
        .toList();
  }

}
