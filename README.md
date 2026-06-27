# device-gateway

A Spring Boot service that discovers smart-home/AV devices on the local network
and exposes a simple HTTP API to control them. It started as a Samsung TV remote
backend and is structured around a device-agnostic control layer so other device
types can be added.

## How it works

- **Discovery** — finds devices on the LAN via UPnP/SSDP ([jUPnP](https://github.com/jupnp/jupnp))
  and mDNS ([JmDNS](https://github.com/jmdns/jmdns)). Both rely on multicast.
- **Control layer** — a device-agnostic `DeviceController` abstraction
  (`control/`) with concrete implementations such as Samsung TVs (`tvs/`,
  using a WebSocket connection).
- **Sessions** — `RemoteSessionManager` tracks the currently connected device
  and routes key presses / app launches to it.

### Package layout (`lan.citadel.device_gateway`)

| Package            | Responsibility                                             |
|--------------------|------------------------------------------------------------|
| `device_discovery` | UPnP + mDNS discovery, device registry                     |
| `control`          | Device-agnostic controller abstraction, apps, remote keys  |
| `tvs`              | TV-specific controllers (Samsung), pairing token storage   |
| `requests`         | API request DTOs                                           |
| `exceptions`       | Domain exceptions (mapped to HTTP by `ApiExceptionHandler`)|

## API

Base path: `/api/remote`

| Method | Path                       | Description                                  |
|--------|----------------------------|----------------------------------------------|
| GET    | `/devices`                 | List discovered televisions                  |
| POST   | `/connect/{device_key}`    | Set the active device for this session       |
| GET    | `/apps`                    | List apps available on the active device     |
| POST   | `/apps/{app_name}/open`    | Launch an app on the active device           |
| GET    | `/keys`                    | List remote keys the active device supports  |
| POST   | `/keys/{key}`              | Send a remote key press to the active device |

Actuator health/metrics are exposed under `/actuator` (in the `prod` profile,
limited to `health` and `metrics`).

## Requirements

- Java 21 (the Gradle toolchain will provision it)
- Network access that allows multicast (UPnP/mDNS) to the devices you control

## Running locally

```bash
# Run with the local profile (TRACE logging for lan.citadel.device_gateway)
./gradlew bootRun --args='--spring.profiles.active=local'

# Run tests
./gradlew test

# Build a runnable jar
./gradlew bootJar
```

By default the service listens on **port 8080**. Override with `SERVER_PORT`:

```bash
SERVER_PORT=9090 ./gradlew bootRun
```

## Running with Docker (Unraid)

Discovery uses multicast, which does **not** traverse Docker's bridge network,
so the container runs with **host networking** (the "Host" network type on
Unraid). With host networking the service binds directly on the NAS's IP.

```bash
docker compose up -d --build
```

Configuration via environment variables (see `docker-compose.yml`):

| Variable                 | Default | Purpose                                              |
|--------------------------|---------|------------------------------------------------------|
| `SPRING_PROFILES_ACTIVE` | `prod`  | Active Spring profile                                |
| `SERVER_PORT`            | `8080`  | Listen port (change if 8080 is taken on the NAS)     |
| `JAVA_OPTS`              | _empty_ | Extra JVM flags, e.g. `-Xmx256m`                     |
| `PUID` / `PGID`          | `99`/`100` | Runtime user/group owning `/data` (Unraid defaults)|

Pairing tokens and logs are written under `/data`, which is mounted as a volume
so they survive container restarts. On Unraid, point it at appdata, e.g.
`/mnt/user/appdata/device-gateway:/data`.

## Configuration profiles

- **(default)** — `INFO` logging, port from `SERVER_PORT` (8080 fallback).
- **`local`** — `TRACE` logging for the app, console pattern, and dev state
  (token store, device names, adb keys) under `./.local-data/` (gitignored)
  instead of `/data`, so no mounted volume is needed.
- **`prod`** — token store and log file under `/data`, actuator health/metrics
  exposed. Used by the Docker image.
