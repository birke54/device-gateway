package lan.citadel.device_gateway.control;

import lan.citadel.device_gateway.device_discovery.LogicalDevice;

/**
 * Builds a {@link DeviceController} for the device families it recognises. Implementations are
 * Spring components collected by {@link DeviceControllerRegistry}; adding support for a new device
 * type/manufacturer means dropping in another factory, with no changes elsewhere.
 */
public interface DeviceControllerFactory {
    /** Whether this factory can build a controller for the given device. */
    boolean supports(LogicalDevice device);

    /** Builds a controller for the device. Only called when {@link #supports(LogicalDevice)} is true. */
    DeviceController create(LogicalDevice device);
}
