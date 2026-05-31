package lan.citadel.device_gateway.exceptions;

/** A connection to the television could not be established. */
public class TvConnectionException extends RuntimeException {
    public TvConnectionException(String key) {
        super("Failed to connect to TV: " + key);
    }
}
