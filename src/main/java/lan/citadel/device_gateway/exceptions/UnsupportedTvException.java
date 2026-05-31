package lan.citadel.device_gateway.exceptions;

import lan.citadel.device_gateway.device_discovery.Manufacturer;

/** The device is a television, but its manufacturer is not yet supported by any TV handler. */
public class UnsupportedTvException extends RuntimeException {
    public UnsupportedTvException(Manufacturer manufacturer) {
        super("Unsupported TV manufacturer: " + manufacturer);
    }
}
