package lan.citadel.device_gateway.device_discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceNameStoreTest {

    @TempDir
    Path tempDir;

    private DeviceNameStore storeAt(Path file) {
        return new DeviceNameStore(file.toString());
    }

    @Test
    void overrideIsEmptyForUnknownName() {
        DeviceNameStore store = storeAt(tempDir.resolve("names.properties"));

        assertThat(store.override("Living Room TV")).isEmpty();
    }

    @Test
    void recordDiscoveredWritesTheFileWithABlankValue() throws IOException {
        Path file = tempDir.resolve("names.properties");
        DeviceNameStore store = storeAt(file);

        store.recordDiscovered("Living Room TV");

        assertThat(Files.exists(file)).isTrue();
        // No value yet, so no override.
        assertThat(store.override("Living Room TV")).isEmpty();
    }

    @Test
    void recordDiscoveredDoesNotClobberAnExistingOverride() {
        Path file = tempDir.resolve("names.properties");
        storeAt(file).recordDiscovered("Living Room TV");
        editValue(file, "Living\\ Room\\ TV", "Den");

        DeviceNameStore reloaded = storeAt(file);
        reloaded.load();
        reloaded.recordDiscovered("Living Room TV"); // seen again

        assertThat(reloaded.override("Living Room TV")).contains("Den");
    }

    @Test
    void editedValueIsPickedUpLiveWithoutReload() {
        Path file = tempDir.resolve("names.properties");
        DeviceNameStore store = storeAt(file);
        store.recordDiscovered("Living Room TV");

        assertThat(store.override("Living Room TV")).isEmpty();

        // Simulate a user editing the file out from under the running store.
        editValue(file, "Living\\ Room\\ TV", "Den");

        assertThat(store.override("Living Room TV")).contains("Den");
    }

    @Test
    void resolveRecordsTheNameAndReturnsItUnchangedWithoutAnOverride() {
        Path file = tempDir.resolve("names.properties");
        DeviceNameStore store = storeAt(file);

        assertThat(store.resolve("Bedroom box")).isEqualTo("Bedroom box");
        assertThat(Files.exists(file)).isTrue();
    }

    @Test
    void resolveReturnsTheOverrideSoItCanDriveClassification() {
        Path file = tempDir.resolve("names.properties");
        DeviceNameStore store = storeAt(file);
        // A device whose raw name classifies as UNKNOWN.
        store.recordDiscovered("Bedroom box");
        assertThat(Manufacturer.fromName(store.resolve("Bedroom box"))).isEqualTo(Manufacturer.UNKNOWN);

        // The user renames it to something the classifier recognises.
        editValue(file, "Bedroom\\ box", "Samsung Bedroom TV");

        String resolved = store.resolve("Bedroom box");
        assertThat(resolved).isEqualTo("Samsung Bedroom TV");
        assertThat(Manufacturer.fromName(resolved)).isEqualTo(Manufacturer.SAMSUNG);
    }

    @Test
    void blankAndIdentityValuesAreNotTreatedAsOverrides() {
        Path file = tempDir.resolve("names.properties");
        DeviceNameStore store = storeAt(file);
        store.recordDiscovered("Living Room TV");

        editValue(file, "Living\\ Room\\ TV", "Living Room TV"); // same as discovered

        assertThat(store.override("Living Room TV")).isEmpty();
    }

    /** Rewrites the backing properties file with a single key=value pair, bumping its mtime. */
    private void editValue(Path file, String escapedKey, String value) {
        try {
            // Sleep a hair so the modification time is guaranteed to differ on coarse-grained clocks.
            Thread.sleep(10);
            Files.writeString(file, escapedKey + "=" + value + "\n");
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
