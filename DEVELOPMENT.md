# Developing Courtside

User setup and controls are in [README.md](README.md); contributor rules are in
[AGENTS.md](AGENTS.md). This document covers building, maintaining, testing, and releasing the
Android app (`io.github.bjin.courtside`).

## Environment

- Install **JDK 17 or newer**, **Git**, **Bash**, and an **Android SDK with platform 37**.
  Set `ANDROID_HOME` to the SDK directory, or set `sdk.dir` in an untracked `local.properties`.
  Android platform-tools provide `adb`; install an emulator/system image if using virtual devices.
- Use the checked-in Gradle wrapper. It downloads Gradle **9.7.1** on first use; dependency
  resolution and the foojay JDK 17 toolchain resolver also need network access when uncached.
- The app compiles against API 37, targets API 36 (Android 16), and has minimum API 26.
  Targeting API 36 preserves the tested Android 16 behavior on newer emulators.
- Dependency/plugin versions live in [gradle/libs.versions.toml](gradle/libs.versions.toml);
  Android settings live in [app/build.gradle.kts](app/build.gradle.kts). AGP's built-in Kotlin
  support is used; do not add a separate `kotlin-android` plugin.
- Keep full Git history and version tags available. App versions are calculated during Gradle
  configuration, so a source archive without Git metadata is not sufficient. For a shallow
  clone, run `git fetch --unshallow --tags`; otherwise use `git fetch --tags` as needed.
- Keep signing credentials, generated output, and scratch notes out of commits. `local.properties`
  and build outputs are ignored. Additional font binaries are ignored intentionally; the B612
  digit subset is the sole exception.

For automated/non-interactive builds, pass `--no-daemon --console=plain` and redirect stdin from
`/dev/null`, as CI does. Set SDK environment variables explicitly in the process running Gradle.

## Build and install

```sh
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk

./gradlew assembleRelease --no-configuration-cache
# Signed: app/build/outputs/apk/release/app-release.apk
# Without credentials: app/build/outputs/apk/release/app-release-unsigned.apk
```

