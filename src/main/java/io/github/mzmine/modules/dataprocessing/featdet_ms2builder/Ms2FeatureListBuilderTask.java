package io.github.mzmine.modules.dataprocessing.featdet_ms2builder;

import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess.ScanDataType;
import io.github.mzmine.datamodel.data_access.ScanDataAccess;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.types.MsMsInfoType;
import io.github.mzmine.datamodel.impl.DDAMsMsInfoImpl;
import io.github.mzmine.datamodel.msms.DDAMsMsInfo;
import io.github.mzmine.datamodel.msms.MsMsInfo;
import io.github.mzmine.datamodel.msms.PasefMsMsInfo;
import io.github.mzmine.modules.dataprocessing.gapfill_peakfinder.Gap;
import io.github.mzmine.modules.dataprocessing.gapfill_peakfinder.multithreaded.ImsGap;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.exceptions.MissingMassListException;
import io.github.mzmine.util.scans.SpectraMerging;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Ms2FeatureListBuilderTask extends AbstractTask {

  private static Logger logger = Logger.getLogger(Ms2FeatureListBuilderTask.class.getName());


  private final ModularFeatureList flist;
  private final MZTolerance mzTolerance;
  private final RawDataFile file;
  private final boolean buildIntensityFromMS2TIC;
  private String featureListName;
  private final ScanSelection scanSelection;
  private final RTTolerance rtTolerance;

  public Ms2FeatureListBuilderTask(MemoryMapStorage storage, Date date) {
    super(storage, date);
    mzTolerance = new MZTolerance(0.005, 15);
    buildIntensityFromMS2TIC = true;
    rtTolerance = null;
    scanSelection = null;
    file = null;
    flist = null;
  }

  @Override
  public String getTaskDescription() {
    return null;
  }

  @Override
  public double getFinishedPercentage() {
    return 0;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);
    final List<PasefMsMsInfo> infos =
        file instanceof IMSRawDataFile imsFile ? imsFile.getFrames().stream().flatMap(
            f -> f.getImsMsMsInfos().stream()).toList() : null;

    infos.sort(Comparator.comparingDouble(PasefMsMsInfo::getIsolationMz));

    final RangeMap<Double, List<PasefMsMsInfo>> map = TreeRangeMap.create();

    // map infos to create m/z ranges
    for (PasefMsMsInfo info : infos) {
      final double isolationMz = info.getIsolationMz();
      List<PasefMsMsInfo> ddaMsMsInfos = map.get(isolationMz);
      if (ddaMsMsInfos == null) {
        ddaMsMsInfos = new ArrayList<>();
        final Range<Double> range = SpectraMerging.createNewNonOverlappingRange(
            map, mzTolerance.getToleranceRange(isolationMz));
        map.put(range, infos);
      }
      ddaMsMsInfos.add(info);
    }

    final List<ImsGap> gaps = new ArrayList<>();
    final Map<Range<Double>, List<PasefMsMsInfo>> mapOfRanges = map.asMapOfRanges();
    for (Entry<Range<Double>, List<PasefMsMsInfo>> entry : mapOfRanges.entrySet()) {
      //gaps.stream().filter(gap -> gap.getMzRange().)
    }

    final ModularFeatureList flist = new ModularFeatureList(featureListName, getMemoryMapStorage(),
        file);
    final List<ModularFeature> features = buildMs2Chromatograms();
  }

  private List<ModularFeature> buildMs2Chromatograms() {
    final ScanDataAccess access = EfficientDataAccess.of(file, ScanDataType.CENTROID, scanSelection);
    while (access.hasNextScan()) {
      try {
        final Scan scan = access.nextScan();


      } catch (MissingMassListException e) {
        logger.log(Level.WARNING, e.getMessage(), e);
      }
    }
    return null;
  }
}
