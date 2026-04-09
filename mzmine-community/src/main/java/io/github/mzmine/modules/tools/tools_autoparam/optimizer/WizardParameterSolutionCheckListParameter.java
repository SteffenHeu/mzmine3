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

package io.github.mzmine.modules.tools.tools_autoparam.optimizer;

import io.github.mzmine.modules.tools.batchwizard.WizardSequence;
import io.github.mzmine.parameters.UserParameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.scene.layout.Priority;
import org.controlsfx.control.CheckListView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * A {@link UserParameter} that presents a list of {@link WizardParameterPrototype} choices in a
 * {@link CheckListView}. The parameter value is the list of currently checked (selected) factories.
 * Items are identified by their {@link WizardParameterPrototype#name()} for display and XML
 * serialization.
 */
public class WizardParameterSolutionCheckListParameter implements
    UserParameter<List<WizardParameterPrototype>, CheckListView<WizardParameterPrototype>> {

  private final String name;
  private final String description;
  private final List<WizardParameterPrototype> choices;
  private List<WizardParameterPrototype> value;
  @Nullable
  private WizardSequence wizardSequence;

  /**
   * @param name        parameter name shown in the UI
   * @param description tooltip/description
   * @param choices     all available factories (used as checklist items)
   * @param value       initially selected factories; pass a copy of {@code choices} to select all
   */
  public WizardParameterSolutionCheckListParameter(String name, String description,
      List<WizardParameterPrototype> choices, List<WizardParameterPrototype> value) {
    this.name = name;
    this.description = description;
    this.choices = List.copyOf(choices);
    this.value = new ArrayList<>(value);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getDescription() {
    return description;
  }

  /**
   * Sets the wizard sequence used to filter which of the {@code choices} are visible in the
   * checklist UI. Items selected but filtered out remain in {@link #getValue()} and are preserved
   * in XML. Pass {@code null} to show all choices.
   */
  public void setWizardSequence(@Nullable WizardSequence sequence) {
    this.wizardSequence = sequence;
  }

  /**
   * Returns the subset of {@code choices} that are relevant for the current wizard sequence. When
   * no sequence is set, all choices are returned.
   */
  private @NotNull List<WizardParameterPrototype> getFilteredChoices() {
    if (wizardSequence == null) {
      return choices;
    }
    final Set<String> available = OptimizerParameters.collectSolutions(wizardSequence).stream()
        .map(WizardParameterPrototype::name).collect(Collectors.toSet());
    // decision: filter choices by name so displayed items are always instances from choices (for identity matching in setValueToComponent)
    return choices.stream().filter(c -> available.contains(c.name())).toList();
  }

  @Override
  public Priority getComponentVgrowPriority() {
    return Priority.SOMETIMES;
  }

  @Override
  public CheckListView<WizardParameterPrototype> createEditingComponent() {
    final CheckListView<WizardParameterPrototype> view = new CheckListView<>(
        FXCollections.observableArrayList(getFilteredChoices()));
    view.setPrefHeight(200);
    return view;
  }

  @Override
  public List<WizardParameterPrototype> getValue() {
    List<WizardParameterPrototype> clone = new ArrayList<>(value);
    clone.retainAll(getFilteredChoices());
    return clone;
  }

  @Override
  public void setValue(List<WizardParameterPrototype> newValue) {
    this.value = new ArrayList<>(newValue);
  }

  @Override
  public void setValueFromComponent(CheckListView<WizardParameterPrototype> component) {
    this.value = new ArrayList<>(component.getCheckModel().getCheckedItems());
  }

  @Override
  public void setValueToComponent(CheckListView<WizardParameterPrototype> component,
      @Nullable List<WizardParameterPrototype> newValue) {
    component.getCheckModel().clearChecks();
    if (newValue == null) {
      return;
    }
    final List<WizardParameterPrototype> componentItems = component.getItems();
    for (WizardParameterPrototype selected : newValue) {
      // only check items actually present in the (possibly filtered) component list
      if (componentItems.contains(selected)) {
        component.getCheckModel().check(selected);
      }
    }
  }

  @Override
  public void loadValueFromXML(Element xmlElement) {
    final NodeList items = xmlElement.getElementsByTagName("item");
    final List<WizardParameterPrototype> loaded = new ArrayList<>();
    for (int i = 0; i < items.getLength(); i++) {
      final String itemName = items.item(i).getTextContent();
      choices.stream().filter(c -> c.name().equals(itemName)).findFirst().ifPresent(loaded::add);
    }
    if (!loaded.isEmpty()) {
      this.value = loaded;
    }
  }

  @Override
  public void saveValueToXML(Element xmlElement) {
    if (value == null) {
      return;
    }
    final Document doc = xmlElement.getOwnerDocument();
    for (WizardParameterPrototype s : value) {
      final Element item = doc.createElement("item");
      item.setTextContent(s.name());
      xmlElement.appendChild(item);
    }
  }

  @Override
  public boolean checkValue(Collection<String> errorMessages) {
    if (value == null || value.isEmpty()) {
      errorMessages.add(name + ": at least one parameter to optimize must be selected.");
      return false;
    }
    return true;
  }

  @Override
  public WizardParameterSolutionCheckListParameter cloneParameter() {
    return new WizardParameterSolutionCheckListParameter(name, description, choices,
        value != null ? value : List.of());
  }
}
