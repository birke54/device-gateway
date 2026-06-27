package lan.citadel.device_gateway.control;

import lan.citadel.device_gateway.device_discovery.DeviceType;
import lan.citadel.device_gateway.device_discovery.LogicalDevice;
import lan.citadel.device_gateway.device_discovery.Manufacturer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceControllerRegistryTest {

    private static final LogicalDevice DEVICE =
            new LogicalDevice("10.0.0.5", "TV", Manufacturer.SAMSUNG, DeviceType.TV);

}
