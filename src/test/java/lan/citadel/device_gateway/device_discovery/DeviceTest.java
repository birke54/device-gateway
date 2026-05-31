package lan.citadel.device_gateway.device_discovery;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceTest {

    @Test
    void toStringIncludesKeyFields() {
        Device device = new Device("10.0.0.5", "Living Room TV", Manufacturer.SAMSUNG, 8001, 1800, DeviceType.TV);

        assertThat(device.toString())
                .contains("Living Room TV")
                .contains("SAMSUNG")
                .contains("10.0.0.5")
                .contains("8001")
                .contains("TV")
                .contains("1800");
    }
}
