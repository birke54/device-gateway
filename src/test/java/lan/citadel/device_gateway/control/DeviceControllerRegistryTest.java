package lan.citadel.device_gateway.control;

import lan.citadel.device_gateway.device_discovery.DeviceType;
import lan.citadel.device_gateway.device_discovery.LogicalDevice;
import lan.citadel.device_gateway.device_discovery.Manufacturer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceControllerRegistryTest {

    private static final LogicalDevice DEVICE =
            new LogicalDevice("10.0.0.5", "TV", Manufacturer.SAMSUNG, DeviceType.TV);

    @Test
    void returnsControllerFromFirstSupportingFactory() {
        DeviceController controller = mock(DeviceController.class);
        DeviceControllerFactory unsupported = mock(DeviceControllerFactory.class);
        when(unsupported.supports(DEVICE)).thenReturn(false);
        DeviceControllerFactory supported = mock(DeviceControllerFactory.class);
        when(supported.supports(DEVICE)).thenReturn(true);
        when(supported.create(DEVICE)).thenReturn(controller);

        DeviceControllerRegistry registry = new DeviceControllerRegistry(List.of(unsupported, supported));

        assertThat(registry.create(DEVICE)).contains(controller);
        verify(unsupported, never()).create(any());
    }

    @Test
    void returnsEmptyWhenNoFactorySupportsDevice() {
        DeviceControllerFactory factory = mock(DeviceControllerFactory.class);
        when(factory.supports(any())).thenReturn(false);

        DeviceControllerRegistry registry = new DeviceControllerRegistry(List.of(factory));

        assertThat(registry.create(DEVICE)).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoFactoriesRegistered() {
        DeviceControllerRegistry registry = new DeviceControllerRegistry(List.of());

        assertThat(registry.create(DEVICE)).isEmpty();
    }

    @Test
    void usesFirstSupportingFactoryWhenSeveralMatch() {
        DeviceController first = mock(DeviceController.class);
        DeviceControllerFactory firstFactory = mock(DeviceControllerFactory.class);
        when(firstFactory.supports(DEVICE)).thenReturn(true);
        when(firstFactory.create(DEVICE)).thenReturn(first);
        DeviceControllerFactory secondFactory = mock(DeviceControllerFactory.class);
        when(secondFactory.supports(DEVICE)).thenReturn(true);

        DeviceControllerRegistry registry = new DeviceControllerRegistry(List.of(firstFactory, secondFactory));

        assertThat(registry.create(DEVICE)).contains(first);
        verify(secondFactory, never()).create(any());
    }
}
