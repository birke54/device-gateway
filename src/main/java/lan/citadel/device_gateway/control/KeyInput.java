package lan.citadel.device_gateway.control;

import java.util.Set;

/** A device that accepts directional/remote key presses. */
public interface KeyInput {
    /** Sends a remote key, translating it to this device's wire representation. */
    void sendKey(RemoteKey key);

    /** The subset of {@link RemoteKey} this device can actually send. */
    Set<RemoteKey> supportedKeys();
}
