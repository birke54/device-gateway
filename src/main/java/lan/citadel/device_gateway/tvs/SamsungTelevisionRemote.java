package lan.citadel.device_gateway.tvs;

import lan.citadel.device_gateway.TokenStore;
import lan.citadel.device_gateway.control.App;
import lan.citadel.device_gateway.control.RemoteKey;
import lan.citadel.device_gateway.exceptions.AppLaunchException;
import lan.citadel.device_gateway.exceptions.UnsupportedKeyException;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Drives a Samsung (Tizen) television over its WebSocket remote-control channel.
 * <p>
 * 2018+ sets only expose the secure endpoint {@code wss://<host>:8002/...} with a self-signed
 * certificate, so TLS verification is disabled for the LAN connection. The first connection raises
 * an on-screen Allow/Deny prompt and, once allowed, the TV returns a token in the
 * {@code ms.channel.connect} event; that token is cached via {@link TokenStore} and replayed on
 * later connects to skip the prompt.
 */
public class SamsungTelevisionRemote implements Television, PersistentConnection {
    private static final Logger logger = LoggerFactory.getLogger(SamsungTelevisionRemote.class);

    private static final int SECURE_PORT = 8002;
    /** Plain-HTTP control/info API, used to probe whether a given app is installed. */
    private static final int REST_PORT = 8001;
    private static final String CONTROLLER_NAME = "device-gateway";
    /** Generous, because on an unpaired TV this waits for the user to accept the on-screen prompt. */
    private static final long HANDSHAKE_TIMEOUT_SECONDS = 30;
    private static final long APP_FETCH_TIMEOUT_SECONDS = 2;
    private static final long REST_TIMEOUT_SECONDS = 2;

    /**
     * Curated set of well-known Samsung Tizen application IDs, probed over the REST API as a fallback
     * for sets whose firmware ignores the {@code ed.installedApp.get} websocket query. Unknown IDs are
     * simply skipped, and each app's display name comes from the TV's own response rather than this
     * list, so the catalogue is only a best-effort set of candidates and is safe to extend.
     */
    private static final List<String> KNOWN_APP_IDS = List.of(
            "3201907018807", // Netflix
            "111299001912",  // YouTube
            "3201707014489", // YouTube TV
            "3201910019365", // Prime Video
            "3201901017640", // Disney+
            "3201601007625", // Hulu
            "3201601007230", // Max (HBO)
            "3201606009684", // Spotify
            "3201807016597", // Apple TV
            "3201908019041", // Apple Music
            "3201512006963", // Plex
            "3201506003488", // Pandora
            "111299000288"   // Peacock TV
    );

    /** Maps the canonical remote keys this TV supports to Samsung's {@code KEY_*} wire codes. */
    private static final Map<RemoteKey, String> KEY_CODES = new EnumMap<>(Map.ofEntries(
            Map.entry(RemoteKey.POWER,        "KEY_POWER"),
            Map.entry(RemoteKey.HOME,         "KEY_HOME"),
            Map.entry(RemoteKey.BACK,         "KEY_RETURN"),
            Map.entry(RemoteKey.MENU,         "KEY_MENU"),
            Map.entry(RemoteKey.UP,           "KEY_UP"),
            Map.entry(RemoteKey.DOWN,         "KEY_DOWN"),
            Map.entry(RemoteKey.LEFT,         "KEY_LEFT"),
            Map.entry(RemoteKey.RIGHT,        "KEY_RIGHT"),
            Map.entry(RemoteKey.OK,           "KEY_ENTER"),
            Map.entry(RemoteKey.VOLUME_UP,    "KEY_VOLUP"),
            Map.entry(RemoteKey.VOLUME_DOWN,  "KEY_VOLDOWN"),
            Map.entry(RemoteKey.MUTE,         "KEY_MUTE"),
            Map.entry(RemoteKey.CHANNEL_UP,   "KEY_CHUP"),
            Map.entry(RemoteKey.CHANNEL_DOWN, "KEY_CHDOWN"),
            Map.entry(RemoteKey.PLAY,         "KEY_PLAY"),
            Map.entry(RemoteKey.PAUSE,        "KEY_PAUSE"),
            Map.entry(RemoteKey.STOP,         "KEY_STOP"),
            Map.entry(RemoteKey.REWIND,       "KEY_REWIND"),
            Map.entry(RemoteKey.FAST_FORWARD, "KEY_FF"),
            Map.entry(RemoteKey.INPUT_SOURCE, "KEY_SOURCE")
    ));

    private final String host;
    private final TokenStore tokenStore;
    private final ObjectMapper mapper = new ObjectMapper();
    // Pinned to HTTP/1.1: the default client negotiates HTTP/2, whose cleartext h2c upgrade the TV's
    // minimal HTTP server rejects by dropping the connection ("header parser received no bytes").
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    /** Installed-app list, fetched lazily once on first {@link #retrieveApps()} and cached thereafter. */
    private volatile JsonNode installedApps;
    /** Set while a fetch is awaiting the TV's async ed.installedApp.get reply; counted down on arrival. */
    private volatile CountDownLatch appsLatch;
    /** The resolved app list (websocket result, or REST-probe fallback), cached after the first lookup. */
    private volatile List<App> appCache;
    private RemoteSocket socket;

    public SamsungTelevisionRemote(String host, TokenStore tokenStore) {
        this.host = host;
        this.tokenStore = tokenStore;
    }

    @Override
    public String host() {
        return host;
    }

    @Override
    public Boolean connect() {
        Optional<String> token = tokenStore.get(host);
        try {
            RemoteSocket newSocket = new RemoteSocket(buildUri(token.orElse(null)));
            newSocket.setSocketFactory(trustAllSocketFactory());
            this.socket = newSocket;

            if (!newSocket.connectBlocking(HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                logger.warn("Samsung TV {} did not complete the WebSocket handshake in time", host);
                return false;
            }
            // The socket is open, but authorization only completes once ms.channel.connect arrives
            // (immediately when a token is known, or after the user accepts the on-screen prompt).
            if (!newSocket.awaitAuthorization()) {
                logger.warn("Samsung TV {} was not authorized within {}s", host, HANDSHAKE_TIMEOUT_SECONDS);
                return false;
            }
            return newSocket.isAuthorized();
        } catch (GeneralSecurityException e) {
            logger.warn("Failed to set up TLS for Samsung TV {}: {}", host, e.toString());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while connecting to Samsung TV {}", host);
            return false;
        }
    }

    @Override
    public void disconnect() {
        if (socket != null) {
            socket.close();
            socket = null;
        }
    }

    @Override
    public void sendKey(RemoteKey key) {
        String code = KEY_CODES.get(key);
        if (code == null) {
            throw new UnsupportedKeyException(key);
        }
        ObjectNode params = mapper.createObjectNode();
        params.put("Cmd", "Click");
        params.put("DataOfCmd", code);
        params.put("Option", "false");
        params.put("TypeOfRemote", "SendRemoteKey");
        send("ms.remote.control", params);
    }

    @Override
    public Set<RemoteKey> supportedKeys() {
        return KEY_CODES.keySet();
    }

    @Override
    public synchronized List<App> retrieveApps() {
        if (appCache != null) {
            return appCache;
        }
        // Newer Tizen firmware silently ignores ed.installedApp.get, so fall back to probing the
        // REST API for the curated set of well-known apps.
        List<App> installed = new ArrayList<>();
        for (String appId : KNOWN_APP_IDS) {
            probeApp(appId).ifPresent(installed::add);
        }
        logger.debug("REST probe found {} installed apps on Samsung TV {}", installed.size(), host);
        return installed;
    }

    /**
     * Queries {@code http://<host>:8001/api/v2/applications/<appId>}; returns the app iff the TV
     * recognizes it (HTTP 200 with a name). A 404 or any error means "not installed / unavailable".
     */
    private Optional<App> probeApp(String appId) {
        HttpRequest request = HttpRequest.newBuilder(appUri(appId))
                .timeout(Duration.ofSeconds(REST_TIMEOUT_SECONDS))
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.debug("REST probe for app {} on Samsung TV {} returned HTTP {}",
                        appId, host, response.statusCode());
                return Optional.empty();
            }
            JsonNode body = mapper.readTree(response.body());
            String name = body.path("name").asString("");
            return name.isEmpty() ? Optional.empty() : Optional.of(new App(body.path("id").asString(appId), name));
        } catch (IOException e) {
            logger.debug("REST probe for app {} on Samsung TV {} failed: {}", appId, host, e.toString());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /**
     * Launches an app via the REST API ({@code POST .../applications/<appId>}). Newer Tizen firmware
     * silently ignores the {@code ed.apps.launch} websocket event, whereas the REST call returns a
     * status we can act on.
     */
    @Override
    public void openApp(String appName) {
        String appId = resolveAppId(appName);
        HttpRequest request = HttpRequest.newBuilder(appUri(appId))
                .timeout(Duration.ofSeconds(REST_TIMEOUT_SECONDS))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new AppLaunchException(appName, "TV returned HTTP " + response.statusCode());
            }
            logger.info("Launched app {} ({}) on Samsung TV {}", appName, appId, host);
        } catch (IOException e) {
            throw new AppLaunchException(appName, e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppLaunchException(appName, "interrupted");
        }
    }

    /** REST endpoint for a single app on this TV: {@code http://<host>:8001/api/v2/applications/<appId>}. */
    private @NonNull URI appUri(String appId) {
        return URI.create("http://" + host + ':' + REST_PORT + "/api/v2/applications/" + appId);
    }

    /** Sends a top-level control message ({@code method} + {@code params}). */
    private void send(String method, ObjectNode params) {
        ObjectNode message = mapper.createObjectNode();
        message.put("method", method);
        message.set("params", params);
        sendRaw(message);
    }

    private void sendRaw(ObjectNode message) {
        RemoteSocket current = socket;
        if (current == null || !current.isOpen()) {
            throw new IllegalStateException("Not connected to Samsung TV " + host);
        }
        current.send(message.toString());
    }

    private String resolveAppId(String appName) {
        for (App app : retrieveApps()) {
            if (appName.equalsIgnoreCase(app.name())) {
                return app.appId();
            }
        }
        // Fall back to treating the argument as an app id (caller may already know it).
        return appName;
    }

    private @NonNull URI buildUri(String token) {
        String encodedName = Base64.getEncoder().encodeToString(CONTROLLER_NAME.getBytes(StandardCharsets.UTF_8));
        StringBuilder url = new StringBuilder("wss://").append(host).append(':').append(SECURE_PORT)
                .append("/api/v2/channels/samsung.remote.control?name=").append(encodedName);
        if (token != null && !token.isEmpty()) {
            url.append("&token=").append(token);
        }
        return URI.create(url.toString());
    }

    /** Samsung TVs present self-signed certificates, so the LAN connection trusts any certificate. */
    private static SSLSocketFactory trustAllSocketFactory() throws GeneralSecurityException {
        TrustManager[] trustAll = {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustAll, new SecureRandom());
        return context.getSocketFactory();
    }

    /** The live WebSocket connection; captures the authorization handshake and async responses. */
    private final class RemoteSocket extends WebSocketClient {
        private final CountDownLatch authLatch = new CountDownLatch(1);
        private volatile boolean authorized;

        private RemoteSocket(URI serverUri) {
            super(serverUri);
        }

        boolean awaitAuthorization() throws InterruptedException {
            return authLatch.await(SamsungTelevisionRemote.HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        boolean isAuthorized() {
            return authorized;
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            logger.debug("WebSocket opened to Samsung TV {}", host);
        }

        /**
         * Samsung TVs present a self-signed certificate that does not list the set's LAN IP in its
         * subject alternative names, so we leave the endpoint identification algorithm unset to skip
         * hostname verification. The trust-all socket factory already covers certificate trust.
         */
        @Override
        protected void onSetSSLParameters(@NonNull SSLParameters sslParameters) {
            sslParameters.setEndpointIdentificationAlgorithm(null);
        }

        @Override
        public void onMessage(String message) {
            try {
                JsonNode node = mapper.readTree(message);
                String event = node.path("event").asString("");
                switch (event) {
                    case "ms.channel.connect" -> {
                        String token = node.path("data").path("token").asString("");
                        if (!token.isEmpty()) {
                            tokenStore.put(host, token);
                        }
                        authorized = true;
                        logger.info("Samsung TV {} authorized", host);
                        authLatch.countDown();
                    }
                    case "ms.channel.unauthorized" -> {
                        logger.warn("Samsung TV {} denied authorization", host);
                        authorized = false;
                        authLatch.countDown();
                    }
                    case "ed.installedApp.get" -> {
                        installedApps = node.path("data").path("data");
                        logger.debug("Received installed-app list from Samsung TV {}", host);
                        CountDownLatch latch = appsLatch;
                        if (latch != null) {
                            latch.countDown();
                        }
                    }
                    default -> logger.trace("Unhandled message from Samsung TV {}: {}", host, message);
                }
            } catch (Exception e) {
                logger.warn("Failed to parse message from Samsung TV {}: {}", host, e.toString());
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            logger.debug("WebSocket to Samsung TV {} closed ({}): {}", host, code, reason);
            // Release connect() if the TV closed the socket before authorizing.
            authLatch.countDown();
        }

        @Override
        public void onError(@NonNull Exception ex) {
            logger.warn("WebSocket error for Samsung TV {}: {}", host, ex.toString());
        }
    }
}
