package lan.citadel.device_gateway.exceptions;

import lan.citadel.device_gateway.device_discovery.LogicalDevice;
import org.jspecify.annotations.NonNull;

/** A connection to the television could not be established. */
public class TvConnectionException extends RuntimeException {
    public TvConnectionException(@NonNull LogicalDevice device) {
        super("Failed to connect to TV: " + device.deviceName() + ", " + device.host());
    }
}