Release builds enable R8 optimization and resource shrinking. Prefer a release build for matches:
the existing project guidance notes faster startup and input response than debug builds. Unsigned
release APKs cannot be installed until signed. See [release signing](#release-signing) below.

For installation over Wi-Fi, enable *Wireless debugging* in the phone's developer options, then:

```sh
adb pair <pairing-ip:port>
adb connect <debugging-ip:port>
adb install -r app/build/outputs/apk/release/app-release.apk
```

Pairing and connection addresses/ports are shown separately by Android. USB debugging is an
alternative. Use a disposable test installation when changing signing keys or running
instrumented tests: uninstalling the app clears saved scores, history, and settings.

## Tests and checks

```sh
./gradlew test lintDebug
./gradlew connectedDebugAndroidTest
```

- `test` runs all JVM tests; `lintDebug` must remain free of errors.
- `connectedDebugAndroidTest` needs a connected device/emulator and drives the real
  `MediaSession` through `ScoreboardApp.sessionToken` and `MediaController`.
- **Connected instrumentation uninstalls the app afterwards.** Do not use an installation whose
  scores/settings you need to keep.
- Pure scoring logic belongs in `core/` with JVM tests; Android glue stays thin. Core code uses
  only compile-time `KeyEvent` constants from Android. The unit-test configuration permits
  Android default return values, but core behavior must remain JVM-testable without Android.
- Check screenshots at multiple aspect ratios, cutouts, themes, and locales for UI changes.
  The intended phone range is 16:9–21:9 at different densities; this is a layout requirement,
  not hardware certification.
- Commit snapshots that build and pass the relevant tests. Automated checks do not replace
  real watch/phone tests of routing, metadata, volume, and OEM background behavior.

## Release signing

Use a dedicated release key, never the development debug key. All four environment variables
are required for a signed release build:

| Variable | Meaning |
|---|---|
| `COURTSIDE_RELEASE_KEYSTORE` | Absolute path to the private release keystore |
| `COURTSIDE_RELEASE_STORE_PASSWORD` | Keystore password |
| `COURTSIDE_RELEASE_KEY_ALIAS` | Signing-key alias |
| `COURTSIDE_RELEASE_KEY_PASSWORD` | Signing-key password |

### Local key convention

Keep the key and environment file outside the repository:

- `~/.config/courtside/signing/release.keystore`
- `~/.config/courtside/signing/release.env`

For an authorized maintainer, check for the expected files without printing their contents:

```sh
test -f "$HOME/.config/courtside/signing/release.keystore"
test -f "$HOME/.config/courtside/signing/release.env"
```

An existing `release.env` should assign all four variables above, with the keystore path pointing
to `$HOME/.config/courtside/signing/release.keystore`. Source only a trusted file: it is shell
code. Export its assignments for Gradle and disable configuration caching for the signing build:

```sh
set -a
. "$HOME/.config/courtside/signing/release.env"
set +a
./gradlew assembleRelease --no-configuration-cache
adb install -r app/build/outputs/apk/release/app-release.apk
```

If the files are missing, recover the existing release identity from a secure backup or an
authorized maintainer. **Do not generate a replacement key for an existing release identity.**
For a separate fork or a genuinely new signing identity, create a private directory and an
interactive keystore (the prompts avoid putting passwords in command history):

```sh
install -d -m 700 "$HOME/.config/courtside/signing"
keytool -genkeypair \
  -keystore "$HOME/.config/courtside/signing/release.keystore" \
  -alias courtside -keyalg RSA -keysize 3072 -validity 10000
```

Only run that command when the destination does not already hold a key. Create `release.env`
with a trusted local editor, assigning the four variables to the actual private values. Restrict
both files to the account that builds releases:

```sh
chmod 600 "$HOME/.config/courtside/signing/release.keystore" \
  "$HOME/.config/courtside/signing/release.env"
```

Never commit, paste into issues, or print the environment file, passwords, or encoded keystore.
Avoid shell tracing (`set -x`) while handling them. Back up the keystore, alias, and passwords
securely. Disabling Gradle's configuration cache for signing avoids retaining credentials there.

The dedicated Courtside release certificate's recorded SHA-256 fingerprint (public information) is:

```text
f0448bf1836fa1d6c62fc1aa54f9c9b57c48572e57178bfb6c4c39bd96eb8d43
```

The first dedicated-key build cannot update an older debug-signed installation. Uninstall/reinstall
once, accepting loss of app data. Later releases must keep the same signing identity and increase
`versionCode`.

## Versioning

[app/build.gradle.kts](app/build.gradle.kts) reads both Android version fields from
[tools/git-version.sh](tools/git-version.sh); do not hard-code version numbers. Preview them with:

```sh
bash tools/git-version.sh
```

- **`versionName`** uses the nearest reachable `vMAJOR.MINOR.PATCH` tag. At `v0.1.0`, it is
  `0.1.0`; twelve commits later it is `0.1.0.r12.gxxxxxxxx`, with a hash of at least eight
  characters. Before the first version tag it is `0.0.0.r<count>.g<hash>`, using the total
  reachable commit count.
- **`versionCode`** is the total number of commits reachable from HEAD. Android orders updates
  using this integer, not the visible name. It increases along descendant history; unrelated
  branches can have the same code, so arbitrary branch builds do not guarantee an upgrade.
- Uncommitted changes do not change the generated version. Build official releases only from
  clean, tagged commits. Do not rewrite published release history, and create a new commit for
  each new release rather than adding multiple release tags to the same commit.

When preparing a release, commit all intended changes and complete verification first, then
manually create and push an annotated tag. For example, the initial release uses:

```sh
git tag -a v0.1.0 -m "Courtside 0.1.0"
git push origin master
git push origin v0.1.0
```

These are release actions, not build side effects. Use the appropriate `vMAJOR.MINOR.PATCH` tag
for subsequent releases; no source-file version bump is needed. Publishing a release does not
make a private repository public.

## GitHub Actions and release secrets

The configured [CI workflow](.github/workflows/ci.yml) runs on all branch pushes, `v*` tag pushes,
and pull requests targeting any branch. Checkouts fetch full history and tags for versioning.

1. **Tests:** all JVM tests, Android lint, and all instrumented tests on an Android 16/API 36
   emulator. Reports are uploaded even on failure when available.
2. **Signed APK:** after tests pass, branch/tag pushes build and verify a signed release APK.
   The run's artifact is named `courtside-release-<commit SHA>`. APK and test-report artifacts
   are retained for 30 days. Pull requests run tests only and never receive signing secrets.
3. **Publish:** only a `v*` tag push downloads that same verified APK, creates a GitHub
   **pre-release** with generated notes, and attaches `app-release.apk`. The tag must already
   exist on GitHub. Tag publishing remains pre-release-only until the release policy changes.
   Only this job has `contents: write`; it receives no signing secrets and does not execute
   checked-out repository code. Branch pushes and pull requests do not create releases.

Configure these repository Actions secrets before expecting a signed push build to succeed:

| Actions secret | Contents |
|---|---|
| `ANDROID_RELEASE_KEYSTORE_BASE64` | Base64-encoded bytes of the private release keystore |
| `ANDROID_RELEASE_STORE_PASSWORD` | Keystore password |
| `ANDROID_RELEASE_KEY_ALIAS` | Signing-key alias |
| `ANDROID_RELEASE_KEY_PASSWORD` | Signing-key password |

Use the repository's **Settings → Secrets and variables → Actions** or an authorized secret
management tool. Transfer values privately, without exposing them in logs, shell history, or
repository files. Base64 is transport encoding, not encryption: the encoded keystore is still a
secret. The signing job restores a temporary keystore, maps the secrets to the local build's
`COURTSIDE_RELEASE_*` variables, disables Gradle's configuration cache, verifies the APK with
`apksigner`, and removes the temporary keystore afterwards. Never grant pull-request code access
to signing secrets.

## Architecture and invariants

### State and input

- `core/` is pure Kotlin. `ScoreState` stores point history by team (A/B) and replays scores
  from that history, so history and score cannot disagree. Swap changes only the display
  mapping; undo and serving-side highlighting follow the teams. Scores cap at 99.
- `ScoreboardController` owns all state on the main thread. Touch, session callbacks, and volume
  pass through it. It saves history and swap state to SharedPreferences on every change and
  exposes StateFlows plus the menu's remote-input log. State survives rotation, activity
  recreation, and process restarts.
- `TapGuard` accepts one finger, no movement beyond touch slop, and a duration of at most 0.8 s.
  A scoring touch cannot begin on a message, inside a control's 1.5× exclusion zone, or within
  24 dp of the rounded display edge. System-bar swipes and near-miss controls must not score.
- Reset clears all history and is not undoable. On-screen reset requires a 0.6 s hold with a
  rising red fill. Remote reset uses `ResetGuard`: two separate volume inputs, the second at
  least 300 ms after the previous input and strictly within 5 s. Any other command cancels it.
  Bursts cannot confirm reset; `VolumeStepFilter` folds held-key/repeated relative inputs
  within 600 ms.
- `MediaCommandMapping` maps previous/rewind/skip-back and seeks at least 1 s backward to
  left +1; next/fast-forward/skip-forward and seeks at least 1 s forward to right +1. Play,
  pause, play/pause, and headset hook mean undo. Stop is ignored; only initial key-down counts.
- `watchTitle` builds the score title and serving marker. Refresh metadata at startup and after
  every change. The default title is `● L : R ○` or `○ L : R ●`; before a point both marks
  are hollow. Spaces distinguish circles from zeroes. With highlighting off, use `L : R`.
  The subtitle/status describes the last action, including the armed-reset prompt.

### Media service

`media/ScoreboardService` is a `mediaPlayback` foreground service using the framework
`android.media.session.MediaSession`, not Media3: there is no music player, and direct control
of raw buttons, volume, and state is intentional.

- The session reports `STATE_PLAYING` and advertises only previous/next/play/pause. This helps
  it remain prominent in `MediaSessionManager.getActiveSessions()` and avoids idle-session
  demotion, but cannot guarantee which session a companion app selects. On activity start it
  transitions PAUSED → PLAYING to reclaim prominence.
- Handle `onMediaButtonEvent` directly. The framework default introduces a 300 ms play/pause
  double-tap delay and can turn two quick undo presses into “next”.
- With volume reset enabled, `setPlaybackToRemote` exposes an absolute `VolumeProvider`
  spanning 0–10, springing back to midpoint 5. Session-volume input must not change the phone's
  real volume. Calls closer than 300 ms form a reset-input burst; held hardware keys count
  once until key-up.
- Relative volume steps count as reset input only while a screen is started
  (`ScreenRegistry.onScreen`); otherwise they go to `STREAM_MUSIC`. Absolute session-volume
  input can still reach the reset guard while the service is alive without a visible screen.
- `PhoneVolumeWatcher` is an opt-in fallback, off by default, for remotes that change the real
  media stream. While a screen is started it observes non-public Android volume/mute broadcasts,
  counts changes, and restores the previous volume on a best-effort basis. It cannot distinguish
  watch actions from other apps or the user, and blocks normal volume adjustment while active.
- `ScreenRegistry` stops the service only when the last screen closes. During recreation a new
  screen starts before the old one is destroyed; do not stop the service prematurely.
- `SilentPulse`, when *Receive media buttons* is enabled, plays 0.3 s of digital silence on
  opening. Android routes media keys to the app that last played audio. It requests no audio
  focus, and is not a guarantee against competing players or vendor routing decisions.
- A MediaStyle notification carries the session token for controllers discovering sessions
  through notifications. Media-session notifications are exempt from `POST_NOTIFICATIONS`.
- Keep permissions limited to `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`:
  no network access, accounts, analytics, ads, or runtime permission prompts.

### Layout, typography, and localization

- `ui/` uses Jetpack Compose. The window is immersive and edge-to-edge; a swipe temporarily
  reveals system bars. Force LTR: left/right are physical court sides, independent of locale.
- `ScoreLayout` derives geometry from the actual window, not a fixed resolution. Two tabular
  digits fill each half; all scores and both sides share one scale so reaching 10 does not
  cause a size jump. Exact ink bounds, not text layout boxes, determine the fit.
- The digits are vector outlines from `app/src/main/res/font/score_digits.ttf`, the B612 Bold
  0–9 subset. Cutout rectangles intersecting digit rows push that side's margin inward;
  rounded corners move corner controls inward. Keep scores and controls clear of both.
- B612 was selected by comparing 55 variants of 34 open fonts: render each at the size that fits
  2608×1200, blur to simulate 6 m viewing with 2–3 arcmin acuity loss, and compare every digit
  pair. It had the most even worst case. The 3 has a flat top; 6/9 have open, straight tails;
  1 has a foot and flag; 0 has no slash or dot. Figures are tabular, with strokes about 21% of
  figure height. On the measured 2608×1200 phone digits were about 700 px (approximately 41 mm)
  tall. These are historical design measurements, not a promise for every display/viewer.
- B612 is the Airbus/ENAC cockpit-display typeface and remains under SIL OFL 1.1. Preserve its
  [license](app/src/main/assets/licenses/B612-OFL.txt) and
  [source notice](app/src/main/assets/licenses/B612-SOURCE.txt); MIT does not replace them.
- UI text uses Android's system sans-serif with locale-aware CJK fallback. All languages share
  font sizes, weights, and line heights, though device fonts vary. Do not bundle CJK fonts or
  alter the digit vectors/fitting geometry as a side effect of text localization.
- Supported locales are system default, English, Simplified/Traditional Chinese, Japanese,
  Korean, German, French, and Spanish. The System label is translated; language choices use
  native names. Android 13+ exposes app-language settings; older versions persist selection
  locally. Unsupported system languages fall back to English.
- Use short button labels and fuller instructions. Translate controls, accessibility strings,
  notifications, and watch status. Raw diagnostics and all legal notices remain English; the
  MIT permissions summary is localized. Keep `LICENSE` and the bundled
  `assets/licenses/Courtside-MIT.txt` identical.
- Preserve serving-side tint, change flashes, remote-action feedback, and the two-back-press
  exit guard. Screen-on, full brightness, and show-when-locked default on but remain optional.

## Emulator commands and technical debugging

Use a disposable emulator for display overrides and test resets:

```sh
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
adb shell wm size 1200x2608
adb shell wm density 480
# Alternative portrait override: 1080x1920 gives 16:9 when rotated.
adb shell cmd overlay enable-exclusive --category com.android.internal.display.cutout.emulation.tall

adb shell cmd media_session dispatch next
# Other useful commands: previous, play-pause.
adb shell dumpsys media_session
adb shell input keyevent KEYCODE_VOLUME_UP
# Send twice, at least 0.3 s apart and within 5 s, to test reset.
adb exec-out screencap -p > shot.png
adb shell uiautomator dump /sdcard/ui.xml

# Remove display overrides when finished.
adb shell wm size reset
adb shell wm density reset
adb shell cmd overlay disable com.android.internal.display.cutout.emulation.tall
```

Restore rotation settings to their previous values if continuing to use that device. Use the
UI hierarchy's content descriptions to find controls and their bounds. In `dumpsys media_session`,
look for `state=PLAYING`, `volumeType=REMOTE`, and a description containing the score and status.
The menu's *Recent remote commands* log identifies received commands and caller packages when
available, for example `key MEDIA_NEXT → Right +1 · com.yf.smart.coros.dist`.

Important caveats:

- `cmd media_session dispatch` reaches Courtside only while it is Android's media-button target,
  normally after the silent pulse with *Receive media buttons* enabled.
- On API 37, `cmd media_session volume --set` has been observed to have no effect. Use the
  instrumented test for the phone-volume fallback rather than treating that shell command as
  proof of a broken volume path.
- With `wm size` overrides, the cutout delivered to the app can differ from `dumpsys window`;
  trust the app's insets when assessing layout.
- Keep `res/mipmap-anydpi-v26`. The lint `ObsoleteSdkInt` suggestion to remove the version
  qualifier is misleading here; moving those resources breaks resource linking.
- `ComponentActivity.dispatchKeyEvent` is restricted API. Handle foreground volume keys through
  `onKeyDown`/`onKeyUp`, including held-key/release behavior.

## Recorded hardware behavior and remaining risks

The recorded baseline is COROS APEX 4, COROS app 4.10.8 (`com.yf.smart.coros.dist`), and a
2608×1200 Xiaomi phone running HyperOS 3 / Android 16. A debug build logged each input:

- COROS buttons arrived as media-button down/up pairs a few milliseconds apart: `MEDIA_PREVIOUS`,
  `MEDIA_NEXT`, and `MEDIA_PAUSE` while the session was playing. They did not arrive as transport
  calls such as `skipToNext()`. Arrival-to-metadata-update time was at most 8 ms in that record;
  this does not measure end-to-end watch latency.
- Each watch volume press arrived as `MediaController.setVolumeTo(10)`, in bursts of 1–6 calls
  no more than about 90 ms apart. Actual phone volume stayed unchanged. The direct-phone-volume
  fallback was unnecessary and remains off by default.
- COROS selected Courtside consistently with no other media player active. Selection among
  competing sessions is still the companion app's decision.
- Default HyperOS battery settings froze the background app even with its foreground service:
  timers stopped and queued input was applied on returning to the activity. Phone-volume keys
  in other apps also reached the session; relative volume handling now forwards those inputs
  to real media volume instead of arming reset when no screen is started.

For field testing, apply the [COROS setup](README.md#example-coros-apex-4), including unrestricted
battery/background settings, and avoid clearing Courtside from recents. HyperOS needs separate
*Show on Lock screen* consent for show-when-locked behavior.

Mute on the watch has not been tested. Android drops remote-session mute commands; reset would
require a companion to translate mute into a useful absolute call such as `setVolumeTo(0)`.
Other devices may use direct phone-volume changes, omit metadata, or interpret “previous” as
restart-first. A headset's automatic “play” on connection is an undo input. Test each path
separately rather than assuming pairing means compatibility.

Android 16 can ignore fixed orientation on large screens, unlike the target phone. Android 17
restrictions on volume changes from apps without a visible activity or foreground service may
affect a companion app's volume calls; this newer-version path is not field-validated here.
See the [compatibility guide](docs/watch-compatibility.md) for source references, the provisional
matrix, and a structured community test checklist. Do not turn expected standard-media-control
compatibility into an unqualified claim that every watch, headset, or phone works.

## Licensing changes

First-party code and documentation use the [MIT License](LICENSE), with copyright holder
**Bin Jin <bjin@protonmail.com>**. Preserve the B612 OFL notices and other third-party notices,
including those in the Gradle wrapper; do not relabel them as first-party MIT material.
