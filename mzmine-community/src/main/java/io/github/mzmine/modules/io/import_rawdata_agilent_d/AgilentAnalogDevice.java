package io.github.mzmine.modules.io.import_rawdata_agilent_d;

import java.util.List;
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
  // "Universal interface box" (analog in, e.g. charged aerosol detector). The SDK reports UIB2,
  // the device is named UIB in the acquisition's Devices.xml
  UIB2("UIB2", "UIB"), //
  ;

  /**
   * enum name in agilent software
   */
  final String title;
  /**
   * other names of the device type, e.g. the device name in the raw file
   */
  private final List<String> aliases;

  AgilentAnalogDevice(String title, String... aliases) {
    this.title = title;
    this.aliases = List.of(aliases);
  }

  @Nullable
  public static AgilentAnalogDevice fromTitle(@Nullable String title) {
    if (title == null) {
      return null;
    }
    for (var device : values()) {
      if (device.title.equalsIgnoreCase(title) || device.aliases.stream()
          .anyMatch(title::equalsIgnoreCase)) {
        return device;
      }
    }
    return null;
  }
}
