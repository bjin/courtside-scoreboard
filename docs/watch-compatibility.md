# Early smartwatch and smart-band compatibility

> **Status: provisional and mostly unverified.** The only external watch validation currently recorded is a COROS APEX 4 with a Xiaomi HyperOS 3 / Android 16 phone. The matrix below combines vendor documentation with clearly marked inferences; it is not a compatibility guarantee.

This app needs four useful inputs or outputs:

1. **Left point:** previous/rewind/skip-back media action.
2. **Right point:** next/fast-forward/skip-forward media action.
3. **Undo:** play, pause, play/pause, or headset-hook action.
4. **Reset and display:** two distinct volume inputs reset the score; the watch may display the score as media metadata.

The groups mean:

- **A — likely to work fine:** vendor documentation exposes phone playback and phone volume. The score title is still a separate, model-dependent question.
- **B — probably partial:** phone playback is documented, but a previous-track quirk, volume route, supported-player list, or metadata display is uncertain.
- **C — probably will not work:** the documented path is tied to an incompatible phone/service, or there is no documented media-control surface.

Representative models are listed; a brand name does not make every model compatible.

## Provisional matrix — inference

`K` = Android media-button `KeyEvent`; `T` = Android `MediaSession` transport callback; `S` = session `VolumeProvider`; `P` = direct phone `STREAM_MUSIC` volume. The route is normally hidden by the watch's companion app, so **unknown means unknown**, not a negative result.

