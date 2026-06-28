package lan.citadel.device_gateway.control;

import lan.citadel.device_gateway.device_discovery.DeviceType;
import lan.citadel.device_gateway.device_discovery.LogicalDevice;
import lan.citadel.device_gateway.device_discovery.Manufacturer;

class DeviceControllerRegistryTest {

    private static final LogicalDevice DEVICE =
            new LogicalDevice("10.0.0.5", "TV", Manufacturer.SAMSUNG, DeviceType.TV);

}
