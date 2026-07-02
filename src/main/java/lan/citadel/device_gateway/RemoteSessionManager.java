package lan.citadel.device_gateway;

import lan.citadel.device_gateway.control.*;
import lan.citadel.device_gateway.device_discovery.DeviceRegistry;
import lan.citadel.device_gateway.device_discovery.DeviceType;
import lan.citadel.device_gateway.device_discovery.LogicalDevice;
import lan.citadel.device_gateway.exceptions.DeviceNotFoundException;
import lan.citadel.device_gateway.exceptions.DeviceNotTelevisionException;
import lan.citadel.device_gateway.exceptions.NoActiveSessionException;
import lan.citadel.device_gateway.exceptions.TvConnectionException;
import lan.citadel.device_gateway.tvs.PersistentConnection;
import lan.citadel.device_gateway.tvs.Television;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class RemoteSessionManager {
    private static final Logger logger = LoggerFactory.getLogger(RemoteSessionManager.class);
    private final DeviceRegistry deviceRegistry;
    private final TvRemoteFactory remoteFactory;
    private Television tv = null;

    public RemoteSessionManager(DeviceRegistry deviceRegistry, TvRemoteFactory remoteFactory) {
        this.deviceRegistry = deviceRegistry;
        this.remoteFactory = remoteFactory;
    }

    public void setActiveRemote(String host) {
        LogicalDevice device = deviceRegistry.getDevice(host);
        if (device == null) {
            throw new DeviceNotFoundException(host);
        }
        if (device.deviceType() != DeviceType.TV) {
            throw new DeviceNotTelevisionException(device);
        }

        // Early return if the requested device is already active
        if (this.tv != null && device.host().equals(this.tv.host())) {
            return;
        }

        Television tvController = remoteFactory.create(device);
        // Only persistent-connection tvs need an explicit connection step
        if (tvController instanceof PersistentConnection pc && !pc.connect()) {
            throw new TvConnectionException(device);
        }

        // Promote the new session first, so its connection is tracked even if
        // tearing down the previous one fails.
        Television previous = this.tv;
        this.tv = tvController;

        // Best-effort teardown of the previous session if it held a connection.
        if (previous instanceof PersistentConnection oldPc) {
            try {
                oldPc.disconnect();
            } catch (Exception e) {
                logger.warn("Failed to disconnect previous TV {}: {}", previous.host(), e.toString());
            }
        }
    }

    public List<App> getApps(String host) {
        Television active = this.tv;
        if (active == null || !active.host().equals(host)) {
            throw new NoActiveSessionException();
        }
        return active.retrieveApps();
    }

    public void openApp(String host, String appName) {
        Television active = this.tv;
        if (active == null || !active.host().equals(host)) {
            throw new NoActiveSessionException();
        }
        active.openApp(appName);
    }

    public void sendKey(String host, RemoteKey key) {
        Television active = this.tv;
        if (active == null || !active.host().equals(host)) {
            throw new NoActiveSessionException();
        }
        active.sendKey(key);
    }

    public Set<RemoteKey> supportedKeys(String host) {
        Television active = this.tv;
        if (active == null || !active.host().equals(host)) {
            throw new NoActiveSessionException();
        }
        return active.supportedKeys();
    }

    @PreDestroy
    public void shutdown() {
        if (this.tv instanceof PersistentConnection pc) {
            pc.disconnect();
        }
    }
}
