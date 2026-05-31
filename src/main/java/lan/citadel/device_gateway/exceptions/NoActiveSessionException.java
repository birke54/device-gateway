package lan.citadel.device_gateway.exceptions;

/** An operation needs an active television session, but none is connected yet. */
public class NoActiveSessionException extends RuntimeException {
    public NoActiveSessionException() {
        super("No active television session; connect to a device first");
    }
}
