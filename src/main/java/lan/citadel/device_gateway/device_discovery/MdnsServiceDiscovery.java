package lan.citadel.device_gateway.device_discovery;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import javax.jmdns.ServiceTypeListener;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Objects;

@Component
public class MdnsServiceDiscovery implements DeviceDiscovery, Closeable {
    private static final Logger logger = LoggerFactory.getLogger(MdnsServiceDiscovery.class);
    private final DeviceRegistry deviceRegistry;
    private final DeviceNameStore nameStore;
    @Value("${device-gateway.mdns-device-ttl:86400}")
    private int DEFAULT_TTL_SECONDS;

    private JmDNS jmdns;

    public MdnsServiceDiscovery(DeviceRegistry deviceRegistry, DeviceNameStore nameStore) {
        this.deviceRegistry = deviceRegistry;
        this.nameStore = nameStore;
    }

    @Override
    @PostConstruct
    public void start() {
        try {
            InetAddress addr = resolveBindAddress();
            this.jmdns = JmDNS.create(addr);
            this.jmdns.addServiceTypeListener(buildTypeListener());
            logger.info("mDNS discovery started on {}", addr.getHostAddress());
        } catch (IOException e) {
            throw new RuntimeException("Failed to start mDNS discovery", e);
        }
    }

    /**
     * Determines the LAN address to bind mDNS to.
     * <p>
     * {@link InetAddress#getLocalHost()} frequently resolves to a loopback entry on Linux
     * (e.g. {@code 127.0.1.1} from {@code /etc/hosts}), which binds the mDNS multicast socket
     * to loopback and hides every device on the network. Instead, ask the routing table which
     * local interface would be used to reach an external host (this sends no traffic), and fall
     * back to scanning for a non-loopback site-local IPv4 address if that fails.
     */
    private @NonNull InetAddress resolveBindAddress() throws IOException {
        try (DatagramSocket probe = new DatagramSocket()) {
            probe.connect(InetAddress.getByName("8.8.8.8"), 53);
            InetAddress local = probe.getLocalAddress();
            if (local != null && !local.isLoopbackAddress() && !local.isAnyLocalAddress()) {
                return local;
            }
        } catch (SocketException e) {
            logger.warn("Could not probe default route for mDNS bind address, scanning interfaces", e);
        }

        for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) {
                continue;
            }
            for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                    return addr;
                }
            }
        }
        throw new IOException("No suitable non-loopback LAN address found for mDNS discovery");
    }

    @Override
    @PreDestroy
    public void close() throws IOException {
        if (jmdns != null) {
            jmdns.close();
            logger.info("mDNS discovery stopped");
        }
    }

    /**
     * Listens for the DNS-SD service-type meta-query ({@code _services._dns-sd._udp.local.}),
     * which JmDNS issues on our behalf. As each advertised service type is discovered, register a
     * {@link ServiceListener} for it so the underlying device instances get resolved. This lets us
     * discover every device on the network rather than a single hard-coded service type.
     */
    private @NonNull ServiceTypeListener buildTypeListener() {
        return new ServiceTypeListener() {
            @Override
            public void serviceTypeAdded(ServiceEvent event) {
                logger.info("mDNS service type discovered, browsing: {}", event.getType());
                jmdns.addServiceListener(event.getType(), buildListener());
            }

            @Override
            public void subTypeForServiceTypeAdded(ServiceEvent event) {
                logger.debug("mDNS service subtype discovered: {}", event.getType());
            }
        };
    }

    private @NonNull ServiceListener buildListener() {
        return new ServiceListener() {
            @Override
            public void serviceAdded(ServiceEvent event) {
                logger.debug("Service added: {}", event.getName());
                jmdns.requestServiceInfo(event.getType(), event.getName());
            }

            @Override
            public void serviceRemoved(ServiceEvent event) {
                // One service leaving does not mean the device is gone (it may still advertise
                // other services), and the removal event carries no host to map it to a logical
                // device anyway. Eviction is left to the registry's TTL sweeper.
                logger.debug("mDNS Service removed: {} {}", event.getType(), event.getName());
            }

            @Override
            public void serviceResolved(ServiceEvent event) {
                try {
                    Device device = parseEventInfo(event);
                    deviceRegistry.addDevice(device);
                    logger.debug("mDNS Service resolved: {}, {}", device.deviceName(), device.hostName());
                } catch (RuntimeException e) {
                    logger.warn("Failed to parse resolved mDNS service {}, skipping: {}",
                            event.getName(), e.toString());
                }
            }
        };
    }

    private @NonNull Device parseEventInfo(@NonNull ServiceEvent event) {
        // The host keys the registry, so a service that resolves without an address is unusable and
        // is rejected. Manufacturer/type fall back to UNKNOWN, so non-Samsung, non-TV devices still
        // register instead of throwing out of the listener and being silently dropped.
        ServiceInfo info = event.getInfo();
        String[] hostAddresses = info.getHostAddresses();
        if (hostAddresses.length == 0) {
            throw new IllegalStateException("mDNS service has no host address");
        }
        String hostName = hostAddresses[0];
        String discoveredName = Objects.requireNonNull(info.getName(), "mDNS service has no name");
        // Apply any manual override before classifying, so a corrected name drives the derived
        // manufacturer and type as well as the display name.
        String deviceName = nameStore.resolve(discoveredName);
        Manufacturer manufacturer = Manufacturer.fromName(deviceName);

        int port = info.getPort();
        int ttl = DEFAULT_TTL_SECONDS; // TODO: dynamic TTL
        return new Device(hostName, deviceName, manufacturer, port, ttl, DeviceType.fromName(deviceName));
    }
}