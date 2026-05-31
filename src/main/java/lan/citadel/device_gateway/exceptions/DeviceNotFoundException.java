package lan.citadel.device_gateway.exceptions;

/** No device is registered under the requested key. */
public class DeviceNotFoundException extends RuntimeException {
    public DeviceNotFoundException(String key) {
        super("No device registered for key: " + key);
    }
}
