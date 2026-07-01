package lan.citadel.device_gateway;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import lan.citadel.device_gateway.device_discovery.Manufacturer;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public final class GatewayMetrics
{
    private final MeterRegistry meterRegistry;

    GatewayMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    private static @NonNull List<Tag> parseTags(@NonNull Map<Tags, String> tags) {
        return tags.entrySet().stream()
            .map(entry -> Tag.of(entry.getKey().getKey(), entry.getValue()))
            .toList();
    }

    //
    public void incrementCounter(@NonNull Meter meter, Map<Tags, String> tags) {
        String meterName = meter.toString();
        List<Tag> tagList = parseTags(tags);
        meterRegistry.counter(meterName, tagList).increment();
    }

    public @NonNull Timer registerTimer(@NonNull Meter meter, Map<Tags, String> tags) {
        return Timer.builder(meter.toString())
                .tags(parseTags(tags))
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    /**
     * Times {@code op} and records the duration only when it completes normally. If {@code op}
     * throws, the exception propagates and nothing is recorded, so the timer reflects
     * success latency only.
     */
    public <T> T recordSuccessDuration(@NonNull Meter meter, Map<Tags, String> tags, @NonNull Supplier<T> op) {
        long start = System.currentTimeMillis();
        T result = op.get();
        registerTimer(meter, tags).record(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS);
        return result;
    }

    /**
     * Void variant of {@link #recordSuccessDuration(Meter, Map, Supplier)} for operations that
     * return nothing. Records the duration only when {@code op} completes normally.
     */
    public void recordSuccessDuration(@NonNull Meter meter, Map<Tags, String> tags, @NonNull Runnable op) {
        long start = System.currentTimeMillis();
        op.run();
        registerTimer(meter, tags).record(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS);
    }

}
