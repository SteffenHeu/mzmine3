package io.github.mzmine.modules.io.import_library_sqlite.cfm_sqlite;

import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.filenames.FileNameParameter;
import io.github.mzmine.parameters.parametertypes.filenames.FileSelectionType;
import java.util.List;
import javafx.stage.FileChooser.ExtensionFilter;

public class CFMLibraryImportParameters extends SimpleParameterSet {

  public static final FileNameParameter fileName = new FileNameParameter("Database file",
      "The database file.", List.of(new ExtensionFilter("sqlite database", "*.sqlite", "*.db")),
      FileSelectionType.OPEN);

  public CFMLibraryImportParameters() {
    super(new Parameter[]{fileName});
  }
}
