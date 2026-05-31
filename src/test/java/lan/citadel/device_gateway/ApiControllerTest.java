package lan.citadel.device_gateway;

import lan.citadel.device_gateway.control.App;
import lan.citadel.device_gateway.control.RemoteKey;
import lan.citadel.device_gateway.device_discovery.DeviceRegistry;
import lan.citadel.device_gateway.device_discovery.DeviceType;
import lan.citadel.device_gateway.device_discovery.LogicalDevice;
import lan.citadel.device_gateway.device_discovery.Manufacturer;
import lan.citadel.device_gateway.exceptions.NoActiveSessionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiControllerTest {

    private DeviceRegistry deviceRegistry;
    private RemoteSessionManager sessionManager;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        deviceRegistry = mock(DeviceRegistry.class);
        sessionManager = mock(RemoteSessionManager.class);
        mvc = MockMvcBuilders.standaloneSetup(new ApiController(deviceRegistry, sessionManager))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void getDevicesReturnsTelevisions() throws Exception {
        when(deviceRegistry.getTelevisions())
                .thenReturn(List.of(new LogicalDevice("10.0.0.5", "Living Room TV", Manufacturer.SAMSUNG, DeviceType.TV)));

        mvc.perform(get("/api/remote/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].host").value("10.0.0.5"))
                .andExpect(jsonPath("$[0].deviceName").value("Living Room TV"));
    }

    @Test
    void connectActivatesTheRequestedDevice() throws Exception {
        mvc.perform(post("/api/remote/connect/10.0.0.5")).andExpect(status().isOk());

        verify(sessionManager).setActiveRemote("10.0.0.5");
    }

    @Test
    void getAppsReturnsTheSessionAppList() throws Exception {
        when(sessionManager.getApps()).thenReturn(List.of(new App("11101200001", "Netflix")));

        mvc.perform(get("/api/remote/apps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Netflix"));
    }

    @Test
    void openAppDelegatesToTheSession() throws Exception {
        mvc.perform(post("/api/remote/apps/Netflix/open")).andExpect(status().isOk());

        verify(sessionManager).openApp("Netflix");
    }

    @Test
    void getKeysReturnsSupportedKeys() throws Exception {
        when(sessionManager.supportedKeys()).thenReturn(Set.of(RemoteKey.POWER));

        mvc.perform(get("/api/remote/keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("POWER"));
    }

    @Test
    void pressKeySendsTheParsedKey() throws Exception {
        mvc.perform(post("/api/remote/keys/VOLUME_UP")).andExpect(status().isOk());

        verify(sessionManager).sendKey(RemoteKey.VOLUME_UP);
    }

    @Test
    void noActiveSessionIsMappedToConflictByTheAdvice() throws Exception {
        doThrow(new NoActiveSessionException()).when(sessionManager).sendKey(RemoteKey.POWER);

        mvc.perform(post("/api/remote/keys/POWER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }
}
