package lan.citadel.device_gateway.device_discovery;

import jakarta.annotation.PostConstruct;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.Optional;
import java.util.Properties;

/**
 * A human-editable map of discovered device name to manual override, backed by a properties file.
 * Each key is a {@code deviceName} as discovered on the network; set its value to rename that device
 * everywhere the gateway presents it.
 * <p>
 * The file is self-documenting: whenever a device is discovered, its name is recorded here with a
 * blank value, so the file accumulates the list of names you can override. Editing a value takes
 * effect live — the file's modification time is checked on each read and reloaded if it changed, so
 * no restart is needed. The location is configurable via {@code device-gateway.device-names.path}.
 * <p>
 * Because overrides are keyed by name, two physical devices that advertise the same discovered name
 * resolve to the same override.
 */
@Component
public class DeviceNameStore {
    private static final Logger logger = LoggerFactory.getLogger(DeviceNameStore.class);

    private final Path file;

    /** Discovered name -> override (empty string means "no override; use the discovered name"). */
    private Properties names = new Properties();
    private FileTime loadedModifiedTime;
    private long loadedSize = -1;

    public DeviceNameStore(
            @Value("${device-gateway.device-names.path}") String path) {
        this.file = Path.of(path);
    }

    @PostConstruct
    synchronized void load() {
        if (!Files.exists(file)) {
            return;
        }
        try (InputStream in = Files.newInputStream(file)) {
            Properties loaded = new Properties();
            loaded.load(in);
            names = loaded;
            loadedModifiedTime = Files.getLastModifiedTime(file);
            loadedSize = Files.size(file);
            logger.info("Loaded {} device name(s) from {}", names.size(), file);
        } catch (IOException e) {
            logger.warn("Failed to load device names from {}: {}", file, e.toString());
        }
    }

    /**
     * The manual override for a discovered name, if one has been entered. Returns empty when the name
     * is unknown or its value is left blank.
     */
    public Optional<String> override(@NonNull String discoveredName) {
        reloadIfChanged();
        String override;
        synchronized (this) {
            override = names.getProperty(discoveredName);
        }
        if (override == null || override.isBlank() || override.equals(discoveredName)) {
            return Optional.empty();
        }
        return Optional.of(override.trim());
    }

    /**
     * Records a discovered name and returns the name to use in its place: the manual override if one
     * has been entered, otherwise the discovered name unchanged. This is the single entry point for
     * the discovery layer — calling it before classification lets an override drive a device's
     * derived manufacturer and type, not just its display name.
     */
    public String resolve(@NonNull String discoveredName) {
        recordDiscovered(discoveredName);
        return override(discoveredName).orElse(discoveredName);
    }

    /**
     * Records a discovered name so it shows up in the file for the user to override. New names are
     * added with a blank value; existing entries (including any override the user typed) are left
     * untouched.
     */
    public void recordDiscovered(@NonNull String discoveredName) {
        reloadIfChanged();
        synchronized (this) {
            if (names.containsKey(discoveredName)) {
                return;
            }
            names.setProperty(discoveredName, "");
        }
        persist();
    }

    /**
     * Reloads from disk when the file has been edited since we last read it, so overrides go live.
     * Both modification time and size are checked: size catches edits made within the same clock
     * tick as our last write on coarse-grained (one-second) filesystems.
     */
    private synchronized void reloadIfChanged() {
        try {
            FileTime current = Files.getLastModifiedTime(file);
            if (!current.equals(loadedModifiedTime) || Files.size(file) != loadedSize) {
                load();
            }
        } catch (NoSuchFileException e) {
            // File not created yet (or was removed); nothing to reload.
        } catch (IOException e) {
            logger.warn("Failed to check device name file {}: {}", file, e.toString());
        }
    }

    private synchronized void persist() {
        Path parent = file.toAbsolutePath().getParent();
        Path tmp = null;
        try {
            Files.createDirectories(parent);

            // Write to a sibling temp file first, then atomically swap it into place so a crash
            // mid-write can never leave the live file truncated or partially written.
            tmp = Files.createTempFile(parent, ".device-names", ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                names.store(out, "Discovered device names. Set a value to rename that device.");
            }
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                // Filesystem can't do an atomic rename; fall back to a best-effort replace.
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            tmp = null;
            loadedModifiedTime = Files.getLastModifiedTime(file);
            loadedSize = Files.size(file);
        } catch (IOException e) {
            logger.warn("Failed to persist device names to {}: {}", file, e.toString());
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException e) {
                    logger.warn("Failed to clean up temp device name file {}: {}", tmp, e.toString());
                }
            }
        }
    }
}
