# LensAssist

Google replaced the Assistant with Gemini and took the "Search screen" feature with it.
LensAssist puts it back, and does nothing else.

Register it as the device assistance app. When you long-press Home (3-button nav) or
swipe in from a bottom corner (gesture nav), the system hands LensAssist a screenshot of
whatever you were looking at. LensAssist writes it to a cache file, hands the file to the
Google app — where Lens lives — and disappears. No visible UI of its own, no network
permission, no analytics, no third-party dependencies beyond AndroidX core and appcompat.

Target device for this build: Samsung Galaxy S20 (SM-G980F), Android 13 / One UI 5.1.
Debug sideload only; there is no Play Store release.

## Build

Requires JDK 17+ and the Android SDK (`ANDROID_HOME` set, or a `local.properties` with
`sdk.dir=`).

```bash
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

CI (`.github/workflows/build.yml`) runs the same command on every push and uploads the
APK as a build artifact, so you can grab a built APK from the Actions run instead of
setting up a local Android toolchain.

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk

# or, from a CI artifact
adb install -r ~/Downloads/lensassist-debug-apk/app-debug.apk
```

## Device setup

Two things have to be true: LensAssist must hold the assistant role, and the assistant's
screenshot permission must be on. The second one is the one people miss — without it the
system simply never sends an image, and LensAssist will tell you so rather than opening
Lens.

**One UI 5.1 (Android 13):**

1. `Settings → Apps → Choose default apps → Digital assistant app`
2. Tap `Device assistance app` and pick **LensAssist**.
3. Back on the same screen, open the settings gear next to the assistance app and enable
   the screen-context and screenshot toggles. Samsung's wording drifts between builds —
   look for "Use screen context", "Use screenshot", or "Allow access to screen content".
4. Make sure the gesture is actually mapped: `Settings → Display → Navigation bar`. With
   3-button nav, long-press Home invokes the assistant; with gesture nav, swipe diagonally
   in from a bottom corner.

Or just open LensAssist and press **Open assistant settings** — it walks the fallback
chain (`VOICE_INPUT_SETTINGS` → default-apps → Settings) and lands you as close as the
device allows. `ROLE_ASSISTANT` cannot be requested programmatically, so a human has to
make the selection either way.

The launcher screen reports whether the role is held. Re-open it after changing settings;
it refreshes on resume.

## Checking it works

Press **Send test image to Lens** on the launcher screen. It pushes the bundled test card
(`res/raw/sample_card.png`) through the exact same code path the assist gesture uses, so
if the button opens Lens with the card loaded, the share path is good and any remaining
problem is in the assistant registration, not the Lens hand-off.

The test card carries English body text, monospaced strings, German and Spanish blocks,
and a deliberately small final paragraph — enough to exercise the Text and Translate tabs
and to show you where OCR starts to struggle.

## Diagnostics

The launcher screen reports three things that can only be answered on a real device:

- **The ACTION_SEND handler the Google app currently exposes**, as `package/class`.
  LensAssist always targets the package and lets the system resolve the activity —
  Google renames these between releases, so a hardcoded class name is a time bomb. If
  this line says "none found", the Google app has stopped accepting shared images and
  LensAssist falls back to the share sheet.
- **The size and config of the last screenshot the system delivered.** The assist
  screenshot is not full resolution; the system downscales it. This is the number that
  decides whether Lens's Text tab can read fine print.
- **The last launch route** — which component got the image, or whether it went to the
  chooser.

There is also a **launch path** toggle:

- `context.startActivity` — Lens opens in its own task.
- `startAssistantActivity` — launch into the assistant stack.

Both are legal from a shown assist session, and which one leaves Back returning to your
original app is device behavior, not documented API. Try both on your device and keep the
one that behaves. `context.startActivity` is the default.

## What the Google app actually accepts

Verify on your own device rather than trusting any writeup, including this one — Google
changes this without notice:

```bash
# What the Google app exposes for a shared PNG
adb shell cmd package query-activities -a android.intent.action.SEND -t image/png \
  | grep -i googlequicksearchbox

# Everything the Google app declares for ACTION_SEND
adb shell dumpsys package com.google.android.googlequicksearchbox \
  | grep -B2 -A8 'android.intent.action.SEND'

# Watch LensAssist while you invoke it
adb logcat -s LensAssist
```

The launcher screen's diagnostics line reports the same resolution result from inside the
app, which is the one that matters, since it is subject to the same `<queries>` package
visibility filtering the app runs under.

If the Google app resolves but lands you in plain Google Search rather than Lens, that is
a routing decision inside the Google app, not something the intent can force. Report what
the diagnostics line says and the intent can be retargeted.

## Failure modes

| What you see | Why |
| --- | --- |
| "the screenshot / screen-context toggle is off" | The system showed the session without `SHOW_WITH_SCREENSHOT`. Turn the toggle on (step 3 above). LensAssist detects this immediately instead of waiting out the timeout. |
| "this app blocks screen capture (FLAG_SECURE)" | Banking apps, Netflix, password managers. The system delivers a null bitmap and there is nothing to send. Working as intended — no crash. |
| "the system did not deliver a screenshot in time" | The 1.5 s backstop fired. Rare; usually means the system was busy. |
| "no activity accepted the image" | Neither the Google app nor the share sheet took it. Check the diagnostics line. |
| "the launch was refused" | A background-activity-launch rejection. Try the other launch path. |

## Layout

```
LensVoiceInteractionService   assistant registration; holds BIND_VOICE_INTERACTION
LensSessionService            hands the system a session
LensSession                   the whole feature: screenshot in, Lens out, no UI
LensLauncher                  builds the ACTION_SEND intent, resolves, falls back to chooser
ScreenshotCache               PNG staging in cacheDir/screens, exposed via FileProvider
StubRecognitionService        declines everything; exists so the picker lists the app
Diagnostics                   what the last invocation did
MainActivity                  the one screen
```

## Deliberate choices

- **The session never requests `SHOW_WITH_SCREENSHOT`.** That flag is the system's, set
  from the user's assist toggles and delivered to `onShow`. The session reads it and
  fails fast when it is absent.
- **The assist bitmap is copied out of HARDWARE config before compressing.**
  `Bitmap.compress()` throws on a hardware-backed bitmap, which is how the system
  normally delivers it.
- **`hide()` comes after the launch, never before.** Hiding first drops the foreground
  standing that makes starting an activity legal.
- **The cache keeps the last two files, not one.** Lens may still be reading the previous
  URI when the next invocation writes.
- **`setUiEnabled(false)` plus an empty transparent content view.** Belt and braces, so
  nothing of LensAssist can flash on screen or land in the screenshot.
- **Package-targeted, never class-targeted.** `setPackage(...)` plus resolution, so a
  Google app rename degrades to the share sheet instead of crashing.

## Out of scope

OCR, translation, hotword detection, a results UI of its own. Lens already does all of
that; the entire point of this app is to get out of the way.
