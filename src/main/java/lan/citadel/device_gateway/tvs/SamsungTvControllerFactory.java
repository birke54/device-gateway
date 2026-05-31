package lan.citadel.device_gateway.tvs;

import lan.citadel.device_gateway.control.DeviceController;
import lan.citadel.device_gateway.control.DeviceControllerFactory;
import lan.citadel.device_gateway.device_discovery.DeviceType;
import lan.citadel.device_gateway.device_discovery.LogicalDevice;
import lan.citadel.device_gateway.device_discovery.Manufacturer;
import org.springframework.stereotype.Component;

/** Builds {@link SamsungTelevision} controllers for Samsung TVs. */
@Component
public class SamsungTvControllerFactory implements DeviceControllerFactory {

    private final TokenStore tokenStore;

    public SamsungTvControllerFactory(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Override
    public boolean supports(LogicalDevice device) {
        return device.deviceType() == DeviceType.TV && device.manufacturer() == Manufacturer.SAMSUNG;
    }

    @Override
    public DeviceController create(LogicalDevice device) {
        return new SamsungTelevision(device.host(), tokenStore);
    }
}
