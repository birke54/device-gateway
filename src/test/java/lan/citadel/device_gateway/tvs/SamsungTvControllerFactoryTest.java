package lan.citadel.device_gateway.tvs;

import lan.citadel.device_gateway.control.DeviceController;
import lan.citadel.device_gateway.device_discovery.DeviceType;
import lan.citadel.device_gateway.device_discovery.LogicalDevice;
import lan.citadel.device_gateway.device_discovery.Manufacturer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SamsungTvControllerFactoryTest {

    private final SamsungTvControllerFactory factory = new SamsungTvControllerFactory(mock(TokenStore.class));

    private static LogicalDevice device(Manufacturer manufacturer, DeviceType type) {
        return new LogicalDevice("10.0.0.5", "TV", manufacturer, type);
    }

    @Test
    void supportsSamsungTelevisions() {
        assertThat(factory.supports(device(Manufacturer.SAMSUNG, DeviceType.TV))).isTrue();
    }

    @Test
    void rejectsNonSamsungTelevisions() {
        assertThat(factory.supports(device(Manufacturer.UNKNOWN, DeviceType.TV))).isFalse();
    }

    @Test
    void rejectsSamsungNonTelevisions() {
        assertThat(factory.supports(device(Manufacturer.SAMSUNG, DeviceType.UNKNOWN))).isFalse();
    }

    @Test
    void createsSamsungTelevisionForTheDeviceHost() {
        DeviceController controller = factory.create(device(Manufacturer.SAMSUNG, DeviceType.TV));

        assertThat(controller).isInstanceOf(SamsungTelevision.class);
        assertThat(controller.host()).isEqualTo("10.0.0.5");
    }
}
