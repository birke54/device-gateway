package lan.citadel.device_gateway.device_discovery;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lan.citadel.device_gateway.exceptions.DeviceNotFoundException;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class DeviceRegistry {
    /**
     * Logical devices keyed by host. A physical device (e.g., a Samsung TV) publishes several
     * discovery advertisements — multiple UPnP root devices plus mDNS services — that all share a
     * host; they are merged into a single {@link LogicalDevice} here. The individual advertisements
     * are not retained.
     * <p>
     * Because only the merged device is kept, a single advertisement's removal cannot be acted on
     * (the device may still be alive via its other advertisements, and mDNS removals don't even
     * carry the host). Eviction is therefore driven solely by TTL expiry in {@link #sweepExpired()}.
     */
    private final ConcurrentHashMap<String, Entry> deviceRegistry = new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(DeviceRegistry.class);

    /** How often the sweeper checks for stale entries.*/
    @Value("${device-gateway.device-sweeper-interval:10}")
    private int SWEEP_INTERVAL_SECONDS;

    private ScheduledExecutorService sweeper;

    /**
     * A merged logical device plus bookkeeping: the wall-clock time it was last seen (any
     * advertisement for the host), the TTL to expire it after (the longest-lived advertisement, so
     * we wait for the slowest re-announcer), and the score of the advertisement whose metadata is
     * currently representative (so a better advertisement can take over the name).
     */
    private record Entry(LogicalDevice device, long lastSeenMillis, int ttlSeconds, int representativeScore) { }

    @PostConstruct
    public void start() {
        sweeper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "device-registry-sweeper");
            t.setDaemon(true);
            return t;
        });
        sweeper.scheduleAtFixedRate(this::sweepExpired,
                SWEEP_INTERVAL_SECONDS, SWEEP_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() {
        if (sweeper != null) {
            sweeper.shutdownNow();
        }
    }

    public void addDevice(Device advertisement) {
        mergeIn(advertisement);
    }

    public void updateDevice(Device advertisement) {
        mergeIn(advertisement);
    }

    /**
     * Folds one discovery advertisement into the logical device for its host, creating it if absent.
     * Metadata is upgraded monotonically — a known device type and a known manufacturer always win
     * (first known value sticks; {@code UNKNOWN} only fills a gap), and the friendly name is taken
     * from the highest-scoring advertisement seen so far — so the vaguer advertisements (e.g., an mDNS
     * airplay record reporting {@code UNKNOWN}) never degrade an entry.
     */
    private void mergeIn(@NonNull Device ad) {
        deviceRegistry.compute(ad.hostName(), (host, existing) -> {
            long now = System.currentTimeMillis();
            int adScore = representativeScore(ad);
            if (existing == null) {
                logger.debug("Logical device added to registry: {} ({})", ad.deviceName(), host);
                return new Entry(
                        new LogicalDevice(host, ad.deviceName(), ad.manufacturer(), ad.deviceType()),
                        now, ad.ttl(), adScore);
            }

            LogicalDevice current = existing.device();
            DeviceType deviceType = current.deviceType() != DeviceType.UNKNOWN
                    ? current.deviceType()
                    : ad.deviceType();
            Manufacturer manufacturer = current.manufacturer() != Manufacturer.UNKNOWN
                    ? current.manufacturer()
                    : ad.manufacturer();
            String deviceName = adScore > existing.representativeScore() ? ad.deviceName() : current.deviceName();
            int representativeScore = Math.max(existing.representativeScore(), adScore);
            int ttlSeconds = Math.max(existing.ttlSeconds(), ad.ttl());

            logger.debug("Logical device refreshed in registry: {} ({})", deviceName, host);
            return new Entry(new LogicalDevice(host, deviceName, manufacturer, deviceType),
                    now, ttlSeconds, representativeScore);
        });
    }

    /** Ranks an advertisement's metadata: a known device type dominates, a known manufacturer breaks ties. */
    private static int representativeScore(@NonNull Device ad) {
        return (ad.deviceType() != DeviceType.UNKNOWN ? 2 : 0) + (ad.manufacturer() != Manufacturer.UNKNOWN ? 1 : 0);
    }

    public LogicalDevice getDevice(String host) {
        Entry entry = deviceRegistry.get(host);
        if (entry == null) {
            throw new DeviceNotFoundException(host);
        }
        return entry.device();
    }

    public List<LogicalDevice> getDevicesByType(DeviceType type) {
        List<LogicalDevice> devices = new ArrayList<>();
        deviceRegistry.values().forEach(entry -> {
            if (entry.device().deviceType() == type) {
                devices.add(entry.device());
            }
        });
        return List.copyOf(devices);
    }

    public List<LogicalDevice> getTelevisions() {
        return getDevicesByType(DeviceType.TV);
    }

    public void clear() {
        deviceRegistry.clear();
    }

    /**
     * The only eviction path: a device that vanishes (power loss, network drop, or a graceful
     * goodbye) stops being re-advertised, so its entry ages out. Any entry not refreshed within its
     * TTL is removed.
     */
    private void sweepExpired() {
        long now = System.currentTimeMillis();
        deviceRegistry.forEach((host, entry) -> {
            long ageSeconds = (now - entry.lastSeenMillis()) / 1000;
            if (ageSeconds > entry.ttlSeconds()) {
                // Conditional remove: skips the entry if it was refreshed since we read it.
                if (deviceRegistry.remove(host, entry)) {
                    logger.info("Device expired from registry (no refresh within {}s ttl): {}",
                            entry.ttlSeconds(), entry.device().deviceName());
                }
            }
        });
    }
}
