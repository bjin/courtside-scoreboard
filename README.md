# Courtside

[![CI](https://github.com/bjin/courtside-scoreboard/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/bjin/courtside-scoreboard/actions/workflows/ci.yml)
[![Latest release, including pre-releases](https://img.shields.io/github/v/release/bjin/courtside-scoreboard?include_prereleases)](https://github.com/bjin/courtside-scoreboard/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Turn an Android phone into a badminton scoreboard you can control from your wrist.**

Place your phone beside the court in landscape orientation. Courtside shows two large scores,
with digits designed to be readable from about 6 metres away. Add points by tapping the screen,
using your watch's music controls, or pressing buttons on a Bluetooth media remote or headset.
No special watch app is needed.

## What it looks like

| Wide phone | 16:9 phone | 20:9 phone |
|---|---|---|
| ![Dark scoreboard on a wide phone](docs/screenshots/2608x1200-dark.png) | ![Dark scoreboard at 16:9](docs/screenshots/1920x1080-dark.png) | ![Dark scoreboard at 20:9](docs/screenshots/2400x1080-dark.png) |
| ![Light scoreboard on a wide phone](docs/screenshots/2608x1200-light.png) | ![Light scoreboard at 16:9](docs/screenshots/1920x1080-light.png) | ![Light scoreboard at 20:9](docs/screenshots/2400x1080-light.png) |

The blue side won the last point and serves next. You can switch between light and dark backgrounds.

## How a watch controls the score

Courtside makes itself look like a **music player** to Android. Your watch's normal phone-music
controls then become scoreboard buttons:

| Music button | Scoreboard action |
|---|---|
| Previous track | Add a point on the **left** |
| Next track | Add a point on the **right** |
| Play / Pause | **Undo** the last point |
| Volume twice, with a pause between presses | **Reset** both scores to zero |

Instead of a song title, a compatible watch shows the score—for example **● 11 : 9 ○**.
The filled circle marks the serving side. Courtside does not play music; it may briefly play
silence to receive media buttons. Avoid playing music on the same phone during a match,
or the buttons may control that player instead.

Bluetooth media remotes and headsets can use the same buttons. Available controls, volume reset,
and score display depend on the device.

### Example: COROS APEX 4

1. Pair the watch through the **COROS phone app**, then grant that app **notification access**
   in Android settings so its Media Control feature can see Courtside.
2. Open Courtside on the phone and place it beside the court.
3. On the watch, long-press **Back** to open Toolbox, then choose **Media Control** (“手机音频”).
   During an activity, you can also reach it through **Switch View**.
4. Use **Previous** for a left point, **Next** for a right point, and **Play/Pause** to undo.
   The watch should show the score as the song title.
5. To reset, press volume once to arm it, pause at least **0.3 seconds**, then press again
   within **5 seconds**. A held button or continuous slider drag does not confirm a reset.

**Tested with COROS APEX 4 and a Xiaomi phone running Android 16 / HyperOS 3.** On HyperOS,
set battery restrictions to **No restrictions** for both Courtside and COROS, allow COROS
**Autostart**, and do not clear Courtside from recent apps during a match.

Other watches and Bluetooth controllers using standard Android media controls should work in
many cases, but are not all tested. See the [compatibility guide](docs/watch-compatibility.md)
for known results and model-specific caveats.

## Use the screen directly

You do not need a watch or remote:

- **Tap either score** to add a point. Accidental swipes and touches near controls or screen edges are ignored.
- **UNDO** takes back the last point; repeat to undo earlier points.
- **Hold RESET** until the screen fills to clear both scores and the undo history. Reset cannot be undone.
- **SWAP** moves the teams and their scores to the opposite sides when you change ends.
- The **top-left button** changes the background; the **top-right menu** contains settings and language selection.
- Press **Back twice** to leave. Scores and undo history are saved automatically.

The screen stays on by default. English, Simplified and Traditional Chinese, Japanese, Korean,
German, French, and Spanish are available in the menu.

## Install

Requires **Android 8.0 or newer**. Visit [Releases](https://github.com/bjin/courtside-scoreboard/releases)
and download `courtside-scoreboard-<version>-release.apk` from your chosen release, then open it on your phone to install.
Android may ask you to allow installation from that source. Pre-releases may be experimental.
If no release is available yet, see [how to build an APK](DEVELOPMENT.md#build-and-install).

Updating with the same signing key preserves app data. An older development build may need to
be uninstalled first, which clears its saved scores and settings;
[signing details](DEVELOPMENT.md#release-signing).

## If remote buttons do not work

- Stop other music players, reopen Courtside, and enable **Receive media buttons** in its menu.
- Check your watch companion app's notification access and the phone's background/battery restrictions.
- If volume changes the phone's loudness instead of resetting, try **Also use phone media-volume changes**
  with Courtside in front, or use the on-screen RESET button.
- Check **Recent remote commands** in the menu to see which inputs arrived. Test your controls before a match.

More [compatibility notes](docs/watch-compatibility.md) and
[technical troubleshooting](DEVELOPMENT.md#emulator-commands-and-technical-debugging) are available.

## Privacy and license

No network access, accounts, ads, or analytics.

First-party code and documentation are licensed under [MIT](LICENSE).
Copyright (c) 2026 Bin Jin <bjin@protonmail.com>.

The bundled **B612 Bold** digit font retains its [SIL Open Font License 1.1](app/src/main/assets/licenses/B612-OFL.txt).
Other third-party components retain their own licenses. License notices are also available in the app's menu.

For builds, tests, signing, versioning, and release automation, see [DEVELOPMENT.md](DEVELOPMENT.md).
