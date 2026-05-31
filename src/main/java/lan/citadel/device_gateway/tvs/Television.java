package lan.citadel.device_gateway.tvs;

import lan.citadel.device_gateway.control.AppLauncher;
import lan.citadel.device_gateway.control.DeviceController;
import lan.citadel.device_gateway.control.KeyInput;

/**
 * A television: a controllable device that accepts remote keys and launches apps. Defined purely as
 * a composition of capability interfaces so non-TV devices can reuse the same building blocks.
 */
public interface Television extends DeviceController, KeyInput, AppLauncher {
}
