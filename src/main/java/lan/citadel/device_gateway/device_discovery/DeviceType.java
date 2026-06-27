package lan.citadel.device_gateway.device_discovery;

public enum DeviceType {
    TV,
    UNKNOWN,
    // TODO: Add additional types
    ;

    /**
     * Infers a device type from a discovery name (UPnP friendly name or mDNS service name).
     * Currently, a simple heuristic: a name containing {@code "TV"} is a television, everything
     * else is {@link DeviceType#UNKNOWN}.
     */
    public static DeviceType fromName(String name) {
        if (name != null && name.contains("TV")) {
            return TV;
        }
        return UNKNOWN;
    }
}
