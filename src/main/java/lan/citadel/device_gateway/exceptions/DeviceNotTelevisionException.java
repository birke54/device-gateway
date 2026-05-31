package lan.citadel.device_gateway.exceptions;

/** The requested device exists but is not a television, so it cannot be driven as a remote. */
public class DeviceNotTelevisionException extends RuntimeException {
    public DeviceNotTelevisionException(String key) {
        super("Device is not a television: " + key);
    }
}
