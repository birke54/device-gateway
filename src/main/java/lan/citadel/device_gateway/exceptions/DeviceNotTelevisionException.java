package lan.citadel.device_gateway.exceptions;

import lan.citadel.device_gateway.device_discovery.LogicalDevice;
import org.jspecify.annotations.NonNull;

/** The requested device exists but is not a television, so it cannot be driven as a remote. */
public class DeviceNotTelevisionException extends RuntimeException {
    public DeviceNotTelevisionException(@NonNull LogicalDevice device) {
        super("Device is not a television: " + device.deviceName() + ", detected type is: " + device.deviceType());
    }
}
