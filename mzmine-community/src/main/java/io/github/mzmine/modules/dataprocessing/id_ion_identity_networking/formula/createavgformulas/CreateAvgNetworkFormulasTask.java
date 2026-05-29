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
package io.github.mzmine.modules.dataprocessing.id_ion_identity_networking.formula.createavgformulas;


import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.identities.iontype.IonNetwork;
import io.github.mzmine.datamodel.identities.iontype.IonNetworkLogic;
import io.github.mzmine.modules.dataprocessing.id_formula_sort.FormulaSortParameters;
import io.github.mzmine.modules.dataprocessing.id_formula_sort.FormulaSortTask;
import io.github.mzmine.modules.dataprocessing.id_formulaprediction.ResultFormula;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;

public class CreateAvgNetworkFormulasTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(
      CreateAvgNetworkFormulasTask.class.getName());

  private ModularFeatureList featureList;
  private String message;
  private int totalRows;
  private final AtomicInteger finishedNets = new AtomicInteger(0);
  private final boolean sortResults;
  private FormulaSortTask sorter;

  /**
   *
   */
/*  public CreateAvgNetworkFormulasTask() {
    super(null, );
    sortResults = false;
    this.sorter = null;
    message = "Creation of average molecular formulas for MS annotation networks";
  }*/
  public CreateAvgNetworkFormulasTask(FormulaSortTask sorter, @NotNull Instant moduleCallDate) {
    super(null, moduleCallDate);
    sortResults = sorter != null;
    this.sorter = sorter;
    message = "Creation of average molecular formulas for MS annotation networks";
  }

/*  public CreateAvgNetworkFormulasTask(FormulaSortParameters parameters) {
    super(null, );
    sortResults = true;
    FormulaSortParameters sortingParam =
        parameters.getParameter(CreateAvgNetworkFormulasParameters.sorting).getEmbeddedParameters();
    sorter = new FormulaSortTask(sortingParam);
    message = "Creation of average molecular formulas for MS annotation networks";
  }*/

  public CreateAvgNetworkFormulasTask(ModularFeatureList featureList, ParameterSet parameters,
      @NotNull Instant moduleCallDate) {
    super(featureList.getMemoryMapStorage(), moduleCallDate);
    this.featureList = featureList;

    sortResults = parameters.getParameter(CreateAvgNetworkFormulasParameters.sorting).getValue();
    if (sortResults) {
      FormulaSortParameters sortingParam = parameters.getParameter(
          CreateAvgNetworkFormulasParameters.sorting).getEmbeddedParameters();
      sorter = new FormulaSortTask(sortingParam, getModuleCallDate());
    }
    message = "Creation of average molecular formulas for MS annotation networks";
  }

  /**
   *
   */
  @Override
  public double getFinishedPercentage() {
    if (totalRows == 0) {
      return 0.0;
    }
    return finishedNets.get() / (double) totalRows;
  }

  /**
   *
   */
  @Override
  public String getTaskDescription() {
    return message;
  }

  /**
   * @see Runnable#run()
   */
  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);

    // get all networks to run in parallel
    IonNetwork[] nets = IonNetworkLogic.getAllNetworks(featureList, false);
    totalRows = nets.length;
    if (totalRows == 0) {
      setStatus(TaskStatus.ERROR);
      setErrorMessage("No annotation networks found in this list. Run MS annotation");
      cancel();
      return;
    }

    // parallel
    Arrays.stream(nets).forEach(net -> {
      message = "Average formula creation on " + net.getID();
      if (!isCanceled()) {
        combineFormulasOfNetwork(net);
      }
      finishedNets.incrementAndGet();
    });

    logger.info("Finished formula search for all networks");
    setStatus(TaskStatus.FINISHED);
  }

  public List<ResultFormula> combineFormulasOfNetwork(IonNetwork net) {
    // TODO: per-ion formula lists have been removed (formulas now live solely on the IonNetwork).
    // The original "consensus across ions" pass relied on comparing distinct per-ion candidate
    // lists; that distinction no longer exists. As a degraded behavior we just (optionally)
    // re-sort the network's existing list. The module is kept available for batch compatibility
    // but no longer produces a real consensus — consider deprecating or rewriting against the
    // network-level list directly.
    final List<ResultFormula> results = new ArrayList<>(net.getMolFormulas());
    if (!results.isEmpty() && sortResults && sorter != null) {
      sorter.sort(results, net.getNeutralMass());
      net.clearMolFormulas();
      net.addMolFormulas(results);
    }
    return results;
  }

  /**
   * Create an average formula for all present formulas in all lists
   *
   * @param allLists
   * @param results
   */
  private void createAllAvgFormulas(List<List<ResultFormula>> allLists,
      List<ResultFormula> results) {
    removeEmptyLists(allLists);
    // need to have more than one list
    if (allLists.size() <= 1) {
      return;
    }
    // create average formula
    List<ResultFormula> list = allLists.get(0);
    ResultFormula f = list.remove(0);
    removeEmptyLists(allLists);

    AverageResultFormula avg = new AverageResultFormula(f);
    // add all matches
    addAllMatchingFormula(allLists, avg);

    if (avg.getFormulas().size() > 1) {
      results.add(avg);
    }

    createAllAvgFormulas(allLists, results);
  }

  private void removeEmptyLists(List<List<ResultFormula>> allLists) {
    for (int i = 0; i < allLists.size(); ) {
      if (allLists.get(i).isEmpty()) {
        allLists.remove(i);
      } else {
        i++;
      }
    }
  }

  /**
   * Searches all matching molecular formulas, adds them to the avgFormula and removes from the
   * lists
   */
  private void addAllMatchingFormula(List<List<ResultFormula>> allLists, AverageResultFormula avg) {
    for (List<ResultFormula> list : allLists) {
      for (int i = 0; i < list.size(); ) {
        ResultFormula f = list.get(i);
        // compare and add (remove fitting)
        if (avg.isMatching(f)) {
          avg.addFormula(f);
          // remove from list
          list.remove(i);
        } else {
          i++;
        }
      }
    }
  }

}
