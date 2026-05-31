package lan.citadel.device_gateway;

import lan.citadel.device_gateway.control.RemoteKey;
import lan.citadel.device_gateway.device_discovery.Manufacturer;
import lan.citadel.device_gateway.exceptions.AppLaunchException;
import lan.citadel.device_gateway.exceptions.DeviceNotFoundException;
import lan.citadel.device_gateway.exceptions.DeviceNotTelevisionException;
import lan.citadel.device_gateway.exceptions.NoActiveSessionException;
import lan.citadel.device_gateway.exceptions.TvConnectionException;
import lan.citadel.device_gateway.exceptions.UnsupportedKeyException;
import lan.citadel.device_gateway.exceptions.UnsupportedTvException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsDeviceNotFoundToNotFound() {
        ResponseEntity<Map<String, String>> response = handler.handleNotFound(new DeviceNotFoundException("10.0.0.5"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsKey("error");
        assertThat(response.getBody().get("error")).contains("10.0.0.5");
    }

    @Test
    void mapsDeviceNotTelevisionToBadRequest() {
        assertThat(handler.handleNotTelevision(new DeviceNotTelevisionException("10.0.0.5")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void mapsUnsupportedTvToNotImplemented() {
        assertThat(handler.handleUnsupported(new UnsupportedTvException(Manufacturer.UNKNOWN)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_IMPLEMENTED);
    }

    @Test
    void mapsConnectionFailureToBadGateway() {
        assertThat(handler.handleConnectionFailed(new TvConnectionException("10.0.0.5")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void mapsNoActiveSessionToConflict() {
        assertThat(handler.handleNoSession(new NoActiveSessionException()).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void mapsUnsupportedKeyToUnprocessableContent() {
        assertThat(handler.handleUnsupportedKey(new UnsupportedKeyException(RemoteKey.POWER)).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void mapsAppLaunchFailureToBadGateway() {
        assertThat(handler.handleAppLaunchFailed(new AppLaunchException("Netflix", "unreachable")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }
}
