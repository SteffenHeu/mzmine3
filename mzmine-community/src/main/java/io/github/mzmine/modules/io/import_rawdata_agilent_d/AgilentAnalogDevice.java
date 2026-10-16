package io.github.mzmine.modules.io.import_rawdata_agilent_d;

import org.jetbrains.annotations.Nullable;

/**
 * Taken from DeviceType from the MHDAC sdk.
 */
enum AgilentAnalogDevice {
  TCC("ThermostattedColumnCompartment"), //
  DAD("DiodeArrayDetector"),  //
  FID("FlameIonizationDetector"), //
  TCD("ThermalConductivityDetector"), //
  RID("RefractiveIndexDetector"), //
  MWD("MultiWavelengthDetector"), //
  VWD("VariableWavelengthDetector"), //
  ECD("ElectronCaptureDetector"), // some kind of current, but which order of magnitude?
  FD("FluorescenceDetector"), // No unit for emission yet?
  ELSD("EvaporativeLightScatteringDetector"), //
  IP("IsocraticPump"), //
  BP("BinaryPump"), //
  QP("QuarternaryPump"), //
  CP("CapillaryPump"), //
  NP("NanoPump"), //
  LFP("LowFlowPump"), //
  UIB2("UIB2"), // "Universal interface box"
  ;

  /**
   * enum name in agilent software
   */
  final String title;

  AgilentAnalogDevice(String title) {
    this.title = title;
  }

  @Nullable
  public static AgilentAnalogDevice fromTitle(@Nullable String title) {
    if (title == null) {
      return null;
    }
    for (var device : values()) {
      if (device.title.equalsIgnoreCase(title)) {
        return device;
      }
    }
    return null;
  }
}
