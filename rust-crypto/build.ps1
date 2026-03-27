$ErrorActionPreference = "Stop"

function Resolve-AndroidSdkDir {
    if ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) {
        return $env:ANDROID_SDK_ROOT
    }
    if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) {
        return $env:ANDROID_HOME
    }

    $defaultSdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if (Test-Path $defaultSdk) {
        return $defaultSdk
    }

    $repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
    $localProps = Join-Path $repoRoot "local.properties"
    if (Test-Path $localProps) {
        $sdkLine = Select-String -Path $localProps -Pattern "^sdk\.dir=" | Select-Object -First 1
        if ($sdkLine) {
            $raw = $sdkLine.Line.Substring("sdk.dir=".Length).Trim()
            $sdkDir = $raw -replace "\\\\", "\"
            if (Test-Path $sdkDir) {
                return $sdkDir
            }
        }
    }

    throw "Android SDK not found. Set ANDROID_SDK_ROOT (or ANDROID_HOME) or configure sdk.dir in local.properties."
}

function Resolve-AndroidNdkDir([string]$sdkDir) {
    if ($env:ANDROID_NDK_HOME -and (Test-Path $env:ANDROID_NDK_HOME)) {
        return $env:ANDROID_NDK_HOME
    }

    $ndkRoot = Join-Path $sdkDir "ndk"
    if (-not (Test-Path $ndkRoot)) {
        throw "Android NDK folder not found under: $ndkRoot"
    }

    $latest = Get-ChildItem $ndkRoot -Directory | Sort-Object Name -Descending | Select-Object -First 1
    if (-not $latest) {
        throw "No Android NDK versions found under: $ndkRoot"
    }

    return $latest.FullName
}

function Build-Target([string]$target) {
    cargo build --target $target --release
    if ($LASTEXITCODE -ne 0) {
        throw "Build failed for target: $target"
    }
}

$sdkDir = Resolve-AndroidSdkDir
$ndkDir = Resolve-AndroidNdkDir $sdkDir
$toolchainBin = Join-Path $ndkDir "toolchains\llvm\prebuilt\windows-x86_64\bin"

if (-not (Test-Path $toolchainBin)) {
    throw "NDK toolchain bin not found: $toolchainBin"
}

$env:ANDROID_NDK_HOME = $ndkDir

# Override target toolchains per shell to avoid machine-specific values in .cargo/config.toml.
$env:CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER = Join-Path $toolchainBin "aarch64-linux-android21-clang.cmd"
$env:CARGO_TARGET_AARCH64_LINUX_ANDROID_AR = Join-Path $toolchainBin "llvm-ar.exe"

$env:CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER = Join-Path $toolchainBin "armv7a-linux-androideabi21-clang.cmd"
$env:CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_AR = Join-Path $toolchainBin "llvm-ar.exe"

$env:CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER = Join-Path $toolchainBin "x86_64-linux-android21-clang.cmd"
$env:CARGO_TARGET_X86_64_LINUX_ANDROID_AR = Join-Path $toolchainBin "llvm-ar.exe"

Write-Host "Using Android SDK: $sdkDir"
Write-Host "Using Android NDK: $ndkDir"
Write-Host "Building Rust JNI libs for Android..."

rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android | Out-Null

Build-Target "aarch64-linux-android"
Build-Target "armv7-linux-androideabi"
Build-Target "x86_64-linux-android"

Write-Host "Compilation complete. Copying artifacts to androidApp/src/main/jniLibs..."

$jniLibsPath = Resolve-Path (Join-Path $PSScriptRoot "..\androidApp\src\main\jniLibs")
New-Item -ItemType Directory -Force -Path (Join-Path $jniLibsPath "arm64-v8a") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $jniLibsPath "armeabi-v7a") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $jniLibsPath "x86_64") | Out-Null

Copy-Item (Join-Path $PSScriptRoot "target\aarch64-linux-android\release\libvalera_crypto.so") (Join-Path $jniLibsPath "arm64-v8a\libvalera_crypto.so") -Force
Copy-Item (Join-Path $PSScriptRoot "target\armv7-linux-androideabi\release\libvalera_crypto.so") (Join-Path $jniLibsPath "armeabi-v7a\libvalera_crypto.so") -Force
Copy-Item (Join-Path $PSScriptRoot "target\x86_64-linux-android\release\libvalera_crypto.so") (Join-Path $jniLibsPath "x86_64\libvalera_crypto.so") -Force

Write-Host "Done. Updated JNI libs:"
Write-Host "  - arm64-v8a/libvalera_crypto.so"
Write-Host "  - armeabi-v7a/libvalera_crypto.so"
Write-Host "  - x86_64/libvalera_crypto.so"