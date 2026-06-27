package lan.citadel.device_gateway.device_discovery;

public enum Manufacturer {
    SAMSUNG,
    AMAZON,
    UNKNOWN,
    ;

    /**
     * Infers a manufacturer from a discovery name (UPnP friendly name or mDNS service name).
     * Currently, a simple heuristic: a name containing {@code "Samsung"} is a Samsung TV, everything
     * else is {@link Manufacturer#UNKNOWN}.
     */
    public static Manufacturer fromName(String name) {
        if (name != null) {
            if (name.contains("Samsung")) {
                return Manufacturer.SAMSUNG;
            }
            else if (name.contains("Fire")) {
                return Manufacturer.AMAZON;
            }
        }

        return Manufacturer.UNKNOWN;
    }
}