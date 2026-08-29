# Block Blast (No Ads)

A faithful, from-scratch clone of the **Block Blast** block puzzle game for Android,
matching the look and feel of the reference build — **without any ads**, analytics,
internet permissions, or third-party SDKs. Everything (graphics, sounds, game logic)
is self-contained.

## How to play
- Drag one of the 3 pieces at the bottom onto the **8×8** board.
- Fill a full **row or column** to clear it.
- Clear multiple lines in one move for combo bonuses; chain clears on consecutive
  moves for a streak bonus.
- The game ends when none of the 3 offered pieces can be placed on the board.
- Tap the **gear** (top right) to toggle sound or restart.

## Features
- Exact reference art style: warm brown gradient background, dark board frame,
  glossy gold 3D blocks, crown + best score, big centered score, gear + NEW badge.
- Smooth drag & drop with a ghost preview (green when a spot fits, red when it
  doesn't), pop-in placement, flash-and-shrink clear animation, screen shake on an
  invalid drop, and floating "GOOD / GREAT / EXCELLENT" + combo text.
- Synthesized sound effects (no audio files) with a sound toggle and haptics.
- Auto-save: best score and the current board/tray persist across launches.
- Game-over screen with "Play Again".
- Full piece set: singles, dominoes, length 3–5 lines, 2×2 and 3×3 squares,
  corners, L/J, T, S/Z and larger shapes (same tray pieces as the reference).

## Technical
- 100% Kotlin, **zero dependencies** in the app (custom `View` + `Canvas` rendering).
- Pure, testable game logic (`Game.kt`) with JVM unit tests and a Robolectric
  UI-flow test (drag → place → settings → game over → restart).
- `minSdk 24`, `targetSdk 34`, portrait, fullscreen immersive.

## Build & install
With a JDK 17 and the Android SDK (platform 34 + build-tools 34.0.0):

```bash
./gradlew assembleDebug
# install on a connected device/emulator:
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or open the `BlockBlast` folder in Android Studio and press **Run**.

A ready-to-install **`app-debug.apk`** is provided alongside this folder.

## Building a signed release
The project signs a release automatically when a `keystore.properties` file exists in
the project root:

```properties
storeFile=/absolute/path/to/your.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Without that file, `assembleRelease` produces an unsigned release APK (which you can
sign manually with `apksigner`). `keystore.properties` and `*.jks` are git-ignored so
your signing key is never committed.

## License
Released under the [MIT License](LICENSE). This is an original, from-scratch
reimplementation for educational purposes and includes no third-party assets.
