# Offline P2P Voice & Music

Production-oriented Android app for **fully offline, device-to-device**
voice and music communication. Two phones — no server, no Internet, no
account.

Built strictly to the rules in [`AGENTS.md`](./AGENTS.md).

## Features (target)

- Full-duplex, low-latency voice (Opus + AEC/NS/AGC)
- Peer-to-peer discovery + connection via Google Nearby (transport-abstracted)
- Shared music playback with position/tempo synchronization (Media3)
- Real-time voice effects (Robot, Radio, Reverb, …)
- Audio routing: Speaker / Earpiece / Bluetooth / Wired / Auto
- Bluetooth audio with real device names
- Foreground service for background sessions

## Current status

Phase 1 is complete: buildable Android skeleton with the modular
architecture, Compose UI, sealed state model, protocol envelope,
transport abstraction, and CI. Every module compiles and the app boots
to a working navigation shell. Phases 2–11 are outlined per
`AGENTS.md §34` and land incrementally.

## Build

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

CI (GitHub Actions) also runs `lint` and `test`.

## Module layout

```
app/                       -- Compose UI shell + MainActivity + foreground service
core/common                -- ConnectionState, AudioRoute, structured log tags
core/protocol              -- Versioned JSON message envelope + payload types
core/network               -- P2PTransport abstraction + Nearby stub
core/audio                 -- VoiceConfig, AudioEffectsProbe, MusicSyncCalculator
feature/connection         -- ConnectionCoordinator (state flow)
feature/voice              -- Voice session façade (Phase 4)
feature/music              -- PlaylistItem + Media3 hooks (Phase 8)
feature/effects            -- Voice-effect presets & parameters (Phase 7)
feature/settings           -- App-wide preferences
```

## Development phases

See `AGENTS.md §34`. Do **not** delete or rewrite features to
"simplify" the architecture (see §1.1, §49).
