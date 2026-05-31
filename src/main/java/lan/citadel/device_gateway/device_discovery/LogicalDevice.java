package lan.citadel.device_gateway.device_discovery;

import org.jspecify.annotations.NonNull;

/**
 * A single physical device as presented to clients, collapsed from the one-or-more discovery
 * advertisements ({@link Device}) that share a host. A Samsung TV, for example, publishes several
 * UPnP root devices plus mDNS services; they are all the same TV.
 * <p>
 * No port is exposed: discovery ports are per-service (UPnP/airplay) and are not the device's
 * control port. The control port is resolved later in the TV layer.
 */
public record LogicalDevice(@NonNull String host, @NonNull String deviceName,
                            @NonNull Manufacturer manufacturer, @NonNull DeviceType deviceType) {
}
