# Developing HunReader

This guide covers Android development, local testing, private release signing,
and installation on a physical device. Commands are written for Windows
PowerShell and should be run from the repository root unless stated otherwise.

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

The usual SDK location installed by Android Studio on Windows is:

```text
%LOCALAPPDATA%\Android\Sdk
```

The relevant executable locations are normally:

```text
%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat
%LOCALAPPDATA%\Android\Sdk\build-tools\36.0.0\aapt.exe
%LOCALAPPDATA%\Android\Sdk\build-tools\36.0.0\apksigner.bat
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
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
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
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:Path"
```

Replace `JAVA_HOME` with the directory containing your JDK 17 installation.
Confirm both tools:

```powershell
java -version
adb version
where.exe java
where.exe adb
```

To persist the settings for your Windows user, use PowerShell rather than
`setx PATH`, which can unexpectedly truncate or expand an existing PATH:

```powershell
$javaHome = "C:\Program Files\Microsoft\jdk-17"
$androidHome = "$env:LOCALAPPDATA\Android\Sdk"
$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
$entries = @(
    "$javaHome\bin"
    "$androidHome\platform-tools"
    "$androidHome\cmdline-tools\latest\bin"
)
$newUserPath = (($entries + ($userPath -split ";")) |
    Where-Object { $_ } |
    Select-Object -Unique) -join ";"

[Environment]::SetEnvironmentVariable("JAVA_HOME", $javaHome, "User")
[Environment]::SetEnvironmentVariable("ANDROID_HOME", $androidHome, "User")
[Environment]::SetEnvironmentVariable("Path", $newUserPath, "User")
```

Open a new terminal after changing persistent variables.

If you do not want a global `ANDROID_HOME`, let Android Studio create the ignored
`local.properties` file, or create it yourself:

```properties
sdk.dir=C\:\\Users\\YOUR_NAME\\AppData\\Local\\Android\\Sdk
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

A release build fails explicitly if the private signing configuration is absent;
it never silently uses the debug or historical upstream key.

Select and inspect the newest release:

```powershell
$releaseApk = Get-ChildItem ".\app\build\outputs\apk\app\release\*.apk" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

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
