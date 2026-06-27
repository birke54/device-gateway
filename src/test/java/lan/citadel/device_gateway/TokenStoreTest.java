package lan.citadel.device_gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TokenStoreTest {

    @TempDir
    Path tempDir;

    private TokenStore storeAt(Path file) {
        return new TokenStore(file.toString());
    }

    @Test
    void getReturnsEmptyForUnknownHost() {
        TokenStore store = storeAt(tempDir.resolve("tokens.properties"));

        assertThat(store.get("10.0.0.5")).isEmpty();
    }

    @Test
    void putThenGetReturnsTheToken() {
        TokenStore store = storeAt(tempDir.resolve("tokens.properties"));

        store.put("10.0.0.5", "abc123");

        assertThat(store.get("10.0.0.5")).contains("abc123");
    }

    @Test
    void putPersistsTokensAcrossInstances() {
        Path file = tempDir.resolve("tokens.properties");
        storeAt(file).put("10.0.0.5", "abc123");

        TokenStore reloaded = storeAt(file);
        reloaded.load();

        assertThat(reloaded.get("10.0.0.5")).contains("abc123");
    }

    @Test
    void putWritesTheBackingFile() {
        Path file = tempDir.resolve("tokens.properties");

        storeAt(file).put("10.0.0.5", "abc123");

        assertThat(Files.exists(file)).isTrue();
    }

    @Test
    void loadIsANoOpWhenFileMissing() {
        TokenStore store = storeAt(tempDir.resolve("does-not-exist.properties"));

        store.load(); // must not throw

        assertThat(store.get("10.0.0.5")).isEmpty();
    }
}
