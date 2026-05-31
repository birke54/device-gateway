package lan.citadel.device_gateway;

import lan.citadel.device_gateway.control.App;
import lan.citadel.device_gateway.control.DeviceController;
import lan.citadel.device_gateway.control.DeviceControllerRegistry;
import lan.citadel.device_gateway.control.RemoteKey;
import lan.citadel.device_gateway.device_discovery.DeviceRegistry;
import lan.citadel.device_gateway.device_discovery.DeviceType;
import lan.citadel.device_gateway.device_discovery.LogicalDevice;
import lan.citadel.device_gateway.device_discovery.Manufacturer;
import lan.citadel.device_gateway.exceptions.DeviceNotFoundException;
import lan.citadel.device_gateway.exceptions.DeviceNotTelevisionException;
import lan.citadel.device_gateway.exceptions.NoActiveSessionException;
import lan.citadel.device_gateway.exceptions.TvConnectionException;
import lan.citadel.device_gateway.exceptions.UnsupportedTvException;
import lan.citadel.device_gateway.tvs.PersistentConnection;
import lan.citadel.device_gateway.tvs.Television;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class RemoteSessionManagerTest {

    private static final String HOST = "10.0.0.5";

    private DeviceRegistry deviceRegistry;
    private DeviceControllerRegistry controllerRegistry;
    private RemoteSessionManager sessionManager;

    @BeforeEach
    void setUp() {
        deviceRegistry = mock(DeviceRegistry.class);
        controllerRegistry = mock(DeviceControllerRegistry.class);
        sessionManager = new RemoteSessionManager(deviceRegistry, controllerRegistry);
    }

    private static LogicalDevice tvDevice() {
        return new LogicalDevice(HOST, "Living Room TV", Manufacturer.SAMSUNG, DeviceType.TV);
    }

    /** A controller that is both a Television and holds a persistent connection. */
    private Television persistentTv() {
        return mock(Television.class, withSettings().extraInterfaces(PersistentConnection.class));
    }

    @Test
    void setActiveRemoteThrowsWhenDeviceUnknown() {
        when(deviceRegistry.getDevice(HOST)).thenReturn(null);

        assertThatThrownBy(() -> sessionManager.setActiveRemote(HOST))
                .isInstanceOf(DeviceNotFoundException.class);
    }

    @Test
    void setActiveRemoteThrowsWhenDeviceIsNotTelevision() {
        when(deviceRegistry.getDevice(HOST))
                .thenReturn(new LogicalDevice(HOST, "Speaker", Manufacturer.UNKNOWN, DeviceType.UNKNOWN));

        assertThatThrownBy(() -> sessionManager.setActiveRemote(HOST))
                .isInstanceOf(DeviceNotTelevisionException.class);
    }

    @Test
    void setActiveRemoteThrowsWhenNoControllerSupportsDevice() {
        when(deviceRegistry.getDevice(HOST)).thenReturn(tvDevice());
        when(controllerRegistry.create(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionManager.setActiveRemote(HOST))
                .isInstanceOf(UnsupportedTvException.class);
    }

    @Test
    void setActiveRemoteThrowsWhenControllerIsNotATelevision() {
        when(deviceRegistry.getDevice(HOST)).thenReturn(tvDevice());
        when(controllerRegistry.create(any())).thenReturn(Optional.of(mock(DeviceController.class)));

        assertThatThrownBy(() -> sessionManager.setActiveRemote(HOST))
                .isInstanceOf(DeviceNotTelevisionException.class);
    }

    @Test
    void setActiveRemoteThrowsWhenPersistentConnectionFails() {
        Television tv = persistentTv();
        when(((PersistentConnection) tv).connect()).thenReturn(false);
        when(deviceRegistry.getDevice(HOST)).thenReturn(tvDevice());
        when(controllerRegistry.create(any())).thenReturn(Optional.of(tv));

        assertThatThrownBy(() -> sessionManager.setActiveRemote(HOST))
                .isInstanceOf(TvConnectionException.class);
    }

    @Test
    void setActiveRemoteConnectsPersistentTelevision() {
        Television tv = persistentTv();
        when(((PersistentConnection) tv).connect()).thenReturn(true);
        when(tv.retrieveApps()).thenReturn(List.of(new App("1", "Netflix")));
        when(deviceRegistry.getDevice(HOST)).thenReturn(tvDevice());
        when(controllerRegistry.create(any())).thenReturn(Optional.of(tv));

        sessionManager.setActiveRemote(HOST);

        verify((PersistentConnection) tv).connect();
        assertThat(sessionManager.getApps()).containsExactly(new App("1", "Netflix"));
    }

    @Test
    void setActiveRemoteAcceptsNonPersistentTelevisionWithoutConnecting() {
        Television tv = mock(Television.class); // not a PersistentConnection
        when(deviceRegistry.getDevice(HOST)).thenReturn(tvDevice());
        when(controllerRegistry.create(any())).thenReturn(Optional.of(tv));
        when(tv.supportedKeys()).thenReturn(Set.of(RemoteKey.POWER));

        sessionManager.setActiveRemote(HOST);

        assertThat(sessionManager.supportedKeys()).containsExactly(RemoteKey.POWER);
    }

    @Test
    void switchingActiveRemoteDisconnectsThePreviousConnection() {
        Television first = persistentTv();
        when(((PersistentConnection) first).connect()).thenReturn(true);
        Television second = persistentTv();
        when(((PersistentConnection) second).connect()).thenReturn(true);

        when(deviceRegistry.getDevice(HOST)).thenReturn(tvDevice());
        when(controllerRegistry.create(any()))
                .thenReturn(Optional.of(first))
                .thenReturn(Optional.of(second));

        sessionManager.setActiveRemote(HOST);
        sessionManager.setActiveRemote(HOST);

        verify((PersistentConnection) first).disconnect();
        verify((PersistentConnection) second, never()).disconnect();
    }

    @Test
    void operationsThrowWhenNoActiveSession() {
        assertThatThrownBy(() -> sessionManager.getApps()).isInstanceOf(NoActiveSessionException.class);
        assertThatThrownBy(() -> sessionManager.openApp("Netflix")).isInstanceOf(NoActiveSessionException.class);
        assertThatThrownBy(() -> sessionManager.sendKey(RemoteKey.POWER)).isInstanceOf(NoActiveSessionException.class);
        assertThatThrownBy(() -> sessionManager.supportedKeys()).isInstanceOf(NoActiveSessionException.class);
    }

    @Test
    void operationsDelegateToActiveTelevision() {
        Television tv = mock(Television.class);
        when(deviceRegistry.getDevice(HOST)).thenReturn(tvDevice());
        when(controllerRegistry.create(any())).thenReturn(Optional.of(tv));
        sessionManager.setActiveRemote(HOST);

        sessionManager.openApp("Netflix");
        sessionManager.sendKey(RemoteKey.HOME);

        verify(tv).openApp("Netflix");
        verify(tv).sendKey(RemoteKey.HOME);
    }

    @Test
    void shutdownDisconnectsActivePersistentConnection() {
        Television tv = persistentTv();
        when(((PersistentConnection) tv).connect()).thenReturn(true);
        when(deviceRegistry.getDevice(HOST)).thenReturn(tvDevice());
        when(controllerRegistry.create(any())).thenReturn(Optional.of(tv));
        sessionManager.setActiveRemote(HOST);

        sessionManager.shutdown();

        verify((PersistentConnection) tv).disconnect();
    }
}
