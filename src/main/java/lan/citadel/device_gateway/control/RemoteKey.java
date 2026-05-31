package lan.citadel.device_gateway.control;

/**
 * Brand-neutral remote-control keys accepted by the API. This enum is the canonical command
 * vocabulary: each {@link KeyInput} implementation maps the subset it supports to its own wire
 * representation, so an unknown key cannot get past request parsing.
 */
public enum RemoteKey {
    POWER, HOME, BACK, MENU,
    UP, DOWN, LEFT, RIGHT, OK,
    VOLUME_UP, VOLUME_DOWN, MUTE,
    CHANNEL_UP, CHANNEL_DOWN,
    PLAY, PAUSE, STOP, REWIND, FAST_FORWARD,
    INPUT_SOURCE
}
