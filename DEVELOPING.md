# Developing HunReader

This guide covers Android development, local testing, private release signing,
and installation on a physical device. Commands are written for Windows
PowerShell and should be run from the repository root unless stated otherwise.

For a codebase tour, start with the
[architecture and learning guide](docs/README.md). It covers the main modules
and traces content acquisition, reading, storage/sync and integrations with
Mermaid diagrams and source links. This development guide focuses on building,
testing and distributing the application.

## Build identities

| Build   | Application ID                          | Visible name    | Signing key                |
| ------- | --------------------------------------- | --------------- | -------------------------- |
| Debug   | `io.github.hunterxue.hunreader.debug` | HunReader Debug | Local Android debug key    |
| Release | `io.github.hunterxue.hunreader`       | HunReader       | Your private HunReader key |

Debug and release builds can be installed together. HunReader also installs
separately from upstream Legado applications.

## 1. Install the development tools

Required tools:

- Git
- JDK 17
- Android SDK Platform 36
- Android SDK Build-Tools 36
- Android SDK Platform-Tools, which provides `adb`

The checked-in Gradle wrapper downloads the required Gradle version. Do not
install a separate system Gradle or change its version to bypass build errors.

### Verified tools on this machine

The toolchains used to build and test this checkout on 2026-10-05 are installed
at stable, user-owned locations:

| Tool | Installed version | Location |
| ---- | ----------------- | -------- |
| Microsoft OpenJDK | 17.0.20.1+1 LTS (`amd64`) | `C:\Users\hunterxue\.jdks\microsoft-jdk-17.0.20.1` |
| Android SDK Command-line Tools / `sdkmanager` | 19.0 | `C:\Users\hunterxue\AppData\Local\Android\Sdk\cmdline-tools\latest` |
| Android SDK Platform | API 36, package revision 2 | `C:\Users\hunterxue\AppData\Local\Android\Sdk\platforms\android-36` |
| Android SDK Build-Tools | 36.0.0 | `C:\Users\hunterxue\AppData\Local\Android\Sdk\build-tools\36.0.0` |
| Android SDK Platform-Tools | 37.0.1 (`adb` 1.0.41) | `C:\Users\hunterxue\AppData\Local\Android\Sdk\platform-tools` |
| Gradle wrapper | 8.14.4 (Kotlin 2.0.21) | Repository `gradlew.bat` |

The exact values to use on this machine are:

```text
JAVA_HOME=C:\Users\hunterxue\.jdks\microsoft-jdk-17.0.20.1
ANDROID_HOME=C:\Users\hunterxue\AppData\Local\Android\Sdk
SDK_MANAGER=C:\Users\hunterxue\AppData\Local\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat
```

`SDK_MANAGER` above is a documentation label, not a required environment
variable. `JAVA_HOME` and `ANDROID_HOME` were intentionally not persisted
automatically; use the commands in section 2 when ready.

At the time of verification, the existing user and machine `JAVA_HOME` still
pointed to `C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot`, while
`ANDROID_HOME` and `ANDROID_SDK_ROOT` were unset. The project requires JDK 17,
so replace `JAVA_HOME` using the persistence commands below before building from
a new terminal. `ANDROID_SDK_ROOT` is not required when `ANDROID_HOME` is set.

### Recommended: Android Studio

