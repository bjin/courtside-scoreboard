# AGENTS.md

## Purpose
Courtside (`io.github.bjin.courtside`) turns an Android phone into a badminton scoreboard beside
the court: fullscreen landscape, two huge scores readable from about 6 m. A COROS APEX 4 watch
controls it through its built-in Media Control ("手机音频"): the app exposes a MediaSession, so the
watch treats it as the active media player and shows the score as the track title.

## Target system
- Phone: 2608×1200 display in landscape, small rounded corners, Android 16 (Xiaomi HyperOS).
  Sizes come from the actual window, so 16:9–21:9 phones at any density must also fit without
  clipping or touching display cutouts.
- Watch: COROS APEX 4 via the COROS Android app (`com.yf.smart.coros.dist`, needs notification
  access). Observed with COROS app 4.10.8: buttons arrive as media-button key-event pairs
  (`MEDIA_NEXT`, `MEDIA_PREVIOUS`, `MEDIA_PAUSE` while playing), and volume arrives as session
  `setVolumeTo(max)` bursts (≤6 calls, ≤90 ms apart). The phone's media volume is untouched.
- HyperOS: with default battery settings it freezes the app whenever it is not on screen (despite
  the FGS) and force-stops it when cleared from recents; `setShowWhenLocked` needs extra consent.

## Hard constraints
- Only `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK`; no network, accounts,
  analytics or ads. Never leave the phone's real media volume changed.
- A score tap is one finger, no drag beyond touch slop, at most 0.8 s. It never starts on a
  message, in a control's 1.5× zone, or within 24 dp of the rounded display edge (`TapGuard`).
- Reset returns to 0 : 0, clears the undo history, and always needs a deliberate second action.
- Metadata title `● L : R ○` (`●` = serving side) is updated after every change and at startup.

## Key design choices
- `core/` is pure Kotlin (only compile-time `KeyEvent` constants), unit-tested on the JVM:
  - `ScoreState`: undo history per team (A/B), score replayed from it; swap only flips the
    display mapping; scores cap at 99.
  - `ResetGuard`: two separate volume presses; the second at least 300 ms after the previous
    input and within 5 s. Bursts never confirm; `VolumeStepFilter` folds held-key repeats.
  - `MediaCommandMapping`: prev/rewind/skip-back/seek ≥1 s back = left +1;
    next/fast-forward/skip-forward/seek ≥1 s forward = right +1; play, pause, play/pause,
    headset hook = undo; stop ignored; only the initial key-down counts.
  - `ScoreLayout`: two tabular digits fill each half; one scale for both sides and all scores.
- `ScoreboardController` owns all state on the main thread (touch, session callbacks, volume),
  saves history + swap flag to SharedPreferences on every change, and exposes StateFlows plus a
  remote-input log that the menu shows for diagnostics.
- `media/ScoreboardService`: `mediaPlayback` foreground service with a framework `MediaSession`
  (not Media3: no real player; buttons, volume and state need raw control).
  - Always `STATE_PLAYING` (top of `getActiveSessions()`, no idle demotion); advertises only
    prev/next/play/pause; goes PAUSED→PLAYING on activity start to re-claim the top spot.
  - `onMediaButtonEvent` handled directly: the framework default delays play/pause and turns a
    double press into "next".
  - Volume: `setPlaybackToRemote` with an absolute `VolumeProvider` that springs back to mid.
    Relative steps (phone keys) count only while a screen is started (`ScreenRegistry.onScreen`),
    otherwise they go to STREAM_MUSIC. Opt-in `PhoneVolumeWatcher` fallback for other watches.
  - The service stops only when the last screen closes (`ScreenRegistry`): a reopened screen
    starts before the old one is destroyed.
  - `SilentPulse` plays 0.3 s of silence, because Android routes media keys to the app that
    last played audio.
  - The MediaStyle notification carries the session token (exempt from POST_NOTIFICATIONS).
