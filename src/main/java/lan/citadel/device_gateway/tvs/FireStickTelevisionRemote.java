package lan.citadel.device_gateway.tvs;

import dadb.AdbKeyPair;
import dadb.AdbShellResponse;
import dadb.Dadb;
import lan.citadel.device_gateway.control.App;
import lan.citadel.device_gateway.control.RemoteKey;
import lan.citadel.device_gateway.exceptions.AppLaunchException;
import lan.citadel.device_gateway.exceptions.UnsupportedKeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class FireStickTelevisionRemote implements Television, PersistentConnection {
    public static final Logger logger = LoggerFactory.getLogger(FireStickTelevisionRemote.class);
    // DNS or Ip address of the FireStick
    private final String host;
    private static final int PORT = 5555;
    private static final int HANDSHAKE_TIMEOUT_MS = 30_000;

    private final GenerateAdbKeyPair adbKeyPair;

    private Dadb dadb;
    private List<App> appCache;

    public FireStickTelevisionRemote(String host, GenerateAdbKeyPair adbKeyPair) {
        this.host = host;
        this.adbKeyPair = adbKeyPair;
    }

    /** Maps the canonical remote keys this TV supports to Android {@code keyevent} codes. */
    private static final Map<RemoteKey, String> KEY_CODES = new EnumMap<>(Map.ofEntries(
            Map.entry(RemoteKey.POWER,        "26"),
            Map.entry(RemoteKey.HOME,         "3"),
            Map.entry(RemoteKey.BACK,         "4"),
            Map.entry(RemoteKey.MENU,         "82"),
            Map.entry(RemoteKey.UP,           "19"),
            Map.entry(RemoteKey.DOWN,         "20"),
            Map.entry(RemoteKey.LEFT,         "21"),
            Map.entry(RemoteKey.RIGHT,        "22"),
            Map.entry(RemoteKey.OK,           "66"),
            Map.entry(RemoteKey.VOLUME_UP,    "24"),
            Map.entry(RemoteKey.VOLUME_DOWN,  "25"),
            Map.entry(RemoteKey.MUTE,         "164"),
            Map.entry(RemoteKey.CHANNEL_UP,   "166"),
            Map.entry(RemoteKey.CHANNEL_DOWN, "167"),
            Map.entry(RemoteKey.PLAY,         "126"),
            Map.entry(RemoteKey.PAUSE,        "127"),
            Map.entry(RemoteKey.STOP,         "86"),
            Map.entry(RemoteKey.REWIND,       "89"),
            Map.entry(RemoteKey.FAST_FORWARD, "90"),
            Map.entry(RemoteKey.INPUT_SOURCE, "178")
    ));

    private static final Map<String, String> PACKAGE_TO_APP_NAMES = Map.ofEntries(
            Map.entry("com.amazon.avod.thirdpartyclient", "Amazon Video"),
            Map.entry("com.amazon.mp3",                   "Amazon Music"),
            Map.entry("com.netflix.ninja",                "Netflix"),
            Map.entry("com.disney.disneyplus",            "Disney+"),
            Map.entry("com.hulu.plus",                    "Hulu"),
            Map.entry("com.hbo.hbonow",                   "HBO Max"),
            Map.entry("com.peacocktv.peacockandroid",     "Peacock TV"),
            Map.entry("com.cbs.ca",                       "Paramount+"),
            Map.entry("com.apple.atve.amazon.appletv",    "Apple TV"),
            Map.entry("com.google.android.youtube.tv",    "Youtube"),
            Map.entry("com.google.android.youtube.tvunplugged", "Youtube TV"),
            Map.entry("com.plexapp.android",              "Plex")
    );

    private static final Map<String, String> APP_NAME_TO_PACKAGE =
            PACKAGE_TO_APP_NAMES.entrySet().stream()
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getValue, Map.Entry::getKey));

    @Override
    public List<App> retrieveApps() {
        if (dadb == null) {
            throw new IllegalStateException("Not connected to FireStick");
        }
        if (appCache != null) {
            return appCache;
        }

        try {
            String apps = dadb.shell("pm list packages").getOutput();
            appCache = Arrays.stream(apps.split("\n"))
                    .map(line -> line.replace("package:", "").trim())
                    .filter(line -> !line.isEmpty())
                    .map(pkg -> new App(pkg, PACKAGE_TO_APP_NAMES.get(pkg)))
                    .filter(app -> app.name() != null)
                    .collect(Collectors.toList());
            return appCache;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void openApp(String appName) {
        if (dadb == null) {
            throw new IllegalStateException("Not connected to FireStick");
        }

        String pkg = APP_NAME_TO_PACKAGE.get(appName);
        if (pkg == null) {
            throw new AppLaunchException(appName, "no known package for this app");
        }

        try {
            // monkey launches the package's main activity; a missing/unlaunchable package aborts
            // monkey with a non-zero exit code rather than failing the shell call itself.
            AdbShellResponse response = dadb.shell("monkey -p " + pkg + " 1");
            if (response.getExitCode() != 0) {
                throw new AppLaunchException(appName, "monkey exited with " + response.getExitCode()
                        + ": " + response.getAllOutput().trim());
            }
            logger.info("Launched app {} ({}) on FireStick {}", appName, pkg, host());
        } catch (IOException e) {
            throw new AppLaunchException(appName, e.toString());
        }
    }

    @Override
    public String host() {
        return host;
    }

    @Override
    public void sendKey(RemoteKey key) {
        if (dadb == null) {
            throw new IllegalStateException("Not connected to FireStick");
        }

        String code = KEY_CODES.get(key);
        if (code == null) {
            throw new UnsupportedKeyException(key);
        }

        try {
            dadb.shell(String.format("input keyevent %s", code));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Set<RemoteKey> supportedKeys() {
        return KEY_CODES.keySet();
    }

    @Override
    public Boolean connect() {
        AdbKeyPair keyPair;
        // Retrieve or generate a new Adb key pair
        try {
            keyPair = adbKeyPair.loadOrCreateKeyPair();
        } catch (Exception e) {
            logger.error("Failed to generate ADB key pair: {}", e.toString());
            return false;
        }

        // Connect to the FireStick
        try {
            dadb = Dadb.create(host(), PORT, keyPair, HANDSHAKE_TIMEOUT_MS);
            logger.info("Connected to FireStick at {}", host());
            return true;
        } catch (Exception e) {
            logger.error("Failed to connect to FireStick at {}: {}", host(), e.toString());
            return false;
        }
    }

    @Override
    public void disconnect() {
        Dadb current = dadb;
        // Drop the reference up front so the connection can't dangle even if
        // close() throws or disconnect() is called again.
        dadb = null;
        appCache = null;
        if (current == null) {
            return;
        }
        try {
            current.close();
            logger.info("Disconnected from FireStick at {}", host());
        } catch (Exception e) {
            logger.warn("Failed to cleanly close FireStick connection at {}: {}", host(), e.toString());
        }
    }
}
