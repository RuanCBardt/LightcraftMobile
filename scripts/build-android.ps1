param(
    [ValidateSet('arm64-v8a','x86_64','all')] [string]$Abi = 'arm64-v8a',
    [string]$RustToolchain = 'stable',
    [switch]$NativeOnly
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { throw 'Set ANDROID_HOME to your Android SDK folder.' }
$ndkRoot = Join-Path $sdkRoot 'ndk/28.2.13676358'
$llvmRoot = Join-Path $ndkRoot 'toolchains/llvm/prebuilt/windows-x86_64/bin'
if (!(Test-Path -LiteralPath $llvmRoot)) { throw 'Install Android NDK 28.2.13676358 with SDK Manager.' }
$targets = if ($Abi -eq 'all') { @('arm64-v8a','x86_64') } else { @($Abi) }
Push-Location $repoRoot
try {
    foreach ($arch in $targets) {
        $triple = if ($arch -eq 'arm64-v8a') { 'aarch64-linux-android' } else { 'x86_64-linux-android' }
        & rustup target add $triple --toolchain $RustToolchain
        if ($LASTEXITCODE -ne 0) { throw 'rustup failed' }
        $key = 'CARGO_TARGET_' + $triple.ToUpper().Replace('-','_') + '_LINKER'
        [Environment]::SetEnvironmentVariable($key, (Join-Path $llvmRoot ($triple + '29-clang.cmd')), 'Process')
        [Environment]::SetEnvironmentVariable(('CC_' + $triple.Replace('-','_')), (Join-Path $llvmRoot ($triple + '29-clang.cmd')), 'Process')
        & cargo "+$RustToolchain" build --locked --release -p lightcraft-mobile --target $triple
        if ($LASTEXITCODE -ne 0) { throw 'Native engine build failed' }
        $destination = Join-Path $repoRoot "apps/android/app/src/main/jniLibs/$arch"
        New-Item -ItemType Directory -Force $destination | Out-Null
        Copy-Item -LiteralPath (Join-Path $repoRoot "target/$triple/release/liblightcraft_mobile.so") -Destination $destination
    }
    if (!$NativeOnly) {
        Push-Location (Join-Path $repoRoot 'apps/android')
        try { & .\gradlew.bat assembleDebug "-PmobileAbis=$($targets -join ',')"; if ($LASTEXITCODE -ne 0) { throw 'Android build failed' } } finally { Pop-Location }
    }
} finally { Pop-Location }
