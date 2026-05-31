package lan.citadel.device_gateway.tvs;

public interface PersistentConnection {
    Boolean connect();
    void disconnect();
}
