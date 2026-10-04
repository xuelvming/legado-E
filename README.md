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

## Build

Install JDK 17 and Android SDK platform 36 / build tools, and configure
`ANDROID_HOME` or an ignored `local.properties` with `sdk.dir`.
Use the checked-in Gradle wrapper; do not change Gradle versions to bypass errors.

```powershell
.\gradlew.bat :app:assembleAppDebug
.\gradlew.bat :app:testAppDebugUnitTest
```

Debug builds use the normal local Android debug key, never the private release key.
CI debug APKs are disposable test builds, not a stable distribution channel.

### Private release signing (Windows)

Run once:

```powershell
.\scripts\New-HunReaderSigningKey.ps1
```

The script generates a 4096-bit RSA PKCS12 key with about 50 years of validity.
It refuses to overwrite existing material or generate it inside the repository,
and restricts the directory to the current Windows user and SYSTEM.

Files under `%USERPROFILE%\.hunreader`:

* `hunreader-release.p12`: **private signing key**.
* `signing.properties`: **private passwords**, alias and absolute keystore path.
* `hunreader-signing.crt`: public certificate; safe to share.

The password is cryptographically random and is not printed. Open the private
properties file locally to save it in your password manager. Keep encrypted
backups of both the keystore and credentials; losing the key prevents normal
updates of existing installs. Do not upload these files or attach them to a PR.
Windows ACLs are access controls, not encryption at rest.

Release builds read that properties file by default. For another protected
location, set `HUNREADER_SIGNING_PROPERTIES` to its absolute path. Its fields are
`storeFile`, `storePassword`, `keyAlias` and `keyPassword`; use escaped backslashes
in Java properties paths. Private key formats and signing properties are also
ignored by Git as a second safeguard.

```powershell
.\gradlew.bat :app:assembleAppRelease
```

A release build without signing configuration fails explicitly: it never silently
uses an upstream/debug key or emits a supposedly distributable unsigned release.
Build from full Git history because `versionCode` is derived from commit count.

## Validation and distribution

`HunReaderConfigurationTest` checks manifest identities/permissions, removed
presets and preserved TTS import data. `HunReaderDeviceTest` additionally checks
the installed package/providers, import routing and packaged defaults:

```powershell
.\gradlew.bat :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=io.legado.app.HunReaderDeviceTest"
```

Before distributing a release, inspect its application ID and signing certificate
with Android build tools (`aapt dump badging` and `apksigner verify --print-certs`),
then smoke-test local EPUB/TXT reading and your own TTS endpoint. TTS verification
with real credentials is deliberately a local, user-controlled test.

For every APK you share, provide the exact corresponding source and build scripts
under GPLv3, keep license/attribution notices, and identify your modifications.
Build from a clean committed revision and archive that revision (not your whole
working directory). Never include signing credentials or personal configuration.
There is no in-app update service or automated public release in this fork.
