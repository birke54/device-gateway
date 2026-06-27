package lan.citadel.device_gateway;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
 * location is configurable via {@code device-gateway.token-store.path}.
 */
@Component
public class TokenStore {
    private static final Logger logger = LoggerFactory.getLogger(TokenStore.class);

    private final Map<String, String> tokensByHost = new ConcurrentHashMap<>();
    private final Path file;

    public TokenStore(
            @Value("${device-gateway.token-store.path}") String path) {
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
        Path parent = file.toAbsolutePath().getParent();
        Path tmp = null;
        try {
            Files.createDirectories(parent);
            Properties props = new Properties();
            tokensByHost.forEach(props::setProperty);

            // Write to a sibling temp file first, then atomically swap it into place so a crash
            // mid-write can never leave the live file truncated or partially written.
            tmp = Files.createTempFile(parent, ".tv-tokens", ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                props.store(out, "Device pairing tokens");
            }
            restrictToOwner(tmp);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                // Filesystem can't do an atomic rename; fall back to a best-effort replace.
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            tmp = null;
        } catch (IOException e) {
            logger.warn("Failed to persist TV tokens to {}: {}", file, e.toString());
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException e) {
                    logger.warn("Failed to clean up temp token file {}: {}", tmp, e.toString());
                }
            }
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
