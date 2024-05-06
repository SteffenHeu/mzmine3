/*
 * Copyright (c) 2004-2024 The MZmine Development Team
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

package io.github.mzmine.modules.tools.id_fraggraph.mvci;

import static io.github.mzmine.javafx.components.factories.FxLabels.*;
import static io.github.mzmine.javafx.components.util.FxLayout.newAccordion;
import static io.github.mzmine.javafx.components.util.FxLayout.newHBox;

import io.github.mzmine.javafx.components.util.FxLayout;
import io.github.mzmine.javafx.mvci.FxViewBuilder;
import io.github.mzmine.main.ConfigService;
import io.github.mzmine.modules.tools.id_fraggraph.graphstream.SignalFormulaeModel;
import io.github.mzmine.modules.visualization.networking.visual.NetworkPane;
import io.github.mzmine.util.FormulaUtils;
import io.github.mzmine.util.components.FormulaTextField;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import org.graphstream.graph.Edge;
import org.graphstream.graph.Element;
import org.graphstream.graph.Node;
import org.jetbrains.annotations.NotNull;

class FragmentGraphBuilder extends FxViewBuilder<FragmentGraphModel> {

  private static final Logger logger = Logger.getLogger(FragmentGraphBuilder.class.getName());

  FragmentGraphBuilder(FragmentGraphModel model) {
    super(model);
  }

  @Override
  public Region build() {
    final BorderPane pane = new BorderPane();
    addGraphListenerForNetworkUpdate(pane);

    final TextField formulaField = createBoundFormulaTextField();
    final Label formulaMassLabel = createBoundFormulaMassLabel();

//    final Accordion settingsAccordion = newAccordion(true, newTitledPane("Precursor settings",
//        newHBox(new Label("Precursor formula: "), formulaField, new Label("m/z:"),
//            formulaMassLabel)));
    return pane;
  }

  @NotNull
  private Label createBoundFormulaMassLabel() {
    final Label formulaMassLabel = new Label();
    formulaMassLabel.textProperty().bind(Bindings.createStringBinding(() -> {
      if (model.getPrecursorFormula() == null) {
        return "Cannot parse formula";
      }
      final double mz = FormulaUtils.calculateMzRatio(model.getPrecursorFormula());
      return ConfigService.getGuiFormats().mz(mz);
    }, model.precursorFormulaProperty()));
    return formulaMassLabel;
  }

  @NotNull
  private TextField createBoundFormulaTextField() {
    final FormulaTextField formulaField = new FormulaTextField();
    formulaField.editableProperty().bind(model.precursorFormulaEditableProperty());
    formulaField.formulaProperty().bindBidirectional(model.precursorFormulaProperty());
    return formulaField;
  }

  private void addGraphListenerForNetworkUpdate(BorderPane pane) {
    final List<ChangeListener<ObservableList<SignalFormulaeModel>>> oldNodeListeners = new ArrayList<>();

    model.graphProperty().addListener((_, _, graph) -> {
      pane.setCenter(null);
      for (ChangeListener<ObservableList<SignalFormulaeModel>> old : oldNodeListeners) {
        model.selectedNodesProperty().removeListener(old);
      }

      if (graph == null) {
        return;
      }
      NetworkPane network = new NetworkPane(graph.getId(), false, graph);
      network.showFullGraph();

      model.getAllNodes().forEach(nodeModel -> {
        nodeModel.clearPassThroughGraphs();
        nodeModel.addPassThroughGraph(network.getGraphicGraph());
      });
      // update mappings to filtered nodes
//      network.getGraph().getEdgeFilteredGraph().nodes().forEach(node -> {
//        final SignalFormulaeModel nodeModel = model.getAllNodes().get(node.getId());
//        if (nodeModel != null) {
//          nodeModel.setFilteredNode(node);
//        }
//      });

      pane.setCenter(network);

      // listen to changes in network selection and map to model
      network.getSelectedNodes().addListener((ListChangeListener<Node>) c -> {
        c.next();
        final ObservableList<? extends Node> selected = c.getList();
        var selectedNodes = selected.stream().map(Element::getId)
            .map(id -> model.getAllNodesMap().get(id)).filter(Objects::nonNull).toList();
//        model.selectedNodesProperty().clear();
        if (model.getSelectedNodes().equals(selectedNodes)) {
          return;
        }
        model.setSelectedNodes(FXCollections.observableArrayList(selectedNodes));
        logger.finest(() -> STR."Selected nodes: \{selectedNodes.toString()}");
      });

      // listen to changes in model and map to graph
      final ChangeListener<ObservableList<SignalFormulaeModel>> modelToGraphNodeListener = createModelToGraphNodeListener(
          network);
      model.selectedNodesProperty().addListener(modelToGraphNodeListener);
      oldNodeListeners.add(modelToGraphNodeListener);

      network.getSelectedEdges().addListener((ListChangeListener<? super Edge>) c -> {
        c.next();
        final ObservableList<? extends Edge> selected = c.getList();
        var selectedEdges = selected.stream().map(Element::getId)
            .map(id -> model.getAllEdgesMap().get(id)).filter(Objects::nonNull).toList();
        model.getSelectedEdges().clear();
        model.getSelectedEdges().addAll(selectedEdges);
        logger.finest(() -> STR."Selected edges: \{selectedEdges.toString()}");
      });
    });
  }

  @NotNull
  private ChangeListener<ObservableList<SignalFormulaeModel>> createModelToGraphNodeListener(
      NetworkPane network) {
    return (_, _, n) -> {
      if (network.getSelectedNodes().stream().map(Element::getId)
          .map(id -> model.getAllNodesMap().get(id)).filter(Objects::nonNull).toList().equals(n)) {
        return;
      }
      var selectedNodes = n.stream().map(SignalFormulaeModel::getId)
          .map(id -> model.getAllNodesMap().get(id)).filter(Objects::nonNull)
          .map(SignalFormulaeModel::getUnfilteredNode).toList();
      network.getSelectedNodes().setAll(selectedNodes);
    };
  }
}