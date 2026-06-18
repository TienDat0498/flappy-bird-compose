# Flappy Bird — Android (Jetpack Compose)

A from-scratch Android port of the classic Flappy Bird game, written in
Kotlin and rendered with Jetpack Compose's `Canvas`. The game logic is
kept pure-Kotlin and free of Android imports so it can be unit-tested on
the JVM.

## What it does

- Renders a 288×512 virtual viewport, letterboxed to fit any device.
- A yellow bird that flaps on tap, with gravity, rotation, and a 3-frame
  wing-flap animation.
- Procedurally spawned green pipe pairs; the bird scores one point per
  pipe cleared.
- A three-state game loop: `READY` (title screen, bird bobs in place) →
  `PLAYING` → `GAME_OVER` (with a short restart window).
- Five SFX (wing, hit, die, point, swoosh) played through `MediaPlayer`.
- Persistent best score via DataStore Preferences.
- Day-time background, scrolling ground, and the original number sprites
  for the score display.

## Project structure

```
app/src/main/java/com/example/myapplication/
├── MainActivity.kt              # Single Activity, hosts the Composable
├── ui/
│   ├── FlappyBirdGame.kt        # Composable: input, rendering, SFX dispatch
│   └── theme/                   # Material 3 theme files
└── game/                        # Pure-Kotlin game engine
    ├── GameEngine.kt            # State machine, collision, scoring
    ├── GameSnapshot.kt          # Read-only per-tick world state
    ├── GameState.kt             # READY / PLAYING / GAME_OVER
    ├── GameConfig.kt            # World constants (viewport, speeds, sizes)
    ├── Bird.kt                  # Bird physics + flap animation
    ├── PipePair.kt              # Obstacle pair with score box
    ├── SoundManager.kt          # Android-side MediaPlayer wrapper
    ├── HighScoreStore.kt        # DataStore-backed best score
    └── HighScoreStoreExt.kt     # Application-context DataStore binding

app/src/main/assets/
├── sprites/                     # Bird, pipes, ground, numbers, etc.
└── audio/                       # OGG sound effects
```

## Build

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

## Run tests

```bash
./gradlew test
```

Unit tests cover the pure-Kotlin game engine (bird physics, pipe
scrolling, state transitions, scoring, and `GameConfig`).

## Requirements

- Android Studio (recent) or the Gradle wrapper
- JDK 11
- Android SDK with platform 36 (compile/target) and API 24+ (min)
