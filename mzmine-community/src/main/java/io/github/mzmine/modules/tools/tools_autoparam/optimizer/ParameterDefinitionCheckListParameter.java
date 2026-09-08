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
import io.github.mzmine.modules.tools.tools_autoparam.estimation.ParameterDefinition;
import io.github.mzmine.parameters.UserParameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javafx.collections.FXCollections;
import javafx.scene.layout.Priority;
import org.controlsfx.control.CheckListView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * A checklist of typed definitions. XML stores {@link ParameterDefinition#id()} while the UI uses
 * {@link ParameterDefinition#name()}.
 */
public class ParameterDefinitionCheckListParameter implements
    UserParameter<List<ParameterDefinition<?>>, CheckListView<ParameterDefinition<?>>> {

  private final String name;
  private final String description;
  private final List<ParameterDefinition<?>> choices;
  private List<ParameterDefinition<?>> value;
  @Nullable
  private WizardSequence wizardSequence;

  /**
   * @param name        parameter name shown in the UI
   * @param description tooltip/description
   * @param choices     all available definitions (used as checklist items)
   * @param value       initially selected definitions; pass a copy of {@code choices} to select
   *                    all
   */
  public ParameterDefinitionCheckListParameter(@NotNull String name, @NotNull String description,
      @NotNull List<ParameterDefinition<?>> choices, @NotNull List<ParameterDefinition<?>> value) {
    this.name = name;
    this.description = description;
    this.choices = List.copyOf(choices);
    this.value = new ArrayList<>(value);
  }

  @Override
  public @NotNull String getName() {
    return name;
  }

  @Override
  public @NotNull String getDescription() {
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
  private @NotNull List<ParameterDefinition<?>> getFilteredChoices() {
    if (wizardSequence == null) {
      return choices;
    }
    final Set<ParameterDefinition<?>> available = Set.copyOf(
        OptimizerParameters.collectSolutions(wizardSequence));
    // decision: preserve the checklist instances while matching stable parameter identities.
    return choices.stream().filter(available::contains).toList();
  }

  @Override
  public @NotNull Priority getComponentVgrowPriority() {
    return Priority.SOMETIMES;
  }

  @Override
  public @NotNull CheckListView<ParameterDefinition<?>> createEditingComponent() {
    final CheckListView<ParameterDefinition<?>> view = new CheckListView<>(
        FXCollections.observableArrayList(getFilteredChoices()));
    view.setPrefHeight(200);
    return view;
  }

  @Override
  public @NotNull List<ParameterDefinition<?>> getValue() {
    final List<ParameterDefinition<?>> clone = new ArrayList<>(value);
    clone.retainAll(getFilteredChoices());
    return clone;
  }

  @Override
  public void setValue(@NotNull List<ParameterDefinition<?>> newValue) {
    this.value = new ArrayList<>(newValue);
  }

  @Override
  public void setValueFromComponent(@NotNull CheckListView<ParameterDefinition<?>> component) {
    this.value = new ArrayList<>(component.getCheckModel().getCheckedItems());
  }

  @Override
  public void setValueToComponent(@NotNull CheckListView<ParameterDefinition<?>> component,
      @Nullable List<ParameterDefinition<?>> newValue) {
    component.getCheckModel().clearChecks();
    if (newValue == null) {
      return;
    }
    final List<ParameterDefinition<?>> componentItems = component.getItems();
    for (final ParameterDefinition<?> selected : newValue) {
      // only check items actually present in the (possibly filtered) component list
      if (componentItems.contains(selected)) {
        component.getCheckModel().check(selected);
      }
    }
  }

  @Override
  public void loadValueFromXML(@NotNull Element xmlElement) {
    final NodeList items = xmlElement.getElementsByTagName("item");
    final List<ParameterDefinition<?>> loaded = new ArrayList<>();
    for (int i = 0; i < items.getLength(); i++) {
      final String itemName = items.item(i).getTextContent();
      choices.stream().filter(c -> c.id().equals(itemName)).findFirst().ifPresent(loaded::add);
    }
    this.value = loaded;
  }

  @Override
  public void saveValueToXML(@NotNull Element xmlElement) {
    if (value == null) {
      return;
    }
    final Document doc = xmlElement.getOwnerDocument();
    for (final ParameterDefinition<?> s : value) {
      final Element item = doc.createElement("item");
      item.setTextContent(s.id());
      xmlElement.appendChild(item);
    }
  }

  @Override
  public boolean checkValue(@NotNull Collection<String> errorMessages) {
    if (value == null || value.isEmpty()) {
      errorMessages.add(name + ": at least one parameter to optimize must be selected.");
      return false;
    }
    return true;
  }

  @Override
  public @NotNull ParameterDefinitionCheckListParameter cloneParameter() {
    return new ParameterDefinitionCheckListParameter(name, description, choices,
        value != null ? value : List.of());
  }
}
