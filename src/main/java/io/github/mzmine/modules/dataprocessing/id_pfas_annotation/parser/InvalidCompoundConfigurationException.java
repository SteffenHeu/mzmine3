package io.github.mzmine.modules.dataprocessing.id_pfas_annotation.parser;

public class InvalidCompoundConfigurationException extends RuntimeException {
  public InvalidCompoundConfigurationException() {
    super("Required groups not present.");
  }
}
