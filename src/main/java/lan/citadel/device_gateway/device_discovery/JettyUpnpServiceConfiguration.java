package lan.citadel.device_gateway.device_discovery;

import org.jupnp.DefaultUpnpServiceConfiguration;
import org.jupnp.transport.impl.jetty.JettyTransportConfiguration;
import org.jupnp.transport.impl.jetty.StreamClientConfigurationImpl;
import org.jupnp.transport.spi.StreamClient;

/**
 * Works around a bug in jUPnP 2.7.1: {@link DefaultUpnpServiceConfiguration}
 * declares a {@code StreamClientConfiguration} field that it never initializes,
 * then passes that null field into {@code JettyTransportConfiguration
 * .createStreamClient(...)}, which dereferences it and throws an NPE during
 * {@code UpnpService.startup()}.
 *
 * We override {@link #createStreamClient()} to supply a real configuration
 * (Jetty defaults: 10s timeout).
 */
public class JettyUpnpServiceConfiguration extends DefaultUpnpServiceConfiguration {

    @Override
    @SuppressWarnings("rawtypes")
    public StreamClient createStreamClient() {
        return JettyTransportConfiguration.INSTANCE.createStreamClient(
                getSyncProtocolExecutorService(),
                new StreamClientConfigurationImpl(getSyncProtocolExecutorService()));
    }
}
