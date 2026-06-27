package lan.citadel.device_gateway.tvs;

import dadb.AdbKeyPair;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Holds the ADB key pair used to authenticate against Fire TV devices over ADB. The pair is loaded
 * from disk if present, otherwise generated and persisted on first use so the on-device "allow
 * debugging" prompt only has to be accepted once. The location is configurable via
 * {@code device-gateway.adb-cert-store.adb_key_pair_path}; the public key lives alongside it with a
 * {@code .pub} suffix.
 */
@Component
public class GenerateAdbKeyPair {
    private static final Logger logger = LoggerFactory.getLogger(GenerateAdbKeyPair.class);

    private final Path privateKeyPath;
    private final Path publicKeyPath;

    public GenerateAdbKeyPair(
            @Value("${device-gateway.adb-cert-store.adb_key_pair_path}") String path) {
        this.privateKeyPath = Path.of(path);
        this.publicKeyPath = Path.of(path + ".pub");
    }

    public @NonNull AdbKeyPair loadOrCreateKeyPair() throws IOException {
        if (!Files.exists(privateKeyPath) || !Files.exists(publicKeyPath)) {
            // First run: generate and persist the pair.
            Files.createDirectories(privateKeyPath.getParent());
            AdbKeyPair.generate(privateKeyPath.toFile(), publicKeyPath.toFile());
            logger.info("Generated new ADB key pair at {}", privateKeyPath);
        }
        // Key pair already exists - reuse it.
        return AdbKeyPair.read(privateKeyPath.toFile(), publicKeyPath.toFile());
    }
}
