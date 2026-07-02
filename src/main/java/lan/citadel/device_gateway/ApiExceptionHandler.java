package lan.citadel.device_gateway;

import lan.citadel.device_gateway.exceptions.AppLaunchException;
import lan.citadel.device_gateway.exceptions.DeviceNotFoundException;
import lan.citadel.device_gateway.exceptions.DeviceNotTelevisionException;
import lan.citadel.device_gateway.exceptions.NoActiveSessionException;
import lan.citadel.device_gateway.exceptions.TvConnectionException;
import lan.citadel.device_gateway.exceptions.UnsupportedKeyException;
import lan.citadel.device_gateway.exceptions.UnsupportedOperationException;
import lan.citadel.device_gateway.exceptions.UnsupportedTvException;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DeviceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(DeviceNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(DeviceNotTelevisionException.class)
    public ResponseEntity<Map<String, String>> handleNotTelevision(DeviceNotTelevisionException e) {
        return error(HttpStatus.BAD_REQUEST, e);
    }

    @ExceptionHandler(UnsupportedTvException.class)
    public ResponseEntity<Map<String, String>> handleUnsupported(UnsupportedTvException e) {
        return error(HttpStatus.NOT_IMPLEMENTED, e);
    }

    @ExceptionHandler(TvConnectionException.class)
    public ResponseEntity<Map<String, String>> handleConnectionFailed(TvConnectionException e) {
        return error(HttpStatus.REQUEST_TIMEOUT, e);
    }

    @ExceptionHandler(NoActiveSessionException.class)
    public ResponseEntity<Map<String, String>> handleNoSession(NoActiveSessionException e) {
        return error(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(UnsupportedKeyException.class)
    public ResponseEntity<Map<String, String>> handleUnsupportedKey(UnsupportedKeyException e) {
        return error(HttpStatus.UNPROCESSABLE_CONTENT, e);
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<Map<String, String>> handleUnsupportedOperation(UnsupportedOperationException e) {
        return error(HttpStatus.NOT_IMPLEMENTED, e);
    }

    @ExceptionHandler(AppLaunchException.class)
    public ResponseEntity<Map<String, String>> handleAppLaunchFailed(AppLaunchException e) {
        return error(HttpStatus.BAD_GATEWAY, e);
    }

    private static @NonNull ResponseEntity<Map<String, String>> error(HttpStatus status, @NonNull RuntimeException e) {
        return ResponseEntity.status(status).body(Map.of("error", e.getMessage()));
    }
}
