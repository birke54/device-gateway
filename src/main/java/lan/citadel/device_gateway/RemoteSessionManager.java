package lan.citadel.device_gateway;

import lan.citadel.device_gateway.control.DeviceController;
import lan.citadel.device_gateway.control.DeviceControllerRegistry;
import lan.citadel.device_gateway.device_discovery.DeviceRegistry;
import lan.citadel.device_gateway.device_discovery.DeviceType;
import lan.citadel.device_gateway.device_discovery.LogicalDevice;
import lan.citadel.device_gateway.exceptions.DeviceNotFoundException;
import lan.citadel.device_gateway.exceptions.DeviceNotTelevisionException;
import lan.citadel.device_gateway.exceptions.NoActiveSessionException;
import lan.citadel.device_gateway.exceptions.TvConnectionException;
import lan.citadel.device_gateway.exceptions.UnsupportedTvException;
import lan.citadel.device_gateway.control.App;
import lan.citadel.device_gateway.control.RemoteKey;
import lan.citadel.device_gateway.tvs.PersistentConnection;
import lan.citadel.device_gateway.tvs.Television;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class RemoteSessionManager {
    private final DeviceRegistry deviceRegistry;
    private final DeviceControllerRegistry controllerRegistry;
    private Television tv = null;

    public RemoteSessionManager(DeviceRegistry deviceRegistry, DeviceControllerRegistry controllerRegistry) {
        this.deviceRegistry = deviceRegistry;
        this.controllerRegistry = controllerRegistry;
    }

    public void setActiveRemote(String host) {
        LogicalDevice device = deviceRegistry.getDevice(host);
        if (device == null) {
            throw new DeviceNotFoundException(host);
        }
        if (device.deviceType() != DeviceType.TV) {
            throw new DeviceNotTelevisionException(host);
        }
        DeviceController controller = controllerRegistry.create(device)
                .orElseThrow(() -> new UnsupportedTvException(device.manufacturer()));
        // The "active remote" is a TV concept; reject any controller that isn't one.
        if (!(controller instanceof Television newTv)) {
            throw new DeviceNotTelevisionException(host);
        }
        // Only persistent-connection tvs need an explicit connection step
        if (newTv instanceof PersistentConnection pc && !pc.connect()) {
            throw new TvConnectionException(host);
        }

        // Tear down the previous session if it held a connection.
        if (this.tv instanceof PersistentConnection oldPc) {
            oldPc.disconnect();
        }
        this.tv = newTv;
    }

    public List<App> getApps() {
        Television active = this.tv;
        if (active == null) {
            throw new NoActiveSessionException();
        }
        return active.retrieveApps();
    }

    public void openApp(String appName) {
        Television active = this.tv;
        if (active == null) {
            throw new NoActiveSessionException();
        }
        active.openApp(appName);
    }

    public void sendKey(RemoteKey key) {
        Television active = this.tv;
        if (active == null) {
            throw new NoActiveSessionException();
        }
        active.sendKey(key);
    }

    public Set<RemoteKey> supportedKeys() {
        Television active = this.tv;
        if (active == null) {
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
