package lan.citadel.device_gateway.control;

import java.util.List;

/** A device that hosts launchable applications. */
public interface AppLauncher {
    List<App> retrieveApps();

    void openApp(String appName);
}