- `ui/` (Compose):
  - Digits are vector paths from `res/font/score_digits.ttf` (B612 Bold digit subset, OFL;
    licence in `assets/licenses/`), chosen by a blur-at-6 m legibility measurement and fitted
    to exact ink bounds.
  - Layout avoids cutout rects that reach the digit rows, and rounded corners. LTR is forced
    (left/right are physical sides); the window is immersive and edge-to-edge.
- UX: the side that won the last point is tinted (in badminton the rally winner serves); changes
  flash the affected side; holding RESET fills the screen from the bottom; leaving needs two back
  presses; foreground volume keys are reset presses, handled in `onKeyDown`/`onKeyUp`.

## Build and test
Requires JDK 17+ and an Android SDK (`ANDROID_HOME` or `local.properties`). Versions live in
`gradle/libs.versions.toml` and `app/build.gradle.kts`. AGP's built-in Kotlin is used (no
kotlin-android plugin); targetSdk matches the Android 16 phone. In the agent shell, export the SDK
variables explicitly (`source <(…)` hung there) and run gradlew with `</dev/null`.
```sh
./gradlew assembleDebug assembleRelease   # release: R8; unsigned without release credentials
./gradlew test lintDebug                  # all JVM tests; lint must stay free of errors
./gradlew connectedDebugAndroidTest        # emulator/device; uninstalls the app afterwards
```
Instrumented tests reach the session through `ScoreboardApp.sessionToken` + `MediaController`.
Release signing requires `COURTSIDE_RELEASE_KEYSTORE`, `COURTSIDE_RELEASE_STORE_PASSWORD`,
`COURTSIDE_RELEASE_KEY_ALIAS`, and `COURTSIDE_RELEASE_KEY_PASSWORD`; credentials stay outside
the repository. The maintainer's local environment is `$HOME/.config/courtside/signing/release.env`.
App versions come from `tools/git-version.sh`: `versionName` uses the nearest `vMAJOR.MINOR.PATCH`
tag and commit distance; `versionCode` counts reachable commits. Full Git history is required.
CI runs all JVM and instrumented tests on branch/tag pushes and pull requests; only pushes
build signed release APK artifacts. `v*` tag pushes additionally publish the APK as a GitHub
Release asset. Never expose signing secrets to pull-request code.

## Emulator instrumentation
```sh
adb shell settings put system accelerometer_rotation 0; adb shell settings put system user_rotation 1
adb shell wm size 1200x2608; adb shell wm density 480   # target; 1080x1920 = 16:9; wm size reset
adb shell cmd overlay enable-exclusive --category com.android.internal.display.cutout.emulation.tall
adb shell cmd media_session dispatch next              # previous | play-pause
adb shell dumpsys media_session    # state=PLAYING, volumeType=REMOTE, description=<L : R>, <status>
adb shell input keyevent KEYCODE_VOLUME_UP              # twice, >= 0.3 s apart = reset
adb exec-out screencap -p > shot.png
adb shell uiautomator dump /sdcard/ui.xml               # control bounds via content descriptions
```
- `dispatch` reaches the app only while it is the media-button session, i.e. after its silent
  clip played (setting "Receive media buttons").
- On API 37 `cmd media_session volume --set` has no effect; the phone-volume fallback is covered
  by the instrumented test.
- Under `wm size` overrides the cutout the app gets differs from `dumpsys window`; trust the app.
- Keep `res/mipmap-anydpi-v26`: lint's ObsoleteSdkInt hint is wrong, and moving it breaks
  resource linking.
- `ComponentActivity.dispatchKeyEvent` is restricted API; override `onKeyDown`/`onKeyUp` instead.

## Conventions
- Scoreboard logic goes in `core/` with JVM tests; Android glue stays thin.
- Commit only snapshots that build and pass tests. `prompt.txt` and `environment.txt` are local,
  untracked notes.
