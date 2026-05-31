package lan.citadel.device_gateway.tvs;

import lan.citadel.device_gateway.control.RemoteKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SamsungTelevisionTest {

    private final SamsungTelevision tv = new SamsungTelevision("10.0.0.5", mock(TokenStore.class));

    @Test
    void exposesItsHost() {
        assertThat(tv.host()).isEqualTo("10.0.0.5");
    }

    @Test
    void supportsEveryCanonicalRemoteKey() {
        // The Samsung handler maps the full RemoteKey vocabulary, so none should be rejected.
        assertThat(tv.supportedKeys()).containsExactlyInAnyOrder(RemoteKey.values());
    }
}
