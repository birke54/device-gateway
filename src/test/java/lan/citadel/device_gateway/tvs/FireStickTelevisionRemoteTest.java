package lan.citadel.device_gateway.tvs;

import dadb.AdbShellResponse;
import dadb.Dadb;
import lan.citadel.device_gateway.control.App;
import lan.citadel.device_gateway.control.RemoteKey;
import lan.citadel.device_gateway.exceptions.AppLaunchException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FireStickTelevisionRemoteTest {

    private static final String HOST = "10.0.0.5";

    private final Dadb dadb = mock(Dadb.class);
    private final GenerateAdbKeyPair keyPair = mock(GenerateAdbKeyPair.class);
    private final FireStickTelevisionRemote tv = new FireStickTelevisionRemote(HOST, keyPair);

    /** Drives the real connect() path with a mocked ADB layer so later calls run against {@link #dadb}. */
    private void connectTv() {
        try (MockedStatic<Dadb> dadbStatic = mockStatic(Dadb.class)) {
            dadbStatic.when(() -> Dadb.create(eq(HOST), anyInt(), any(), anyInt())).thenReturn(dadb);
            assertThat(tv.connect()).isTrue();
        }
    }

    private static AdbShellResponse shellResponse(String output, int exitCode) {
        return new AdbShellResponse(output, "", exitCode);
    }

    @Test
    void exposesItsHost() {
        assertThat(tv.host()).isEqualTo(HOST);
    }

    @Test
    void supportsEveryCanonicalRemoteKey() {
        // The FireStick handler maps the full RemoteKey vocabulary, so none should be rejected.
        assertThat(tv.supportedKeys()).containsExactlyInAnyOrder(RemoteKey.values());
    }

    // --- connect / disconnect -------------------------------------------------

    @Test
    void connectReturnsTrueAndEnablesCommands() throws IOException {
        connectTv();

        tv.sendKey(RemoteKey.HOME);

        verify(dadb).shell("input keyevent 3");
    }

    @Test
    void connectReturnsFalseWhenKeyPairCannotBeLoaded() throws IOException {
        when(keyPair.loadOrCreateKeyPair()).thenThrow(new IOException("no key store"));

        assertThat(tv.connect()).isFalse();
    }

    @Test
    void connectReturnsFalseWhenDeviceUnreachable() {
        try (MockedStatic<Dadb> dadbStatic = mockStatic(Dadb.class)) {
            dadbStatic.when(() -> Dadb.create(eq(HOST), anyInt(), any(), anyInt()))
                    .thenThrow(new RuntimeException("connection refused"));

            assertThat(tv.connect()).isFalse();
        }
    }

    @Test
    void disconnectClosesTheConnectionAndDisablesCommands() throws Exception {
        connectTv();

        tv.disconnect();

        verify(dadb).close();
        assertThatThrownBy(() -> tv.sendKey(RemoteKey.POWER)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void disconnectIsSafeWhenNeverConnected() {
        assertThatCode(tv::disconnect).doesNotThrowAnyException();
    }

    // --- sendKey --------------------------------------------------------------

    @Test
    void sendKeyTranslatesToAnAndroidKeyevent() throws IOException {
        connectTv();

        tv.sendKey(RemoteKey.POWER);

        verify(dadb).shell("input keyevent 26");
    }

    @Test
    void sendKeyBeforeConnectThrows() {
        assertThatThrownBy(() -> tv.sendKey(RemoteKey.POWER)).isInstanceOf(IllegalStateException.class);
    }

    // --- openApp --------------------------------------------------------------

    @Test
    void openAppLaunchesKnownAppViaMonkey() throws IOException {
        connectTv();
        when(dadb.shell("monkey -p com.netflix.ninja 1")).thenReturn(shellResponse("Events injected: 1", 0));

        tv.openApp("Netflix");

        verify(dadb).shell("monkey -p com.netflix.ninja 1");
    }

    @Test
    void openAppThrowsWhenMonkeyAborts() throws IOException {
        connectTv();
        when(dadb.shell(anyString()))
                .thenReturn(new AdbShellResponse("", "** No activities found to run, monkey aborted.", 252));

        assertThatThrownBy(() -> tv.openApp("Netflix"))
                .isInstanceOf(AppLaunchException.class)
                .hasMessageContaining("Netflix");
    }

    @Test
    void openAppRejectsUnknownAppWithoutShelling() {
        connectTv();

        assertThatThrownBy(() -> tv.openApp("Nonexistent App"))
                .isInstanceOf(AppLaunchException.class)
                .hasMessageContaining("Nonexistent App");
        verifyNoInteractions(dadb);
    }

    @Test
    void openAppBeforeConnectThrows() {
        assertThatThrownBy(() -> tv.openApp("Netflix")).isInstanceOf(IllegalStateException.class);
    }

    // --- retrieveApps ---------------------------------------------------------

    @Test
    void retrieveAppsMapsKnownPackagesAndDropsUnknownOnes() throws IOException {
        connectTv();
        String packages = String.join("\n",
                "package:com.netflix.ninja",
                "package:com.disney.disneyplus",
                "package:com.example.somethingelse");
        when(dadb.shell("pm list packages")).thenReturn(shellResponse(packages, 0));

        List<App> apps = tv.retrieveApps();

        assertThat(apps).extracting(App::name).containsExactlyInAnyOrder("Netflix", "Disney+");
    }

    @Test
    void retrieveAppsCachesTheResult() throws IOException {
        connectTv();
        when(dadb.shell("pm list packages")).thenReturn(shellResponse("package:com.netflix.ninja", 0));

        tv.retrieveApps();
        tv.retrieveApps();

        verify(dadb, times(1)).shell("pm list packages");
    }

    @Test
    void retrieveAppsRefetchesAfterReconnect() throws IOException {
        when(dadb.shell("pm list packages")).thenReturn(shellResponse("package:com.netflix.ninja", 0));

        connectTv();
        tv.retrieveApps();
        tv.disconnect();
        connectTv();
        tv.retrieveApps();

        // disconnect() clears the cache, so the second session queries the device again.
        verify(dadb, times(2)).shell("pm list packages");
    }

    @Test
    void retrieveAppsBeforeConnectThrows() {
        assertThatThrownBy(tv::retrieveApps).isInstanceOf(IllegalStateException.class);
    }
}
