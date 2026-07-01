package lan.citadel.device_gateway;

public enum Tags {
    MANUFACTURER("manufacturer"),
    DEVICE_KEY("device_key");

    private final String key;

    Tags(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
