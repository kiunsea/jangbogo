$ErrorActionPreference = 'Stop'

$baseDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$jreDir = Join-Path $baseDir 'jre'
$tempDir = Join-Path $baseDir 'tmp-jre-download'
$zipPath = Join-Path $tempDir 'jre21.zip'

# Eclipse Temurin JRE 21 (Windows x64) - Jangbogo requires Java 21
$url = 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse'

if (Test-Path (Join-Path $jreDir 'bin\java.exe')) {
    Write-Host '[INFO] Bundled JRE already exists.'
    exit 0
}

if (-not (Test-Path $tempDir)) {
    New-Item -ItemType Directory -Path $tempDir | Out-Null
}

Write-Host "[INFO] Downloading JRE from: $url"
Invoke-WebRequest -Uri $url -OutFile $zipPath

Write-Host '[INFO] Extracting JRE archive...'
Expand-Archive -Path $zipPath -DestinationPath $tempDir -Force

$extractedJava = Get-ChildItem -Path $tempDir -Recurse -Filter 'java.exe' |
    Where-Object { $_.FullName -like '*\bin\java.exe' } |
    Select-Object -First 1

if (-not $extractedJava) {
    throw 'java.exe not found after extraction.'
}

$resolvedJreRoot = Split-Path (Split-Path $extractedJava.FullName -Parent) -Parent

if (Test-Path $jreDir) {
    Remove-Item -Recurse -Force $jreDir
}
New-Item -ItemType Directory -Path $jreDir | Out-Null

Copy-Item -Path (Join-Path $resolvedJreRoot '*') -Destination $jreDir -Recurse -Force

if (-not (Test-Path (Join-Path $jreDir 'bin\java.exe'))) {
    throw 'Bundled JRE setup failed (java.exe missing).'
}

Remove-Item -Recurse -Force $tempDir
Write-Host '[OK] Bundled JRE is ready: .\jre\bin\java.exe'
