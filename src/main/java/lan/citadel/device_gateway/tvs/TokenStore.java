package lan.citadel.device_gateway.tvs;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the pairing tokens Samsung TVs hand back on first authorization, keyed by host. Reusing the
 * token on later connects avoids re-triggering the on-screen Allow/Deny prompt.
 * <p>
 * Tokens are cached in memory and persisted to a properties file so they survive restarts. The
 * location is configurable via {@code device-gateway.token-store.path} and defaults to
 * {@code ~/.device-gateway/tv-tokens.properties}.
 */
@Component
public class TokenStore {
    private static final Logger logger = LoggerFactory.getLogger(TokenStore.class);

    private final Map<String, String> tokensByHost = new ConcurrentHashMap<>();
    private final Path file;

    public TokenStore(
            @Value("${device-gateway.token-store.path:${user.home}/.device-gateway/tv-tokens.properties}") String path) {
        this.file = Path.of(path);
    }

    @PostConstruct
    void load() {
        if (!Files.exists(file)) {
            return;
        }
        try (InputStream in = Files.newInputStream(file)) {
            Properties props = new Properties();
            props.load(in);
            props.forEach((host, token) -> tokensByHost.put((String) host, (String) token));
            logger.info("Loaded {} TV token(s) from {}", tokensByHost.size(), file);
        } catch (IOException e) {
            logger.warn("Failed to load TV tokens from {}: {}", file, e.toString());
        }
    }

    public Optional<String> get(String host) {
        return Optional.ofNullable(tokensByHost.get(host));
    }

    public void put(String host, String token) {
        String previous = tokensByHost.put(host, token);
        if (!token.equals(previous)) {
            persist();
        }
    }

    private synchronized void persist() {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Properties props = new Properties();
            tokensByHost.forEach(props::setProperty);
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "Samsung TV pairing tokens");
            }
            restrictToOwner(file);
        } catch (IOException e) {
            logger.warn("Failed to persist TV tokens to {}: {}", file, e.toString());
        }
    }

    /** Best-effort owner-only permissions; silently ignored on filesystems without POSIX support. */
    private void restrictToOwner(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (IOException | UnsupportedOperationException ignored) {
            // Non-POSIX filesystem (e.g. Windows); leave default permissions.
        }
    }
}
