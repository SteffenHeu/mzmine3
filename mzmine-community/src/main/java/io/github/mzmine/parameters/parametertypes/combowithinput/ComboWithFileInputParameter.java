/*
 * Copyright (c) 2004-2025 The mzmine Development Team
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

package io.github.mzmine.parameters.parametertypes.combowithinput;

import io.github.mzmine.datamodel.utils.UniqueIdSupplier;
import io.github.mzmine.parameters.UserParameter;
import io.github.mzmine.parameters.parametertypes.filenames.FileNameParameter;
import javafx.collections.ObservableList;

/**
 * A combo box of predefined options plus a custom {@link FileNameParameter} that activates when the
 * input-trigger option is selected. Useful for shipping predefined (bundled) resources while still
 * allowing the user to browse to a custom file.
 */
public class ComboWithFileInputParameter<EnumType extends UniqueIdSupplier> extends
    ComboWithInputParameter<EnumType, ComboWithFileInputValue<EnumType>, FileNameParameter> {

  public ComboWithFileInputParameter(FileNameParameter embeddedParameter, EnumType[] values,
      EnumType inputTrigger, ComboWithFileInputValue<EnumType> defaultValue) {
    super(embeddedParameter, values, inputTrigger, defaultValue);
  }

  public ComboWithFileInputParameter(FileNameParameter embeddedParameter,
      ObservableList<EnumType> values, EnumType inputTrigger,
      ComboWithFileInputValue<EnumType> defaultValue) {
    super(embeddedParameter, values, inputTrigger, defaultValue);
  }

  @Override
  public UserParameter<ComboWithFileInputValue<EnumType>, ComboWithInputComponent<EnumType>> cloneParameter() {
    final FileNameParameter embeddedClone = embeddedParameter.cloneParameter();
    final ComboWithFileInputValue<EnumType> clonedValue = new ComboWithFileInputValue<>(
        value.getSelectedOption(), value.embeddedValue());
    final ComboWithFileInputParameter<EnumType> clone = new ComboWithFileInputParameter<>(
        embeddedClone, choices, inputTrigger, clonedValue);
    clone.setValue(clonedValue);
    return clone;
  }

  @Override
  public ComboWithFileInputValue<EnumType> createValue(EnumType option,
      FileNameParameter embeddedParameter) {
    return new ComboWithFileInputValue<>(option, embeddedParameter.getValue());
  }
}