| Group | Brand or representative devices | Likely Android-facing route | Expected result and remaining risk |
|---|---|---|---|
| **A** | **COROS** — APEX 2/2 Pro/4, PACE 3/4/Pro, NOMAD, VERTIX 2/2S | **Observed on APEX 4:** `K + S + metadata`. Other models are `[INFERENCE]` likely to use the same COROS phone-media path. | **Observed APEX 4:** left/right/undo, volume reset, and score title all worked. Other models remain `[INFERENCE]`; DURA is a cycling computer, not a watch. |
| **A** | **Amazfit** — Balance, Balance 2, Band 7 | `[INFERENCE]` `K` or `T`; `S`/`P` unknown. | `[INFERENCE]` Likely full controls because the documented phone music widget includes track navigation and volume. Song-information display is documented for some models, but Courtside's title has not been checked. |
| **A** | **Xiaomi** — Smart Band 8 Pro/11, Watch S3/S5, Redmi Watch 6 | `[INFERENCE]` Companion phone-music widget using `K` or `T`; `S`/`P` unknown. | `[INFERENCE]` Likely full controls on the listed representatives; their documentation includes phone play/pause, track navigation, and volume. Title rendering varies by model. |
| **B** | **Xiaomi Smart Band 9** | `[INFERENCE]` Phone-player control, probably via `K` or `T`; volume route unknown. | `[INFERENCE]` Playback and artist-name display are documented, but the exact control set and phone-volume behavior are not. Treat reset and score-title support as unverified. |
| **A** | **Huawei** — Watch GT 5 and models covered by the linked Health/Band documentation | `[INFERENCE]` Health phone-music control using `K` or `T`; volume route unknown. | `[INFERENCE]` Likely full controls on supported Android models. Huawei limits supported phone music apps and the exact model coverage needs field testing. |
| **A** | **Samsung** — Galaxy Fit3 and selected Galaxy Watch models | `[INFERENCE]` Android media controller or media keys; volume route unknown. | `[INFERENCE]` Fit3's documented phone controls match the app's transport mapping and include volume. Watch model, title view, and whether volume reaches `S` or `P` need testing. |
| **B** | **Garmin** — vívoactive 5, Venu 3, fēnix 8, Forerunner 965, selected vívosmart models | `[INFERENCE]` `K` or `T`; `S` versus `P` unknown. | `[INFERENCE]` Playback should be usable. Garmin documents source-dependent controls and “previous once = restart, previous twice = previous”, so one logical left point may require two watch actions. Volume and title are model-dependent. |
| **B** | **Wear OS generally** — Pixel Watch, Galaxy Watch 4+, TicWatch, OnePlus Watch 2, OPPO Watch X2 | `[INFERENCE]` Standard Wear OS media UI through `T` or `K`; volume may be watch-local, `S`, or `P`. | `[INFERENCE]` Transport buttons are plausible, but OEM media UI and phone-volume routing differ. A Wear OS media screen is not proof that phone session volume reaches Courtside. |
| **B** | **Polar** — Grit X/X2, Ignite 2/3, Pacer, Vantage, Street X families listed by Polar | `[INFERENCE]` Polar Flow / Android media control using `K` or `T`; volume route unknown. | `[INFERENCE]` Likely partial control. Polar notes that some phone apps do not support previous/next, and control views differ by model. Title display is unknown. |
| **B** | **Suunto** — Suunto 5/7, Race/Race 2, Run, Ocean, Vertical | `[INFERENCE]` Companion media control using `K` or `T`; volume route unknown. | `[INFERENCE]` Phone playback is documented. Volume is explicit on some older models but not every current model; title display is unknown. |
| **B** | **HONOR** — selected HONOR Watch and Band models | `[INFERENCE]` HONOR Health phone-music control using `K` or `T`; volume route unknown. | `[INFERENCE]` Probably usable on Android with a supported music app. HONOR's supported-app and model lists are restrictive; title and exact transport are unknown. |
| **B** | **Fitbit classic** — Blaze, Versa/Versa 2, Sense/Versa 3 families | `[INFERENCE]` Classic Bluetooth/AVRCP-like media keys, likely becoming `K`; volume and metadata unknown. | `[INFERENCE]` Some phone playback may work, but previous and volume behavior is model-specific. Do not generalize this result to current Fitbit service-specific models. |
| **B** | **Other proprietary phone-media watches** — original OnePlus Watch and realme Watch | `[INFERENCE]` Companion-specific bridge; likely `K`/`T`, but protocol unknown. | `[INFERENCE]` Vendor documentation shows phone music controls, so partial control is plausible. Exact events, volume route, and title behavior are unverified. |
| **C** | **Apple Watch used with an Android phone** | Apple media ecosystem; no supported Android pairing path. | `[INFERENCE]` Do not expect Courtside control. Apple documents Now Playing for Apple devices and requires a compatible iPhone for Apple Watch setup. |
| **C** | **Current Fitbit models with service-specific controls** — e.g. Charge 6, Sense 2, Versa 4 | Fitbit/YouTube Music or other service-specific integration, not a documented generic Android player path. | `[INFERENCE]` Probably will not control Courtside's framework `MediaSession`. Verify only if a model explicitly documents generic phone media control. |
| **C** | **Screenless or health-only trackers/rings**, and any model without a documented phone-media widget | No usable watch-side media-control surface is documented. | `[INFERENCE]` Probably cannot send the required action or show the score. This is a category rule, not an exhaustive product list. |

### Vendor facts behind the matrix

These are documented capabilities, not proof that a particular model sends the events Courtside needs.

