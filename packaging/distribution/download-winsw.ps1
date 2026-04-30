$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------------------
# download-winsw.ps1
#
# Jangbogo install.bat fallback downloader. If the build environment did
# not have packaging/winsw/jangbogo-service.exe at packageDist time, the
# distribution ZIP ships without the WinSW binary. install.bat calls this
# script in that case so the user does not have to fetch it manually.
#
# - Target path: <baseDir>\service\jangbogo-service.exe
# - Source     : WinSW v2.12.0 official release (winsw/winsw on GitHub),
#                .NET Framework 4.6.1 build, stable.
# ---------------------------------------------------------------------------

$baseDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$serviceDir = Join-Path $baseDir 'service'
$winswExe = Join-Path $serviceDir 'jangbogo-service.exe'

# Skip if already present.
if (Test-Path $winswExe) {
    Write-Host '[INFO] WinSW executable already present.'
    exit 0
}

if (-not (Test-Path $serviceDir)) {
    New-Item -ItemType Directory -Path $serviceDir | Out-Null
}

$url = 'https://github.com/winsw/winsw/releases/download/v2.12.0/WinSW-x64.exe'

Write-Host "[INFO] Downloading WinSW from: $url"
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $url -OutFile $winswExe -UseBasicParsing
} catch {
    throw "WinSW download failed: $($_.Exception.Message)"
}

if (-not (Test-Path $winswExe)) {
    throw 'WinSW executable was not created after download.'
}

# Strip the Windows MOTW so service registration is not blocked.
try {
    Unblock-File -Path $winswExe -ErrorAction SilentlyContinue
} catch {
    # ignore: not fatal
}

Write-Host "[OK] WinSW executable is ready: $winswExe"
