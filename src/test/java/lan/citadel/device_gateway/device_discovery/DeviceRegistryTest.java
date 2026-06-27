package lan.citadel.device_gateway.device_discovery;

import lan.citadel.device_gateway.exceptions.DeviceNotFoundException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceRegistryTest {

    private final DeviceRegistry registry = new DeviceRegistry();

    private static Device ad(String host, String name, Manufacturer mfr, int ttl, DeviceType type) {
        return new Device(host, name, mfr, 8001, ttl, type);
    }

    @Test
    void addsNewAdvertisementAsLogicalDevice() {
        registry.addDevice(ad("10.0.0.5", "Living Room TV", Manufacturer.SAMSUNG, 1800, DeviceType.TV));

        LogicalDevice device = registry.getDevice("10.0.0.5");
        assertThat(device).isNotNull();
        assertThat(device.host()).isEqualTo("10.0.0.5");
        assertThat(device.deviceName()).isEqualTo("Living Room TV");
        assertThat(device.manufacturer()).isEqualTo(Manufacturer.SAMSUNG);
        assertThat(device.deviceType()).isEqualTo(DeviceType.TV);
    }

    @Test
    void getDeviceThrowsForUnknownHost() {
        assertThatThrownBy(() -> registry.getDevice("192.168.1.1"))
                .isInstanceOf(DeviceNotFoundException.class);
    }

    @Test
    void vaguerAdvertisementDoesNotDegradeExistingMetadata() {
        registry.addDevice(ad("10.0.0.5", "Living Room TV", Manufacturer.SAMSUNG, 1800, DeviceType.TV));
        // An mDNS airplay record for the same host that reports nothing useful.
        registry.updateDevice(ad("10.0.0.5", "airplay", Manufacturer.UNKNOWN, 120, DeviceType.UNKNOWN));

        LogicalDevice device = registry.getDevice("10.0.0.5");
        assertThat(device.deviceName()).isEqualTo("Living Room TV");
        assertThat(device.manufacturer()).isEqualTo(Manufacturer.SAMSUNG);
        assertThat(device.deviceType()).isEqualTo(DeviceType.TV);
    }

    @Test
    void unknownFieldsAreUpgradedMonotonicallyAsBetterAdsArrive() {
        // First sighting carries no useful classification.
        registry.addDevice(ad("10.0.0.7", "raw-ssdp", Manufacturer.UNKNOWN, 1800, DeviceType.UNKNOWN));
        // A later, richer advertisement fills in the gaps and takes over the friendly name.
        registry.updateDevice(ad("10.0.0.7", "Bedroom TV", Manufacturer.SAMSUNG, 1800, DeviceType.TV));

        LogicalDevice device = registry.getDevice("10.0.0.7");
        assertThat(device.deviceName()).isEqualTo("Bedroom TV");
        assertThat(device.manufacturer()).isEqualTo(Manufacturer.SAMSUNG);
        assertThat(device.deviceType()).isEqualTo(DeviceType.TV);
    }

    @Test
    void firstKnownValueSticksAndUnknownOnlyFillsGaps() {
        // Known type, unknown manufacturer (score 2).
        registry.addDevice(ad("10.0.0.9", "TV-by-type", Manufacturer.UNKNOWN, 1800, DeviceType.TV));
        // Unknown type, known manufacturer (score 1): fills the manufacturer gap but must not
        // overwrite the already-known type, and its lower-scoring name must not win.
        registry.updateDevice(ad("10.0.0.9", "TV-by-mfr", Manufacturer.SAMSUNG, 1800, DeviceType.UNKNOWN));

        LogicalDevice device = registry.getDevice("10.0.0.9");
        assertThat(device.deviceType()).isEqualTo(DeviceType.TV);
        assertThat(device.manufacturer()).isEqualTo(Manufacturer.SAMSUNG);
        assertThat(device.deviceName()).isEqualTo("TV-by-type");
    }

    @Test
    void getTelevisionsReturnsOnlyTvDevices() {
        registry.addDevice(ad("10.0.0.1", "TV One", Manufacturer.SAMSUNG, 1800, DeviceType.TV));
        registry.addDevice(ad("10.0.0.2", "TV Two", Manufacturer.SAMSUNG, 1800, DeviceType.TV));
        registry.addDevice(ad("10.0.0.3", "Mystery", Manufacturer.UNKNOWN, 1800, DeviceType.UNKNOWN));

        assertThat(registry.getTelevisions())
                .extracting(LogicalDevice::host)
                .containsExactlyInAnyOrder("10.0.0.1", "10.0.0.2");
    }

    @Test
    void getDevicesFiltersByType() {
        registry.addDevice(ad("10.0.0.1", "TV One", Manufacturer.SAMSUNG, 1800, DeviceType.TV));
        registry.addDevice(ad("10.0.0.3", "Mystery", Manufacturer.UNKNOWN, 1800, DeviceType.UNKNOWN));

        assertThat(registry.getDevicesByType(DeviceType.UNKNOWN))
                .extracting(LogicalDevice::host)
                .containsExactly("10.0.0.3");
    }

    @Test
    void clearEmptiesTheRegistry() {
        registry.addDevice(ad("10.0.0.1", "TV One", Manufacturer.SAMSUNG, 1800, DeviceType.TV));
        registry.clear();

        assertThatThrownBy(() -> registry.getDevice("10.0.0.1"))
                .isInstanceOf(DeviceNotFoundException.class);
        assertThat(registry.getTelevisions()).isEmpty();
    }
}
