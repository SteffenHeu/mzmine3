package io.github.mzmine.gui.mainwindow.tabs.processingreport;

import io.github.mzmine.datamodel.PeakList;
import io.github.mzmine.datamodel.RawDataFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;
import javafx.collections.ModifiableObservableListBase;
import javax.annotation.Nonnull;


/**
 * A log of all processing modules run in this project. Implements {@link
 * ModifiableObservableListBase}, so listeners can be added. Does not support {@link List#add(int,
 * Object)}, set or remove operations to keep integrity of the log.
 */
public class ProcessingLog extends ModifiableObservableListBase<ProcessingStepReport> {

  public static final Logger logger = Logger.getLogger(ProcessingLog.class.getName());

  private final List<ProcessingStepReport> delegate;

  public ProcessingLog() {
    super();
    delegate = new ArrayList<>();
  }

  /**
   * Returns all {@link ProcessingStepReport}s for the given featureList.
   *
   * @param featureList the feature lists
   * @return List of {@link ProcessingStepReport}s or an empty list.
   */
  @Nonnull
  public List<ProcessingStepReport> getProcessingStepsForFeatureList(PeakList featureList) {
    List<ProcessingStepReport> appliedSteps = new ArrayList<>();
    for (ProcessingStepReport report : this) {
      if (report.getFeatureLists().contains(featureList)) {
        appliedSteps.add(report);
      }
    }
    return appliedSteps;
  }

  /**
   * Returns all {@link ProcessingStepReport}s for the given rawDataFile.
   *
   * @param rawDataFile the feature lists
   * @return List of {@link ProcessingStepReport}s or an empty list.
   */
  @Nonnull
  public List<ProcessingStepReport> getProcessingStepsForRawDataFile(RawDataFile rawDataFile) {
    List<ProcessingStepReport> appliedSteps = new ArrayList<>();
    for (ProcessingStepReport report : this) {
      if (report.getRawDataFiles().contains(rawDataFile)) {
        appliedSteps.add(report);
      }
    }
    return appliedSteps;
  }

  @Override
  public ProcessingStepReport get(int index) {
    return delegate.get(index);
  }

  @Override
  public int size() {
    return delegate.size();
  }

  @Override
  protected void doAdd(int index, ProcessingStepReport element) {
    delegate.add(index, element);
  }

  @Override
  protected ProcessingStepReport doSet(int index, ProcessingStepReport element) {
    throw new UnsupportedOperationException(
        "Setting to a specific position is not supported. This is an audit log.");
  }

  @Override
  protected ProcessingStepReport doRemove(int index) {
    return delegate.remove(index);
  }

  @Override
  public void add(int index, ProcessingStepReport element) {
    throw new UnsupportedOperationException(
        "Adding to a specific position is not supported. This is an audit log.");
  }

  @Override
  public boolean addAll(int index, Collection<? extends ProcessingStepReport> c) {
    throw new UnsupportedOperationException("Adding to a specific position is not supported.");
  }

  @Override
  protected void removeRange(int fromIndex, int toIndex) {
    throw new UnsupportedOperationException(
        "Removing elements is not supported. This is an audit log.");
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    throw new UnsupportedOperationException(
        "Removing elements is not supported. This is an audit log.");
  }

  @Override
  public ProcessingStepReport set(int index, ProcessingStepReport element) {
    throw new UnsupportedOperationException(
        "Setting to a specific position is not supported. This is an audit log.");
  }

  @Override
  public boolean remove(Object o) {
    throw new UnsupportedOperationException(
        "Removing elements is not supported. This is an audit log.");
  }

  @Override
  public ProcessingStepReport remove(int index) {
    return super.remove(index);
  }
}
