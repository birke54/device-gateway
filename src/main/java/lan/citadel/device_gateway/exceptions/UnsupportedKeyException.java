package lan.citadel.device_gateway.exceptions;

import lan.citadel.device_gateway.control.RemoteKey;

/** The requested key is part of the canonical vocabulary but is not supported by the active TV. */
public class UnsupportedKeyException extends RuntimeException {
    public UnsupportedKeyException(RemoteKey key) {
        super("Key not supported by the connected TV: " + key);
    }
}
