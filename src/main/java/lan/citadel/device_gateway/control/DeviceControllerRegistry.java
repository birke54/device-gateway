package lan.citadel.device_gateway.control;

import lan.citadel.device_gateway.device_discovery.LogicalDevice;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Resolves a {@link DeviceController} for a discovered device by delegating to the first registered
 * {@link DeviceControllerFactory} that supports it. Spring injects every factory bean, so new device
 * families become available simply by adding a factory component.
 */
@Component
public class DeviceControllerRegistry {

    private final List<DeviceControllerFactory> factories;

    public DeviceControllerRegistry(List<DeviceControllerFactory> factories) {
        this.factories = factories;
    }

    public Optional<DeviceController> create(LogicalDevice device) {
        return factories.stream()
                .filter(factory -> factory.supports(device))
                .findFirst()
                .map(factory -> factory.create(device));
    }
}
