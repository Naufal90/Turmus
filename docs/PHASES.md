# Development Phases

Mirrors `AGENTS.md §34`. Every phase must leave `./gradlew assembleDebug`
green and preserve everything from the previous phase.

| Phase | Deliverable | Status |
|-------|-------------|--------|
| 1 | Android foundation (Kotlin, Compose, modules, CI, debug APK) | ✅ Done |
| 2 | P2P discovery/advertise/connect/disconnect over Nearby | ✅ Done |
| 3 | Control channel: HELLO, PING/PONG, DEVICE_INFO, DISCONNECT | ✅ Done |
| 4 | Voice MVP: AudioRecord ↔ Opus/PCM ↔ AudioTrack | ✅ Done |
| 5 | Audio processing: AEC/NS/AGC lifecycle + per-peer negotiation | ✅ Done |
| 6 | Audio output routing (Speaker/Earpiece/BT/Wired/Auto) | ✅ Done |
| 7 | Real-time voice effects (9 presets + DSP engine) | ✅ Done |
| 8 | Local music player (Media3/ExoPlayer + MediaStore) | ✅ Done |
| 9 | Shared music sync (host/client + drift correction) | ✅ Done |
| 10 | Voice/music mixer with clip-safe limiter | ⏳ |
| 11 | Polish: UI, reconnect, battery, accessibility, settings | ⏳ |

## Phase 9 details

Real host/client shared listening. No fake sync — every timestamp
comes from an injected clock, RTT from the live Phase 3 `RttMonitor`,
and drift correction actually calls `MusicPlayer.seekTo(...)`.

**Files added — `feature/music`**
- `MusicRole.kt` — `None` / `Host` / `Client` state; UI uses it to
  disable local controls in Client mode
- `MusicSyncEngine.kt` — pure state machine + coroutine loops:
    - Host observes `MusicPlayer.state`; emits `MUSIC_PLAY` on song
      change, `MUSIC_PAUSE` / `MUSIC_PLAY(resume)` on play/pause
      toggle, `MUSIC_SEEK` on user seek, `MUSIC_STOP` on stop
    - Host heartbeat: `MUSIC_SYNC` every 1 s (position + isPlaying)
    - Client receives all of the above via `applyRemote*` methods
    - Drift decision delegated to `MusicSyncCalculator` (Phase 1):
      None / GentleSpeed / Seek. Both non-None currently apply as a
      corrective seek (Phase 11 will add
      `setPlaybackParameters(speed)` for the gentle path)
    - Extrapolates host position as `hostPos + (now − hostTs) +
      oneWayLatencyMs`; latency = live `RttMonitor.rttMs / 2`
    - Client that receives a song id it does NOT have in its library
      falls back silently — never fabricates a playback
- `MusicSyncSerializer.kt` — round-trips `SyncOutbound` ↔ `Message`
  envelope. `Resume` is wire-encoded as `MUSIC_PLAY` with blank
  `songId` so a client that missed the initial `MUSIC_PLAY` recovers
  on the next resume
- `MusicChannel.kt` — bridges transport bytes ↔ engine: subscribes
  to `TransportEvent.ControlReceived`, decodes only `MUSIC_*` types
  (leaves `HELLO`/`PING`/etc to `ControlChannel`), forwards to
  engine; also serialises outbound and calls `transport.sendControl`

**Protocol payloads (`core/protocol`)**
- `MusicPlayPayload`, `MusicPausePayload`, `MusicSyncPayload`,
  `MusicSeekPayload`, `MusicStopPayload` — all carry `hostTimestamp`
  for latency-compensated extrapolation

**Wiring**
- `feature/music/build.gradle.kts` — depends on `feature/control`
  (for `ControlCodec`) and `kotlin-serialization`
- `MusicViewModel` — new `role`, `attachSync(engine)` / `detachSync()`,
  Client mode DISABLES local playback commands (play/pause/seek/stop)
  so the UI honours the host
- `ConnectionViewModel.attachMusic(player, lookup)` — builds
  `MusicSyncEngine` + `MusicChannel` once, wires `oneWayLatencyMs`
  to `control.rttMs / 2`; teardown on disconnect/onCleared
- `MusicScreen` — new "Shared listening" card with Host / Follow peer /
  Leave buttons; track list and player controls disabled when
  role = Client; `LaunchedEffect` bridges the two ViewModels on first
  composition

**Tests added (pure-JVM)**
- `MusicSyncSerializerTest` (4) — Play round-trip via envelope, Resume
  encoded as MUSIC_PLAY with blank songId, Sync round-trip, Seek/Stop
  land on their own message types

**AGENTS.md compliance**
- §1.1 preserve existing — solo music playback from Phase 8 untouched;
  Host/Client only engages when the user chooses a role
- §1.2 no fake — extrapolation uses `System.currentTimeMillis()` and
  real RTT; missing song → silent fall-through, not a mock track
- §5 abstracted — `MusicSyncEngine` only knows `MusicPlayer`'s public
  API; `MusicChannel` only knows `P2PTransport`. Neither imports
  Nearby / ExoPlayer types
- §7 versioned protocol — every outbound is a `Message(version=1,…)`
- §15 shared playback — MUSIC_PLAY / MUSIC_PAUSE / MUSIC_SEEK /
  MUSIC_SYNC / MUSIC_STOP all present and dispatched
- §23 clean shutdown — `MusicChannel.shutdown()` cancels event pump
  and calls `engine.leave()`; `ConnectionViewModel.disconnect()`
  cascades before releasing the transport
- §26 structured logging — engine logs `role=Host` on becomeHost;
  missing-song reported via `CODEC_ERROR` tag
- §29 tests — serializer covered
- §36 handshake gate — sync uses the same control channel that only
  reaches `SessionPhase.Ready` after HELLO; no MUSIC_* can be sent
  before pairing
- §43 content-derived id — Phase 8's SHA-1 `contentId` is the wire
  identifier; the host never sends its local URI
- §44 latency-first — heartbeat is 1 s and every correction is
  timestamped so the client extrapolates rather than lagging
