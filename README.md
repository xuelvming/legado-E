# HunReader

A personal Android e-reader fork of [Legado-E](https://github.com/Luoyacheng/legado-E),
itself based on [Legado](https://github.com/gedoor/legado).
Modified by hunterxue on 2026-10-04. This is not an official upstream build.
Original copyright notices and the [GPLv3 license](LICENSE) are retained.

| Build | Application ID | Visible name |
| --- | --- | --- |
| Release | `io.github.hunterxue.hunreader` | HunReader |
| Debug | `io.github.hunterxue.hunreader.debug` | HunReader Debug |

The source namespace remains `io.legado.app`; it is not the installation ID.
HunReader installs separately from upstream. No migration is included.

## Personal-service policy

| Surface | HunReader behavior |
| --- | --- |
| App updater | Removed, including launch checks, settings, download dialog and upstream release clients |
| Firebase / Google Services plugin | Removed, with upstream Firebase configuration and analytics/performance dependencies |
| Shared upstream signing key | Removed from the current tree; never used by HunReader builds (still present in old Git history) |
| Publisher workflows | Removed; CI only builds debug artifacts and corresponding source, without release secrets or publishing |
| Upstream signer classification | Removed; your certificate is not compared with upstream fingerprints |
| Book/RSS, HTTP TTS, dictionary and upload presets | Removed, including startup injection and restore-default actions |
| Online TTS | Retained: add, edit or import your own configuration, including Azure/Foundry-backed HTTP TTS |
| WebDAV | Retained, but an explicit endpoint and credentials are required; default subdirectory is `hunreader` |
| Android automatic cloud backup | Disabled; user-initiated local/WebDAV backup remains available |
| External ReaderProvider API | Disabled; user-initiated file sharing still uses `${applicationId}.fileProvider` |
| Import links | `hunreader://` / `hunreader-debug://`; no upstream Android URI registrations |
| Branding / About | HunReader name, local version information and upstream credits; no store-rating, publisher promotion or upstream update log |
| Reading / networking | Local reading, sources you import, browser, online audio, sync and optional local web server remain |
| Cronet / TLS | No eager Cronet download unless you enable its setting; optional Google-hosted native download and local legacy GMS TLS support remain |

Offline chapter rules, reading layouts, themes and keyboard helpers are retained.
Nothing imports your personal Azure credentials into source or CI. In the reading
screen's read-aloud engine settings, use **Add** or **Import local** to load your
existing HTTP TTS configuration. The list intentionally starts empty; system TTS
is still available. Existing user-created TTS entries are not reset on upgrades.
Protect exported configurations and backups: they may contain credentials.
See [privacy policy](app/src/main/assets/privacyPolicy.md) and [API status](api.md).

## Automatic dictionary lookup

In a book's reading-interface settings:

1. Choose **Text-selection app** and select your installed dictionary action,
   such as Eudic. The list includes apps supporting Android's text-selection
   integration, not every installed app.
2. Enable **Open app automatically after selecting text**. It is off by default.
3. Long-press a word, then lift your finger to send it directly to the app.
   To look up a phrase, keep holding and drag onto the next word **before
   releasing**. Touching any letter in `down` after holding `put` selects
   the complete phrase `put down`.

Long-press dragging snaps to whole words in both directions. Drag back toward
the first word to shrink the selection. Spaces and sentence punctuation at
the moving edge are excluded; spaces and punctuation inside the phrase are
preserved. Words can span wrapped lines. This uses the reader's language-aware
word boundaries, not phrase prediction.

Word snapping also applies with automatic opening off. The separate selection
handles remain character-precise for selecting partial words or punctuation.

Successful launches clear the selection. Returning from the dictionary does not
trigger another lookup. The dictionary controls whether it opens a floating
window or a full screen.

Turn automatic opening off to use the normal copy, bookmark, built-in dictionary,
and share menu; your app choice is remembered. Choosing **None** also disables
automatic opening. The separate **Expand text selection menu** setting continues
to control the normal menu's layout.

This feature requires Android 6.0 or later. If a selected app is removed or can
no longer handle text, an error is shown and the selection menu remains
available. Reselect an app or choose None in reading settings. If your Eudic
version is not listed, its custom dictionary/share protocols are not supported
by this integration. Selected text is sent only to the app you explicitly choose.

## Development

See [Developing HunReader](DEVELOPING.md) for:

- JDK, Android SDK, Platform-Tools, and ADB installation and locations
- `JAVA_HOME`, `ANDROID_HOME`, and user `PATH` configuration
- debug builds and JVM/device tests
- private release-key generation, release builds, and signature verification
- installing and troubleshooting APKs on a physical Android device with ADB

Quick start after the prerequisites are configured:

```powershell
.\gradlew.bat :app:testAppDebugUnitTest :app:assembleAppDebug
adb install -r (Get-ChildItem ".\app\build\outputs\apk\app\debug\*.apk" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1).FullName
```

Debug builds and CI artifacts use a disposable debug signer. Public or long-lived
installations must use the private release process documented in
[DEVELOPING.md](DEVELOPING.md).

## Distribution

For every APK you share, provide the exact corresponding source and build scripts
under GPLv3, keep license/attribution notices, and identify your modifications.
Build from a clean committed revision and archive that revision (not your whole
working directory). Never include signing credentials or personal configuration.
There is no in-app update service or automated public release in this fork.
