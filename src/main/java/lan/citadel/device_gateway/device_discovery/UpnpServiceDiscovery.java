package lan.citadel.device_gateway.device_discovery;

import jakarta.annotation.PostConstruct;
import org.jspecify.annotations.NonNull;
import org.jupnp.UpnpService;
import org.jupnp.UpnpServiceImpl;
import org.jupnp.model.message.header.STAllHeader;
import org.jupnp.model.meta.DeviceDetails;
import org.jupnp.model.meta.LocalDevice;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.meta.RemoteDeviceIdentity;
import org.jupnp.registry.Registry;
import org.jupnp.registry.RegistryListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.Objects;

@Component
public class UpnpServiceDiscovery implements DeviceDiscovery {
    private static final Logger logger = LoggerFactory.getLogger(UpnpServiceDiscovery.class);
    /** Fallback expiry when a UPnP advertisement omits CACHE-CONTROL max-age. */
    @Value("${device-gateway.upnp-device-ttl:1800}")
    private int DEFAULT_TTL_SECONDS;
    private final UpnpService upnpService;
    private RegistryListener registryListener;
    private final DeviceRegistry deviceRegistry;
    private final DeviceNameStore nameStore;

    public UpnpServiceDiscovery(DeviceRegistry deviceRegistry, DeviceNameStore nameStore) {
        upnpService = new UpnpServiceImpl(new JettyUpnpServiceConfiguration());
        registryListener = configureRegistryListener();
        this.deviceRegistry = deviceRegistry;
        this.nameStore = nameStore;
    }

    @Override
    @PostConstruct
    public void start() {
        logger.info("Starting UPnP service discovery...");
        upnpService.startup();
        upnpService.getRegistry().addListener(registryListener);
        upnpService.getControlPoint().search(new STAllHeader());
    }

    private RegistryListener configureRegistryListener() {
        registryListener = new RegistryListener() {
            @Override
            public void remoteDeviceDiscoveryStarted(Registry registry, RemoteDevice device) {
                logger.debug("Upnp device discovery started: {}", device.getDisplayString());
            }

            @Override
            public void remoteDeviceDiscoveryFailed(Registry registry, RemoteDevice device, Exception ex) {
                logger.error("Upnp device discovery failed: {} - {}", device.getDisplayString(), ex.getMessage());
            }

            @Override
            public void remoteDeviceAdded(Registry registry, RemoteDevice found_device) {
                try {
                    Device parsedDevice = parseDeviceInfo(found_device);
                    logger.debug("Upnp device metadata hydrated...adding to registry: {}, {}", parsedDevice.deviceName(), parsedDevice.hostName());
                    deviceRegistry.addDevice(parsedDevice);
                } catch (RuntimeException e) {
                    logger.error("Failed to parse UPnP device {}, skipping: {}", found_device.getDisplayString(), e.toString());
                }
            }

            @Override
            public void remoteDeviceUpdated(Registry registry, RemoteDevice device) {
                try {
                    Device parsedDevice = parseDeviceInfo(device);
                    logger.debug("Upnp device metadata hydrated...updating registry: {}, {}", parsedDevice.deviceName(), parsedDevice.hostName());
                    deviceRegistry.updateDevice(parsedDevice);
                } catch (RuntimeException e) {
                    logger.warn("Failed to parse UPnP device {}, skipping: {}", device.getDisplayString(), e.toString());
                }
            }

            @Override
            public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
                // One UPnP root device leaving does not mean the physical device is gone (it may
                // still advertise other root devices / mDNS services that share the host). Eviction
                // is left to the registry's TTL sweeper.
                logger.debug("Upnp device removed: {}", device.getDisplayString());
            }

            @Override
            public void localDeviceAdded(Registry registry, LocalDevice device) {
                logger.debug("localDeviceAdded not supported: {}", device.getDisplayString());
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            public void localDeviceRemoved(Registry registry, LocalDevice device) {
                logger.debug("localDeviceRemoved not supported: {}", device.getDisplayString());
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            public void beforeShutdown(Registry registry) {
                logger.debug("Upnp service discovery shutting down...");
                deviceRegistry.clear();
            }

            @Override
            public void afterShutdown() {
                logger.debug("Upnp service discovery shutdown complete.");
            }
        };
        return registryListener;
    }

    private @NonNull Device parseDeviceInfo(@NonNull RemoteDevice device) {
        // The host is the only field we genuinely cannot do without — it keys the registry, so a
        // device that lacks it is unusable and is rejected. Everything else falls back so devices
        // with sparse metadata (routers, NAS boxes, etc.) are still registered.
        RemoteDeviceIdentity connectionInfo = device.getIdentity();
        URL descriptorUrl = Objects.requireNonNull(connectionInfo.getDescriptorURL(), "UPnP device has no descriptor URL");
        String hostName = Objects.requireNonNull(descriptorUrl.getHost(), "UPnP device has no host");

        DeviceDetails details = device.getDetails();
        String friendlyName = details != null ? details.getFriendlyName() : null;
        String discoveredName = friendlyName != null ? friendlyName : hostName;
        // Apply any manual override before classifying, so a corrected name drives the derived
        // manufacturer and type as well as the display name.
        String deviceName = nameStore.resolve(discoveredName);
        Manufacturer manufacturer = Manufacturer.fromName(deviceName);
        int port = descriptorUrl.getPort();
        Integer maxAge = connectionInfo.getMaxAgeSeconds();
        int ttl = maxAge != null ? maxAge : DEFAULT_TTL_SECONDS;
        DeviceType deviceType = DeviceType.fromName(deviceName);
        return new Device(hostName, deviceName, manufacturer, port, ttl, deviceType);
    }
}