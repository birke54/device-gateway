package lan.citadel.device_gateway;

import lan.citadel.device_gateway.device_discovery.DeviceRegistry;
import lan.citadel.device_gateway.device_discovery.LogicalDevice;
import lan.citadel.device_gateway.control.App;
import lan.citadel.device_gateway.control.RemoteKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/remote")
public class ApiController {
    private static final Logger logger = LoggerFactory.getLogger(ApiController.class);

    private final DeviceRegistry deviceRegistry;
    private final RemoteSessionManager sessionManager;

    public ApiController(DeviceRegistry deviceRegistry, RemoteSessionManager sessionManager) {
        this.deviceRegistry = deviceRegistry;
        this.sessionManager = sessionManager;
    }

    @GetMapping("/devices")
    public List<LogicalDevice> getDevices() {
        return deviceRegistry.getTelevisions();
    }

    @PostMapping("/connect/{device_key}")
    public void connect(@PathVariable("device_key") String deviceKey) {
        sessionManager.setActiveRemote(deviceKey);
        logger.info("Connecting to device {}", deviceKey);
    }

    @GetMapping("/apps")
    public List<App> getApps() {
        return sessionManager.getApps();
    }

    @PostMapping("/apps/{app_name}/open")
    public void openApp(@PathVariable("app_name") String appName) {
        sessionManager.openApp(appName);
        logger.info("Opening app {}", appName);
    }

    @GetMapping("/keys")
    public Set<RemoteKey> getSupportedKeys() {
        return sessionManager.supportedKeys();
    }

    @PostMapping("/keys/{key}")
    public void pressKey(@PathVariable RemoteKey key) {
        sessionManager.sendKey(key);
        logger.info("Sending key {}", key);
    }

}
