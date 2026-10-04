[CmdletBinding()]
param(
    [string]$Directory = (Join-Path $HOME '.hunreader')
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
    throw 'This script protects credentials with Windows ACLs. Use keytool with equivalent private permissions on other platforms.'
}
$keytool = (Get-Command keytool -ErrorAction Stop).Source
$directoryPath = [IO.Path]::GetFullPath($Directory)
$repositoryPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')).TrimEnd('\') + '\'
if (($directoryPath.TrimEnd('\') + '\').StartsWith($repositoryPath, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Signing material must be generated outside the repository.'
}
$keystore = Join-Path $directoryPath 'hunreader-release.p12'
$propertiesFile = Join-Path $directoryPath 'signing.properties'
$certificate = Join-Path $directoryPath 'hunreader-signing.crt'
foreach ($path in @($keystore, $propertiesFile, $certificate)) {
    if (Test-Path -LiteralPath $path) {
        throw "Refusing to overwrite existing signing material: $path"
    }
}
if (Test-Path -LiteralPath $directoryPath) {
    $directoryInfo = Get-Item -LiteralPath $directoryPath
    if (!$directoryInfo.PSIsContainer -or
        ($directoryInfo.Attributes -band [IO.FileAttributes]::ReparsePoint) -or
        (Get-ChildItem -LiteralPath $directoryPath -Force | Select-Object -First 1)) {
        throw 'Use a new or empty dedicated directory, not a link or an existing data directory.'
    }
} else {
    New-Item -ItemType Directory -Path $directoryPath | Out-Null
}
$acl = New-Object Security.AccessControl.DirectorySecurity
$acl.SetAccessRuleProtection($true, $false)
$user = [Security.Principal.WindowsIdentity]::GetCurrent().User
foreach ($sid in @($user, [Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
    $rule = [Security.AccessControl.FileSystemAccessRule]::new(
        $sid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
    $acl.AddAccessRule($rule)
}
Set-Acl -LiteralPath $directoryPath -AclObject $acl

$random = [Security.Cryptography.RandomNumberGenerator]::Create()
$bytes = New-Object byte[] 48
$random.GetBytes($bytes)
$random.Dispose()
$password = [Convert]::ToBase64String($bytes)
$previousPassword = [Environment]::GetEnvironmentVariable('HUNREADER_KEY_PASSWORD', 'Process')
try {
    $env:HUNREADER_KEY_PASSWORD = $password
    $escapedKeystore = $keystore.Replace('\', '\\')
    $properties = @"
storeFile=$escapedKeystore
storePassword=$password
keyAlias=hunreader
keyPassword=$password
"@
    # Persist recovery credentials before keytool creates an irreplaceable key.
    [IO.File]::WriteAllText($propertiesFile, $properties, [Text.UTF8Encoding]::new($false))
    & $keytool -genkeypair -noprompt -keystore $keystore -storetype PKCS12 `
        -alias hunreader -keyalg RSA -keysize 4096 -sigalg SHA256withRSA `
        -validity 18263 -dname 'CN=HunReader' `
        -storepass:env HUNREADER_KEY_PASSWORD -keypass:env HUNREADER_KEY_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Key generation failed. Inspect the protected signing directory before retrying.' }
    & $keytool -exportcert -rfc -keystore $keystore -alias hunreader `
        -storepass:env HUNREADER_KEY_PASSWORD -file $certificate
    if ($LASTEXITCODE -ne 0) { throw 'The key exists, but public certificate export failed.' }
    & $keytool -list -v -keystore $keystore -alias hunreader -storepass:env HUNREADER_KEY_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Signing key verification failed.' }
    Write-Output "Private keystore: $keystore"
    Write-Output "Private passwords and build configuration: $propertiesFile"
    Write-Output "Public certificate: $certificate"
    Write-Output 'Back up the keystore and credentials securely. They are not printed or uploaded.'
} finally {
    [Environment]::SetEnvironmentVariable('HUNREADER_KEY_PASSWORD', $previousPassword, 'Process')
    $password = $null
    $properties = $null
    [Array]::Clear($bytes, 0, $bytes.Length)
}
