package io.github.mzmine.modules.io.import_rawdata_agilent_d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class AgilentAnalogDeviceTest {

  @Test
  void universalInterfaceBoxByTypeAndDeviceName() {
    assertEquals(AgilentAnalogDevice.UIB2, AgilentAnalogDevice.fromTitle("UIB2"));
    assertEquals(AgilentAnalogDevice.UIB2, AgilentAnalogDevice.fromTitle("UIB"));
    assertEquals(AgilentAnalogDevice.DAD, AgilentAnalogDevice.fromTitle("DiodeArrayDetector"));
    assertNull(AgilentAnalogDevice.fromTitle("UIB3"));
    assertNull(AgilentAnalogDevice.fromTitle(null));
  }
}
