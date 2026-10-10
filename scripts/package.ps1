param(
    [Parameter(Mandatory)][ValidatePattern('^\d+\.\d+\.\d+$')][string]$Version,
    [string]$OutputDirectory = 'dist'
)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { throw 'Set ANDROID_HOME to the Android SDK directory.' }
$tools = Join-Path $sdk 'build-tools/37.0.0'
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java).Source }
$output = [IO.Path]::GetFullPath((Join-Path $root $OutputDirectory))
New-Item -ItemType Directory -Force -Path $output | Out-Null
$checksums = @()
foreach ($variant in @('release', 'debug')) {
    $source = Join-Path $root "build/outputs/apk/$variant/HookXbud-$variant.apk"
    if (!(Test-Path -LiteralPath $source)) { throw "Missing signed APK: $source" }
    & $java -jar (Join-Path $tools 'lib/apksigner.jar') verify --verbose $source
    if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed: HookXbud $variant" }
    $badging = & (Join-Path $tools 'aapt2.exe') dump badging $source
    if ($LASTEXITCODE -ne 0) { throw "APK metadata verification failed: HookXbud $variant" }
    $package = $badging | Select-Object -First 1
    if ($package -notlike "*name='xyz.fearr.hookxbud'*" -or $package -notlike "*versionName='$Version'*") {
        throw "Unexpected APK package or version: $package"
    }
    if ($variant -eq 'release' -and ($badging -contains 'application-debuggable')) {
        throw 'HookXbud release APK is debuggable.'
    }
    $name = "HookXbud-$Version-$variant.apk"
    $target = Join-Path $output $name
    Copy-Item -LiteralPath $source -Destination $target
    $hash = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
    $checksums += "$hash  $name"
    Write-Output "$name $((Get-Item -LiteralPath $target).Length) bytes"
}
[IO.File]::WriteAllLines((Join-Path $output 'SHA256SUMS'), $checksums, [Text.UTF8Encoding]::new($false))
