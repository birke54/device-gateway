package lan.citadel.device_gateway.exceptions;

/** The TV could not launch the requested app (rejected the request or was unreachable). */
public class AppLaunchException extends RuntimeException {
    public AppLaunchException(String appName, String reason) {
        super("Failed to launch app '" + appName + "': " + reason);
    }
}