1. Install [Android Studio](https://developer.android.com/studio).
2. Open **Tools > SDK Manager**.
3. Under **SDK Platforms**, install **Android API 36**.
4. Under **SDK Tools**, install:
   - Android SDK Build-Tools 36
   - Android SDK Command-line Tools (latest)
   - Android SDK Platform-Tools
5. Install a JDK 17 distribution, such as
   [Microsoft Build of OpenJDK](https://learn.microsoft.com/java/openjdk/download)
   or Eclipse Temurin 17.

The SDK location on this machine is:

```text
C:\Users\hunterxue\AppData\Local\Android\Sdk
```

The verified executable locations are:

```text
C:\Users\hunterxue\.jdks\microsoft-jdk-17.0.20.1\bin\java.exe
C:\Users\hunterxue\AppData\Local\Android\Sdk\platform-tools\adb.exe
C:\Users\hunterxue\AppData\Local\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat
C:\Users\hunterxue\AppData\Local\Android\Sdk\build-tools\36.0.0\aapt.exe
C:\Users\hunterxue\AppData\Local\Android\Sdk\build-tools\36.0.0\apksigner.bat
```

Android Studio displays the actual SDK location at the top of SDK Manager.

### Alternative: Android command-line tools

Download and extract the
[Android SDK command-line tools](https://developer.android.com/studio#command-tools).
Arrange them so that `sdkmanager.bat` is located at:

```text
<SDK>\cmdline-tools\latest\bin\sdkmanager.bat
```

Then install the required packages:

```powershell
$sdk = "C:\Users\hunterxue\AppData\Local\Android\Sdk"
$sdkManager = "$sdk\cmdline-tools\latest\bin\sdkmanager.bat"

& $sdkManager --sdk_root="$sdk" `
  "platform-tools" `
  "platforms;android-36" `
  "build-tools;36.0.0"
```

Review and accept the Android SDK license when prompted. Do not copy another
machine's SDK license files.

## 2. Configure environment variables and PATH

For the current PowerShell session:

```powershell
$env:JAVA_HOME = "C:\Users\hunterxue\.jdks\microsoft-jdk-17.0.20.1"
$env:ANDROID_HOME = "C:\Users\hunterxue\AppData\Local\Android\Sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:Path"
```

Confirm all tools and installed Android packages:

```powershell
java -version
adb version
sdkmanager.bat --version
sdkmanager.bat --sdk_root="$env:ANDROID_HOME" --list_installed
where.exe java
where.exe adb
where.exe sdkmanager.bat
```

To persist the settings for your Windows user, use PowerShell rather than
`setx PATH`, which can unexpectedly truncate or expand an existing PATH:

```powershell
$jdk17Home = "C:\Users\hunterxue\.jdks\microsoft-jdk-17.0.20.1"
$jdk25Home = "C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot"
$javaHome = $jdk17Home
$androidHome = "C:\Users\hunterxue\AppData\Local\Android\Sdk"
$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
$knownJavaBins = @("$jdk17Home\bin", "$jdk25Home\bin")
$remainingUserPath = ($userPath -split ";") |
    Where-Object { $_ -and $_ -notin $knownJavaBins }
$entries = @(
    "$javaHome\bin"
    "$androidHome\platform-tools"
    "$androidHome\cmdline-tools\latest\bin"
)
$newUserPath = (($entries + $remainingUserPath) | Select-Object -Unique) -join ";"

[Environment]::SetEnvironmentVariable("JDK17_HOME", $jdk17Home, "User")
[Environment]::SetEnvironmentVariable("JDK25_HOME", $jdk25Home, "User")
[Environment]::SetEnvironmentVariable("JAVA_HOME", $javaHome, "User")
[Environment]::SetEnvironmentVariable("ANDROID_HOME", $androidHome, "User")
[Environment]::SetEnvironmentVariable("Path", $newUserPath, "User")
```

PowerShell environment variables use the `Env:` provider. `$JAVA_HOME` and
`$PATH` are ordinary PowerShell variables and are normally empty; use:

```powershell
Write-Host $env:JAVA_HOME
Write-Host $env:ANDROID_HOME
$env:Path -split ";"
```

Persistent environment changes do not update processes that are already
running. Opening another terminal inside an existing VS Code window may still
inherit VS Code's old environment. After changing the variables in Windows
Settings or with `SetEnvironmentVariable`, close **all** VS Code windows and
start VS Code again. Then open a new terminal and verify:

```powershell
Write-Host $env:JAVA_HOME
Write-Host $env:ANDROID_HOME
where.exe java
where.exe adb
where.exe sdkmanager.bat
java -version
adb version
sdkmanager.bat --version
```

To update only the current terminal without restarting VS Code, run the
current-session assignment block above. It does not change persistent Windows
settings.

### Switching between JDK 17 and JDK 25

Keep `JDK17_HOME` and `JDK25_HOME` as stable references to the installations,
and use the standard `JAVA_HOME` variable for the version active in a terminal.
Do not use `JAVA_HOME2`: Java and Gradle do not recognize that name.

This project requires JDK 17, so leave JDK 17 as the persistent default. To
switch individual PowerShell terminals without changing Windows settings, add
the following functions to your
[PowerShell profile](https://learn.microsoft.com/powershell/module/microsoft.powershell.core/about/about_profiles):

```powershell
function Use-Java {
    param([Parameter(Mandatory)][string] $JavaHome)

    $java = Join-Path $JavaHome "bin\java.exe"
    if (-not (Test-Path $java)) {
        throw "Java executable not found: $java"
    }

    $knownJavaBins = @(
        "$env:JDK17_HOME\bin"
        "$env:JDK25_HOME\bin"
        "$env:JAVA_HOME\bin"
    ) | Where-Object { $_ }
    $remainingPath = ($env:Path -split ";") |
        Where-Object { $_ -and $_ -notin $knownJavaBins }

    $env:JAVA_HOME = $JavaHome
    $env:Path = ((@("$JavaHome\bin") + $remainingPath) |
        Select-Object -Unique) -join ";"

    Write-Host "JAVA_HOME=$env:JAVA_HOME"
    java -version
}

function Use-Java17 { Use-Java $env:JDK17_HOME }
function Use-Java25 { Use-Java $env:JDK25_HOME }
```

Open a new terminal after saving the profile, then use:

```powershell
Use-Java17  # HunReader / this repository
Use-Java25  # A project that requires JDK 25
```

The switch affects only that terminal and its child processes; other open
terminals keep their current Java version. A Gradle daemon keeps the JVM with
which it started. When switching Java versions in the same repository, stop the
old daemon before the next build:

```powershell
.\gradlew.bat --stop
Use-Java17
.\gradlew.bat --version
```

If you do not want a global `ANDROID_HOME`, let Android Studio create the ignored
`local.properties` file, or create it yourself:

```properties
sdk.dir=C\:\\Users\\hunterxue\\AppData\\Local\\Android\\Sdk
```

Do not commit `local.properties`.

## 3. Verify the checkout

The release `versionCode` is based on the Git commit count, so use a full clone
rather than a shallow checkout when producing a release.

```powershell
git status --short
.\gradlew.bat --version
.\gradlew.bat :app:testAppDebugUnitTest
```

The project requires JDK 17. If the Gradle output reports another JVM, correct
`JAVA_HOME` before continuing.

## 4. Build and install a debug APK

Build:

```powershell
.\gradlew.bat :app:assembleAppDebug
```

The APK is written below:

```text
app\build\outputs\apk\app\debug\
```

The filename includes the generated version, for example
`hunreader_app_debug_3.26.100421.apk`. Select the newest file without hard-coding
the version:

```powershell
$debugApk = Get-ChildItem ".\app\build\outputs\apk\app\debug\*.apk" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

$debugApk.FullName
```

Install it on a connected device:

```powershell
adb install -r $debugApk.FullName
```

The debug build has its own package ID, so it does not replace the release build.

## 5. Prepare private release signing

On Windows, generate the key once:

```powershell
.\scripts\New-HunReaderSigningKey.ps1
```

By default, it creates:

```text
%USERPROFILE%\.hunreader\hunreader-release.p12
%USERPROFILE%\.hunreader\signing.properties
%USERPROFILE%\.hunreader\hunreader-signing.crt
```

- `hunreader-release.p12` is the private signing key.
- `signing.properties` contains the random passwords, alias, and keystore path.
- `hunreader-signing.crt` is the public certificate and is safe to share.

The script uses a 4096-bit RSA key with approximately 50 years of validity,
refuses to overwrite an existing key, refuses repository-local output, and
restricts its directory to the current Windows user and SYSTEM.

Open `signing.properties` locally and save its credentials in your password
manager. Keep encrypted backups of both the keystore and credentials. Losing the
key prevents normal updates of existing release installations. Never upload the
private files, attach them to a pull request, or store personal TTS credentials
in the repository.

Gradle reads `%USERPROFILE%\.hunreader\signing.properties` by default. To use a
different protected location:

```powershell
$env:HUNREADER_SIGNING_PROPERTIES = "D:\private\hunreader-signing.properties"
```

This variable only points at a properties file. Its required fields are
`storeFile`, `storePassword`, `keyAlias`, and `keyPassword`. Backslashes in its
Java-properties `storeFile` value must be escaped.

## 6. Build and verify a release APK

Build and run the JVM tests:

```powershell
.\gradlew.bat :app:testAppDebugUnitTest :app:assembleAppRelease
```

The release APK is written below:

```text
app\build\outputs\apk\app\release\
```

> A release build fails explicitly if the private signing configuration is absent; it never silently uses the debug or historical upstream key.

Select and inspect the newest release:

```powershell
$releaseApk = Get-ChildItem ".\app\build\outputs\apk\app\release\*.apk" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

Write-Host $releaseApk

$buildTools = "$env:ANDROID_HOME\build-tools\36.0.0"
& "$buildTools\aapt.exe" dump badging $releaseApk.FullName |
    Select-String "package:|application-label:|sdkVersion:|targetSdkVersion:"
& "$buildTools\apksigner.bat" verify --verbose --print-certs $releaseApk.FullName
```

Confirm:

- Package: `io.github.hunterxue.hunreader`
- Label: `HunReader`
- The SHA-256 signer fingerprint matches your backed-up certificate

To display the certificate fingerprint independently:

```powershell
$signing = Get-Content "$env:USERPROFILE\.hunreader\signing.properties" |
    ConvertFrom-StringData
$env:HUNREADER_KEY_PASSWORD = $signing.storePassword
try {
    keytool -list -v `
      -keystore $signing.storeFile `
      -alias $signing.keyAlias `
      -storepass:env HUNREADER_KEY_PASSWORD |
      Select-String "SHA256:"
} finally {
    Remove-Item Env:HUNREADER_KEY_PASSWORD
}
```

## 7. Connect a physical Android device with ADB

On the device:

1. Open **Settings > About phone**.
2. Tap **Build number** seven times to enable Developer options.
3. Open **Developer options** and enable **USB debugging**.
4. Connect the device with a USB data cable.
5. Accept the computer's debugging authorization prompt on the device.

On Windows, the Google USB Driver is available from SDK Manager, but many
manufacturers require their own OEM driver. See the official
[OEM USB driver list](https://developer.android.com/studio/run/oem-usb).

Check the connection:

```powershell
adb kill-server
adb start-server
adb devices -l
```

The device state must be `device`:

```text
List of devices attached
SERIAL_NUMBER    device product:... model:...
```

If it says `unauthorized`, unlock the device and accept the prompt. If no device
appears, try another data cable/USB port and check Device Manager and the OEM
driver. If `adb` is not found, run it by absolute path:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices -l
```

## 8. Install and launch the release APK

Install or update HunReader while preserving its existing data:

```powershell
$releaseApk = Get-ChildItem ".\app\build\outputs\apk\app\release\*.apk" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

adb install -r $releaseApk.FullName
```

With multiple connected devices, specify the serial from `adb devices -l`:

```powershell
$serial = "YOUR_DEVICE_SERIAL"
adb -s $serial install -r $releaseApk.FullName
```

Launch it:

```powershell
adb shell monkey -p io.github.hunterxue.hunreader `
  -c android.intent.category.LAUNCHER 1
```

View app logs:

```powershell
adb logcat --pid=$(adb shell pidof -s io.github.hunterxue.hunreader)
```

Stop the app:

```powershell
adb shell am force-stop io.github.hunterxue.hunreader
```

Uninstalling deletes HunReader's application data:

```powershell
adb uninstall io.github.hunterxue.hunreader
```

If `adb install -r` reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the installed
APK with this package ID was signed by a different key. Back up any required app
data, uninstall that package, and reinstall. Do not solve this by changing keys
for future releases.

## 9. Run device tests

With a device connected:

```powershell
.\gradlew.bat :app:connectedAppDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=io.legado.app.HunReaderDeviceTest"
```

These tests verify installed package/provider isolation, import routing, and the
absence of bundled online presets. They do not contact a real Azure TTS endpoint.
Test your own TTS configuration manually without placing its credentials in logs,
test fixtures, commits, or issue reports.

### Text-selection app regression checks

Run the focused word-selection and configuration tests and build the debug APK:

```powershell
.\gradlew.bat :app:testAppDebugUnitTest `
  --tests "io.legado.app.ui.book.read.page.WordSelectionTest" `
  --tests "io.legado.app.HunReaderConfigurationTest"
.\gradlew.bat :app:assembleAppDebug
```

The Android tests use a receiver packaged only in the test APK and intercept
outgoing intents, so they do not open a real dictionary or contact a service:

```powershell
.\gradlew.bat :app:connectedAppDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=io.legado.app.TextSelectionAppDeviceTest"
```

They cover exact payloads, app discovery, invalid/unavailable targets, launch
errors, preference behavior, backup exclusions, release/cancel gestures,
selection cleanup, and the normal menu fallback. The gesture fixtures exercise
the native reader's text-selection path without importing a personal book.

Whole-word regressions cover every letter of `put down` and `get up` in both
directions, shrinking/reversing a drag, outer separators versus internal
punctuation, wrapped words, Unicode column offsets, styled text, paragraph
breaks, and character-precise handles. On API 26+, a blocking activity monitor
also checks the actual phrase in `EXTRA_PROCESS_TEXT`, including a final
position delivered only on finger release. The JVM word-range tests run
without a device; Android's own segmentation and touch behavior still require
the instrumented tests.

Without a connected device, compile the device tests separately:

```powershell
.\gradlew.bat :app:assembleAppDebugAndroidTest
```

Compiling the test APK does not count as executing the gesture tests.

### Reader page-turn gesture regression checks

Paginated reading modes preview a page turn only after a clearly horizontal
drag. Releasing must be at least 15% of the reading view width from the original
touch point; shorter drags snap back even when they are fast. Continuous
vertical scrolling and configured tap actions do not use this release gate.

Run the focused JVM gesture and word-selection checks:

```powershell
.\gradlew.bat :app:testAppDebugUnitTest `
  --tests "io.legado.app.ui.book.read.page.PageTurnGestureTest" `
  --tests "io.legado.app.ui.book.read.page.WordSelectionTest"
```

Build the app and instrumentation APK, then run the selection gesture checks on
an authorized device or emulator:

```powershell
.\gradlew.bat :app:assembleAppDebug :app:assembleAppDebugAndroidTest
.\gradlew.bat :app:connectedAppDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=io.legado.app.TextSelectionAppDeviceTest"
```

Manually verify curl, cover, slide, and no-animation modes. Check releases just
below and above 15%, dragging past the threshold and back, small finger drift
while long-pressing text, side taps, and vertical scrolling. A compiled test APK
does not substitute for this real gesture validation.
Windows ARM hosts require a physical Android device for these checks: the
Android emulator currently requires an x64 Windows host, even when an ARM64
guest system image is available. See the
[Windows emulator hardware requirements](https://learn.microsoft.com/en-us/dotnet/maui/android/emulator/hardware-acceleration).

Before declaring Eudic compatibility, separately test with the installed Eudic
version on a physical device:

- Choose its text-selection action in reading settings and enable automatic
  opening. Confirm both settings survive an app restart.
- Hold an English word: nothing launches until release. Release: exactly one
  dictionary lookup, without expanding a menu or showing a chooser.
- Hold a middle letter in `put`, then drag onto the first or middle letter of
  `down`: highlight and send exactly `put down`. Repeat with `get up`, backward
  dragging, a third word, and dragging back to shrink the selection.
- Finish a drag on spaces, a comma, or a closing quote: the moving edge stops
  at the reached word. Punctuation and spacing inside the phrase remain intact.
- Long-press and drag a sentence before releasing: the complete selection is
  sent, including Unicode and paragraph breaks where present.
- Test words/phrases wrapping across lines and between pages visible together
  in scrolling mode. Moving outside hittable text must retain the last valid
  selection. Selection does not automatically turn a page.
- Return to the reader: reading position is unchanged, selection is cleared,
  and Eudic does not reopen. Selecting the same word again starts a new lookup.
- Cancel a selection gesture: no lookup. With automatic mode off, verify copy,
  bookmarks, built-in dictionary, and expanded/collapsed menus still work.
- With automatic mode off, drag the separate handles to select part of a word
  or include punctuation, and cross the handles to check cursor reversal.
- Test both TXT and EPUB, scrolling and paginated reading. Verify API 21-22
  retain normal selection behavior and display the unsupported setting state.
- Exercise unavailable/disabled targets on a disposable test setup rather than
  uninstalling a personal dictionary. Confirm visible errors and retained text.

An APK build or an automated receiver test alone does not establish compatibility
with a particular Eudic release. Record untested device scenarios explicitly.

### TTS paragraph emphasis smoke test

- In read-aloud settings, test text color, bold, and solid/dashed underline
  independently and in combination. Confirm only the current paragraph changes.
- Confirm pause and stop clear the emphasis, and that paragraph and page
  transitions do not leave stale emphasis behind.
- Test TXT and EPUB content in scrolling and paginated modes, including links,
  search results, custom fonts, day/night themes, and E-ink mode.
- With bold enabled, confirm line breaks, pagination, text selection bounds, and
  reading position remain unchanged as TTS advances.

## 10. Distribution checklist

Before sharing an APK:

1. Start from a clean, committed, full-history checkout.
2. Run JVM tests and build the release.
3. Verify the package identity and signing certificate.
4. Install it on a physical device and smoke-test TXT/EPUB reading and your own
   configured services.
5. Archive the exact committed source revision and build scripts.
6. Provide that corresponding source with the APK as required by GPLv3.
7. Keep signing material and personal configurations out of Git and artifacts.

HunReader intentionally has no in-app updater or automated public release
workflow. Protecting and consistently reusing the private release key is your
responsibility.
