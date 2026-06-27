package lan.citadel.device_gateway.control;

import lan.citadel.device_gateway.device_discovery.DeviceType;
import lan.citadel.device_gateway.device_discovery.LogicalDevice;
import lan.citadel.device_gateway.exceptions.DeviceNotTelevisionException;
import lan.citadel.device_gateway.exceptions.UnsupportedTvException;
import lan.citadel.device_gateway.tvs.FireStickTelevisionRemote;
import lan.citadel.device_gateway.tvs.GenerateAdbKeyPair;
import lan.citadel.device_gateway.tvs.SamsungTelevisionRemote;
import lan.citadel.device_gateway.tvs.Television;
import lan.citadel.device_gateway.TokenStore;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

/**
 * Resolves a {@link Television} for a discovered Tv device by matching the manufacturer
 * of a {@link LogicalDevice}. Provides access to the persistent token store for existing connections
 * so we do not need to re-authorize on each reconnect.
 */
@Component
public class TvRemoteFactory {

    private final TokenStore tokenStore;
    private final GenerateAdbKeyPair adbKeyPair;

    public TvRemoteFactory(TokenStore tokenStore, GenerateAdbKeyPair adbKeyPair) {
        this.tokenStore = tokenStore;
        this.adbKeyPair = adbKeyPair;
    }

    public Television create(@NonNull LogicalDevice device) {
        if (device.deviceType() == DeviceType.TV) {
            switch (device.manufacturer()) {
                case SAMSUNG:
                    return new SamsungTelevisionRemote(device.host(), this.tokenStore);
                case AMAZON:
                    return new FireStickTelevisionRemote(device.host(), this.adbKeyPair);
                default:
                    throw new UnsupportedTvException(device.manufacturer());
            }
        } else {
            throw new DeviceNotTelevisionException(device);
        }
    }
}
