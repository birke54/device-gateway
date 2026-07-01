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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class RemoteSessionManager {
    private static final Logger logger = LoggerFactory.getLogger(RemoteSessionManager.class);
    private final DeviceRegistry deviceRegistry;
    private final TvRemoteFactory remoteFactory;
    private final GatewayMetrics metrics;
    private Television tv = null;

    public RemoteSessionManager(DeviceRegistry deviceRegistry, TvRemoteFactory remoteFactory, GatewayMetrics metrics) {
        this.deviceRegistry = deviceRegistry;
        this.remoteFactory = remoteFactory;
        this.metrics = metrics;
    }

    public void setActiveRemote(String host) {
        LogicalDevice device = deviceRegistry.getDevice(host);
        if (device == null) {
            throw new DeviceNotFoundException(host);
        }
        if (device.deviceType() != DeviceType.TV) {
            throw new DeviceNotTelevisionException(device);
        }
        Television tvController = remoteFactory.create(device);

        logger.info("Connecting to device {}", device.host());
        // Only persistent-connection tvs need an explicit connection step
        if (tvController instanceof PersistentConnection pc && !pc.connect()) {
            logger.error("Failed to connect to TV {}", device.host());
            metrics.incrementCounter(Meter.CONNECT_TV_FAILURE_COUNT, new HashMap<>(
                    Map.of(Tags.MANUFACTURER, device.manufacturer().toString(),
                            Tags.DEVICE_KEY, device.host())
            ));
            throw new TvConnectionException(device);
        }

        // Promote the new session first, so its connection is tracked even if
        // tearing down the previous one fails.
        Television previous = this.tv;
        this.tv = tvController;
        logger.info("Switched active TV to {}", host);
        metrics.incrementCounter(Meter.CONNECT_TV_SUCCESS_COUNT, new HashMap<>(
                Map.of(Tags.MANUFACTURER, device.manufacturer().toString(),
                        Tags.DEVICE_KEY, device.host())
        ));

        // Best-effort teardown of the previous session if it held a connection.
        if (previous instanceof PersistentConnection oldPc) {
            try {
                oldPc.disconnect();
                metrics.incrementCounter(Meter.DISCONNECT_TV_SUCCESS_COUNT, new HashMap<>(
                        Map.of(Tags.MANUFACTURER, device.manufacturer().toString(),
                                Tags.DEVICE_KEY, previous.host())
                ));
            } catch (Exception e) {
                logger.warn("Failed to disconnect previous TV {}: {}", previous.host(), e.toString());
                metrics.incrementCounter(Meter.DISCONNECT_TV_FAILURE_COUNT, Map.of(
                        Tags.MANUFACTURER, device.manufacturer().toString(),
                        Tags.DEVICE_KEY, device.host()
                ));
            }
        }
    }

    public List<App> getApps(String host) {
        Television active = this.tv;
        if (active == null || !active.host().equals(host)) {
            throw new NoActiveSessionException();
        }
        return metrics.recordSuccessDuration(Meter.RETRIEVE_TV_APPS_DURATION,
                Map.of(Tags.DEVICE_KEY, active.host()),
                active::retrieveApps);
    }

    public void openApp(String host, String appName) {
        Television active = this.tv;
        if (active == null || !active.host().equals(host)) {
            throw new NoActiveSessionException();
        }
        metrics.recordSuccessDuration(Meter.OPEN_TV_APP_DURATION,
            Map.of(Tags.DEVICE_KEY, active.host()),
            () -> active.openApp(appName));
    }

    public void sendKey(String host, RemoteKey key) {
        Television active = this.tv;
        if (active == null || !active.host().equals(host)) {
            throw new NoActiveSessionException();
        }
        metrics.recordSuccessDuration(Meter.SEND_TV_KEY_DURATION,
                Map.of(Tags.DEVICE_KEY, active.host()),
                () -> active.sendKey(key));
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
