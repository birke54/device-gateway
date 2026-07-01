package lan.citadel.device_gateway;

import lan.citadel.device_gateway.device_discovery.DeviceRegistry;
import lan.citadel.device_gateway.device_discovery.DeviceType;
import lan.citadel.device_gateway.device_discovery.LogicalDevice;
import lan.citadel.device_gateway.control.App;
import lan.citadel.device_gateway.control.RemoteKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class ApiController {
    private static final Logger logger = LoggerFactory.getLogger(ApiController.class);

    private final DeviceRegistry deviceRegistry;
    private final RemoteSessionManager sessionManager;

    public ApiController(DeviceRegistry deviceRegistry, RemoteSessionManager sessionManager) {
        this.deviceRegistry = deviceRegistry;
        this.sessionManager = sessionManager;
    }

    @GetMapping("/v1/remote/unknowndevices")
    public List<LogicalDevice> getUnknownDevices() {
        return deviceRegistry.getDevicesByType(DeviceType.UNKNOWN);
    }

    @GetMapping("/v1/remote/tvdevices")
    public List<LogicalDevice> getTvDevices() {
        return deviceRegistry.getDevicesByType(DeviceType.TV);
    }

    @PostMapping("/v1/remote/connect/{tv_device_key}")
    public void connect(@PathVariable("tv_device_key") String deviceKey) {
        sessionManager.setActiveRemote(deviceKey);
    }

    @GetMapping("/v1/remote/{tv_device_key}/apps")
    public List<App> getApps(@PathVariable("tv_device_key") String deviceKey) {
        return sessionManager.getApps(deviceKey);
    }

    @PostMapping("/v1/remote/{tv_device_key}/apps/{app_name}/open")
    public void openApp(@PathVariable("tv_device_key") String deviceKey, @PathVariable("app_name") String appName) {
        sessionManager.openApp(deviceKey, appName);
        logger.info("Opening app {}", appName);
    }

    @GetMapping("/v1/remote/{tv_device_key}/supportedkeys")
    public Set<RemoteKey> getSupportedKeys(@PathVariable("tv_device_key") String deviceKey) {
        return sessionManager.supportedKeys(deviceKey);
    }

    @PostMapping("/v1/remote/{tv_device_key}/presskey/{key}")
    public void pressKey(@PathVariable("tv_device_key") String deviceKey, @PathVariable RemoteKey key) {
        sessionManager.sendKey(deviceKey, key);
        logger.info("Sending key {}", key);
    }

}