| Ecosystem | Documented fact | Primary source |
|---|---|---|
| COROS | Media Control controls paired-phone play/pause, previous/next, volume, and shows the playing title. The page lists APEX 2/2 Pro/4, PACE 3/4/Pro, NOMAD, VERTIX 2/2S, and DURA. | [COROS Media Control](https://support.coros.com/hc/en-us/articles/43767735906068-Media-Control) |
| Garmin | Manuals for selected watches document a phone music provider, source-dependent controls, volume, and the previous-track restart/twice-previous behavior. | [vívoactive 5](https://www8.garmin.com/manuals/webhelp/GUID-5D183A14-BB43-4A9B-B441-5F824214CE40/EN-US/GUID-8D7F288E-0E62-4DE7-801C-E11948644A96.html), [fēnix 8](https://www8.garmin.com/manuals/webhelp/GUID-EECCAC99-90D6-4AB1-9A3A-EC433D3365E2/EN-US/GUID-8D7F288E-0E62-4DE7-801C-E11948644A96.html) |
| Huawei | Health documentation describes Android phone music control: pause, switch tracks, and adjust phone volume. Separate Band and GT 5 pages describe the phone-music setting and Android compatibility. | [watch music control](https://consumer.huawei.com/en/support/content/en-us00754824/), [Band music control](https://consumer.huawei.com/en/support/content/en-us15771838/), [GT 5 compatibility](https://consumer.huawei.com/en/support/content/en-us16016096/) |
| Xiaomi | Representative FAQs document phone play/pause, track navigation, volume, music-source selection, and in some cases artist names. | [Smart Band 8 Pro](https://www.mi.com/uk/support/faq/details/KA-64019/), [Smart Band 9](https://www.mi.com/global/support/faq/details/KA-230035/), [Smart Band 11](https://www.mi.com/uk/support/faq/details/KA-731992/), [Watch S3](https://www.mi.com/global/support/faq/details/KA-92668/), [Watch S5](https://www.mi.com/global/support/faq/details/KA-703529/), [Redmi Watch 6](https://www.mi.com/global/support/faq/details/KA-694585/) |
| Amazfit | Balance, Balance 2, and Band 7 documentation describes a phone music widget with play/pause, previous/next, volume, and song information. | [Balance](https://support.amazfit.com/en/amazfit_balance/docs/NQLIdofCvoRfq6xEewacMSILnwd), [Balance 2](https://support.amazfit.com/en/amazfit_balance_2/docs/YSFOdGnoOoQM4txPbvccNgT5nOc), [Band 7](https://support.amazfit.com/en/amazfit_band_7/docs/ISyld4woooDDVRxFsipcp29Nn8b) |
| Samsung | Samsung documents phone-mode music controls for selected Galaxy Watch models. The Fit3 guide documents phone pause, previous/next, and a volume slider. | [Galaxy Watch/Fit music controls](https://www.samsung.com/us/support/answer/ANS10002898/), [Galaxy Fit3](https://www.samsung.com/us/support/answer/ANS10004900/) |
| Wear OS | Google documents a standard Wear OS media-control layout and generic phone music control, but the design guide does not certify every OEM's phone routing. | [Wear media controls](https://developer.android.com/design/ui/wear/guides/patterns/media/controls), [Wear OS music](https://support.google.com/wearos/answer/6056802?co=GENIE.Platform%3DAndroid&hl=en), [Google Fit Wear OS controls](https://support.google.com/fit/answer/10267301?hl=en) |
| Polar | Polar lists supported watch families and documents phone music controls, while noting that some phone apps do not support previous/next and that views vary by model. | [Polar music controls](https://support.polar.com/us-en/music-controls) |
| Suunto | Suunto 5 and 7 document phone playback, track navigation, and volume. Current Race/Run documentation documents phone playback, but does not state volume for every model. | [Suunto 5](https://www.suunto.com/Support/Product-support/suunto_5/suunto_5/features/media-controls/), [Suunto 7](https://www.suunto.com/Support/Product-support/suunto_7/suunto_7/music/control-music-from-your-wrist/), [Suunto Race 2](https://www.suunto.com/Support/Product-support/suunto_race_2/suunto_race_2/widgets/media-controls/), [Suunto Run](https://www.suunto.com/en-ca/Support/Product-support/suunto_run/suunto_run/widgets/media-player/) |
| Fitbit | Google documents classic Bluetooth phone-media control for older Fitbit families and separately documents service-specific controls on current models. | [Fitbit music controls](https://support.google.com/googlehealth/answer/14237218?hl=en) |
| HONOR | HONOR Health documentation describes phone play/pause, track switching, and volume, with Android and supported-app requirements. | [HONOR phone music](https://www.honor.com/uk/support/content/en-us15856014/), [HONOR music control](https://www.honor.com/global/support/content/en-us15868270/) |
| Apple | Apple documents Now Playing controls for Apple devices and requires a compatible iPhone for Apple Watch setup. | [Apple Watch Now Playing](https://support.apple.com/en-euro/guide/watch/apd4ea5db227/watchos), [Apple Watch compatibility](https://support.apple.com/en-us/118490) |
| OnePlus / realme / OPPO | The original OnePlus Watch manual and realme Watch page document phone music controls; OPPO Watch X2 documents a crown for music-volume adjustment. Exact Android event paths are not documented. | [OnePlus Watch manual](https://service.oneplus.com/content/dam/support/user-manuals/common/OnePlus_Watch_User_Manual_EN.pdf), [realme Watch](https://www.realme.com/in/realme-watch), [OPPO Watch X2](https://www.oppo.com/au/accessories/watch-x2/) |

## What Courtside actually implements — facts from the current source

The app does **not** connect directly to a watch, Bluetooth radio, AVRCP stack, Wear OS data layer, or vendor cloud API. Android or the companion app must first translate the watch action into one of the interfaces below.

| Interface | Current behavior |
|---|---|
| **Raw media buttons** | `SessionCallback` reads Android `KeyEvent`s. Initial key-down only is accepted; key-up and repeats are consumed. Previous, rewind, and skip-back map to left; next, fast-forward, and skip-forward map to right; play, pause, play/pause, and headset hook map to undo. Stop and unknown keys are ignored. See [`SessionCallback.kt`](../app/src/main/java/io/github/bjin/courtside/media/SessionCallback.kt) and [`RemoteCommand.kt`](../app/src/main/java/io/github/bjin/courtside/core/RemoteCommand.kt). |
| **MediaSession transport callbacks** | `onSkipToPrevious`, `onSkipToNext`, `onPlay`, `onPause`, `onRewind`, `onFastForward`, and sufficiently large relative `onSeekTo` calls are also mapped. The advertised action set is only play/pause and previous/next; a controller may nevertheless call other implemented callbacks. See [`SessionCallback.kt`](../app/src/main/java/io/github/bjin/courtside/media/SessionCallback.kt) and [`ScoreboardService.kt`](../app/src/main/java/io/github/bjin/courtside/media/ScoreboardService.kt). |
| **Metadata** | The session title is updated to the score format `● L : R ○` (serve mark configurable), with status in the other metadata fields. No artwork, queue, or browse service is provided. A watch may ignore or simplify this metadata. See [`ScoreboardService.kt`](../app/src/main/java/io/github/bjin/courtside/media/ScoreboardService.kt). |
| **Remote session volume (`S`)** | When volume reset is enabled (default), the session exposes an absolute `VolumeProvider` with range `0..10` and midpoint `5`. A changed `setVolumeTo()` call counts as an input and springs back to midpoint, including while the session is alive without a screen. Relative steps count while a screen is started; when no screen is started, relative steps are sent to the real phone media stream instead. See [`VolumeInputs.kt`](../app/src/main/java/io/github/bjin/courtside/media/VolumeInputs.kt). |
| **Phone-volume fallback (`P`)** | Optional and off by default. `PhoneVolumeWatcher` observes real `STREAM_MUSIC` volume/mute changes while a screen is started, counts them, and restores the previous value on a best-effort basis. It cannot identify whether another app, the user, or a watch caused the change. See [`VolumeInputs.kt`](../app/src/main/java/io/github/bjin/courtside/media/VolumeInputs.kt) and [`Stores.kt`](../app/src/main/java/io/github/bjin/courtside/data/Stores.kt). |
| **Phone hardware volume keys** | While volume reset is enabled and the activity is in front, `VOLUME_UP` and `VOLUME_DOWN` are consumed as reset inputs rather than changing the phone volume. See [`MainActivity.kt`](../app/src/main/java/io/github/bjin/courtside/ui/MainActivity.kt). |
| **Reset timing** | Two accepted inputs confirm reset only when the second is at least 300 ms after the previous input and strictly within 5 s. Held/repeated relative volume callbacks within 600 ms are folded by the repeat filter. A fast absolute-volume burst therefore arms the guard but does not itself confirm reset. See [`ResetGuard.kt`](../app/src/main/java/io/github/bjin/courtside/core/ResetGuard.kt) and [`VolumeStepFilter.kt`](../app/src/main/java/io/github/bjin/courtside/core/VolumeStepFilter.kt). |
| **Media-button claim** | A setting-enabled 0.3 s silent media pulse makes Courtside a candidate for Android's last media-button target. It is not a vendor-specific routing guarantee. The foreground service is media-playback only and can still be frozen or force-stopped by an OEM. See [`SilentPulse.kt`](../app/src/main/java/io/github/bjin/courtside/media/SilentPulse.kt), [`ScoreboardService.kt`](../app/src/main/java/io/github/bjin/courtside/media/ScoreboardService.kt), and [`AndroidManifest.xml`](../app/src/main/AndroidManifest.xml). |

Android's [`VolumeProvider`](https://developer.android.com/reference/android/media/VolumeProvider) API supports the session-volume path; it does not make every watch's volume control target the session. The companion app's choice between media keys, transport callbacks, session volume, phone volume, and a private service determines the real result.

### Important interpretation rules

- A watch saying “previous” does not guarantee one app event. Garmin-style “restart, then previous” behavior can require two watch actions. Conversely, if both presses are forwarded as separate previous events, Courtside correctly counts both as two left points.
- A watch volume slider can change the watch's own output, the phone's real media stream, or the Courtside session. Only the last two paths can be useful, and the direct-phone path requires the optional fallback when the app is in front.
- A visible song title is not guaranteed by a working control path. The title is Android media metadata; the watch decides whether and how to render it.
- Play/pause is deliberately **undo**, not playback. A watch that suppresses the first press while resolving a double-press gesture may therefore feel different from one that forwards every media event.
- A Bluetooth pairing alone is not sufficient. The phone must route the action to Courtside's active session, and no competing media session should take the top position.

## Recorded baseline

This is the existing field record, not a claim about other brands:

- Phone: Xiaomi HyperOS 3, Android 16, 2608×1200; COROS app 4.10.8 (`com.yf.smart.coros.dist`).
- Watch: COROS APEX 4. COROS emitted paired media key down/up events for previous, next, and pause; volume arrived as several absolute `setVolumeTo()` calls close together.
- The phone's real media volume remained unchanged. Courtside stayed the selected media session when no other player was active.
- HyperOS default battery handling froze the background app until the required unrestricted/background setting was applied.

The broader COROS setup and field notes are in the [README](../README.md). No other watch or band has been hardware-validated for this matrix.

## Community test report

Ask testers to record the exact watch model, firmware, companion-app version, phone model/Android version, and whether the companion app has notification/background access.

1. Start Courtside with no other media player active. Confirm the watch can see a phone-media control screen.
2. Press **next** once and **previous** once. Check whether each produces exactly one point. If previous restarts first, repeat it and record the number of physical presses needed.
3. Press play/pause once. It should undo the last point, not start audio.
4. Make two separate volume actions about one second apart. Check for reset, no audible output, and no lasting phone-volume change. If the watch changes the phone's real media volume instead of the session, enable the optional phone-volume fallback and repeat carefully.
5. Check whether the watch shows the live score title, a generic player title, stale metadata, or nothing.
6. Open **Menu → Recent remote commands** after each trial. Report the displayed command, timing, caller package when available, and whether the phone volume changed.

A report saying “control works” is incomplete unless it separately records transport buttons, reset volume, previous-track press count, and title display.
