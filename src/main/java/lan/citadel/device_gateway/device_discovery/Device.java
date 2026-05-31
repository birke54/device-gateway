package lan.citadel.device_gateway.device_discovery;

import org.jspecify.annotations.NonNull;

/**
 * A single discovery advertisement. These are transient inputs to {@link DeviceRegistry}, which
 * merges all advertisements sharing a host into one {@link LogicalDevice}; they are not stored.
 */
public record Device(@NonNull String hostName, @NonNull String deviceName, @NonNull Manufacturer manufacturer,
                     @NonNull Integer port, @NonNull Integer ttl, @NonNull DeviceType deviceType) {

    @Override
    @NonNull
    public String toString() {
        return "{ " + this.deviceName + ", " + this.manufacturer + ", " + this.hostName + ", " + this.port + ", " + this.deviceType + ", " + this.ttl + " }";
    }
}

