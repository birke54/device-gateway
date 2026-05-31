package lan.citadel.device_gateway.control;

/**
 * Base abstraction for anything the backend can control (a TV today; smart lights, etc. later).
 * Concrete devices mix in capability interfaces — {@code KeyInput}, {@code AppLauncher}, and future
 * ones like {@code PowerControl}/{@code Dimmable} — for the subset of abilities they actually expose,
 * so callers can discover support with an {@code instanceof} check rather than knowing concrete types.
 */
public interface DeviceController {
    /** The host (IP/hostname) this controller drives; matches the {@code LogicalDevice} it was built from. */
    String host();
}
