param(
    [switch]$Restart
)

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$ErrorActionPreference = 'Stop'

$baseDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$serviceExe = Join-Path $baseDir 'service\jangbogo-service.exe'

# ---------------------------------------------------------------------------
# Single-instance guard
#
# OS 재부팅 후 explorer.exe 가 NotifyIcon 알림 영역을 새로고침하지 못하면 트레이
# 아이콘이 보이지 않는 상태가 발생할 수 있습니다. 이 경우 사용자가 단축아이콘을
# 더블클릭하면 새 인스턴스가 추가되어 좀비 프로세스가 누적될 수 있어, 실행 시점에
# 기존 인스턴스 유무를 검사합니다.
#
# - Restart 모드: 같은 스크립트로 떠 있는 PowerShell 프로세스를 모두 종료 후 진행.
# - 기본 모드: Mutex 를 획득해 중복 실행을 막고, 이미 잡혀 있으면 사용자에게 안내.
# ---------------------------------------------------------------------------
$scriptPath = $MyInvocation.MyCommand.Path
$scriptName = [System.IO.Path]::GetFileName($scriptPath)
$mutexName = 'Global\JangbogoTrayInstance'

function Stop-ExistingTrayInstances {
    $current = $PID
    $procs = Get-CimInstance Win32_Process -Filter "Name = 'powershell.exe' OR Name = 'pwsh.exe'" -ErrorAction SilentlyContinue
    foreach ($p in $procs) {
        if ($p.ProcessId -eq $current) { continue }
        $cmd = $p.CommandLine
        if ([string]::IsNullOrEmpty($cmd)) { continue }
        if ($cmd -like "*$scriptName*") {
            try {
                Stop-Process -Id $p.ProcessId -Force -ErrorAction Stop
            }
            catch {
                # 이미 종료되었거나 권한 부족 — 계속 진행
            }
        }
    }
    Start-Sleep -Milliseconds 400
}

if ($Restart) {
    Stop-ExistingTrayInstances
}

$mutexCreated = $false
$singleInstanceMutex = New-Object System.Threading.Mutex($true, $mutexName, [ref]$mutexCreated)
if (-not $mutexCreated) {
    [System.Windows.Forms.MessageBox]::Show(
        "Jangbogo 트레이가 이미 실행 중입니다.`n`n트레이 아이콘이 보이지 않는다면 'Restart Jangbogo Tray' 단축아이콘을 사용하거나, Restart-Tray.bat 을 실행하세요.",
        'Jangbogo Tray',
        [System.Windows.Forms.MessageBoxButtons]::OK,
        [System.Windows.Forms.MessageBoxIcon]::Information
    ) | Out-Null
    return
}
$trayIconIcoCandidates = @(
    [System.IO.Path]::GetFullPath((Join-Path $baseDir 'img\favicon.ico')),
    [System.IO.Path]::GetFullPath((Join-Path $baseDir '..\..\img\favicon.ico'))
)
$dashboardUrl = 'http://127.0.0.1:8282'

function New-JangbogoTrayIcon {
    foreach ($iconPath in $trayIconIcoCandidates) {
        if (Test-Path $iconPath) {
            try {
                return New-Object System.Drawing.Icon ($iconPath)
            }
            catch {
                # Fall through to generated icon if ico loading fails.
            }
        }
    }

    # Generated fallback icon: shopping cart style on blue circle
    $bmp = New-Object System.Drawing.Bitmap 64, 64
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    try {
        $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $g.Clear([System.Drawing.Color]::Transparent)

        # Blue circle background
        $bgBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 33, 150, 243))
        $g.FillEllipse($bgBrush, 2, 2, 60, 60)

        # White border
        $borderPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::White, 2)
        $g.DrawEllipse($borderPen, 2, 2, 60, 60)

        # Shopping cart icon in white
        $cartPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::White, 3)
        $cartBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)

        # Cart body (trapezoid-ish)
        $points = @(
            (New-Object System.Drawing.Point 18, 24),
            (New-Object System.Drawing.Point 48, 24),
            (New-Object System.Drawing.Point 44, 42),
            (New-Object System.Drawing.Point 22, 42)
        )
        $g.DrawPolygon($cartPen, $points)

        # Cart handle
        $g.DrawLine($cartPen, 12, 18, 18, 24)

        # Wheels
        $g.FillEllipse($cartBrush, 23, 46, 6, 6)
        $g.FillEllipse($cartBrush, 37, 46, 6, 6)

        $hIcon = $bmp.GetHicon()
        return [System.Drawing.Icon]::FromHandle($hIcon)
    }
    finally {
        $g.Dispose()
        $bmp.Dispose()
    }
}

function Open-Dashboard {
    Start-Process $dashboardUrl | Out-Null
}

function Invoke-ServiceCommand {
    param([string]$Command)

    if (-not (Test-Path $serviceExe)) {
        [System.Windows.Forms.MessageBox]::Show(
            "service\jangbogo-service.exe was not found.",
            'Jangbogo Tray',
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Error
        ) | Out-Null
        return
    }

    try {
        $output = & $serviceExe $Command 2>&1 | Out-String
        if ([string]::IsNullOrWhiteSpace($output)) {
            $output = "$Command completed"
        }

        [System.Windows.Forms.MessageBox]::Show(
            $output.Trim(),
            "Jangbogo Service: $Command",
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Information
        ) | Out-Null
    }
    catch {
        [System.Windows.Forms.MessageBox]::Show(
            "Service command failed.`n$($_.Exception.Message)",
            'Jangbogo Tray',
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Error
        ) | Out-Null
    }
}

$notifyIcon = New-Object System.Windows.Forms.NotifyIcon
$notifyIcon.Icon = New-JangbogoTrayIcon
$notifyIcon.Text = 'Jangbogo Tray'
$notifyIcon.Visible = $true

$contextMenu = New-Object System.Windows.Forms.ContextMenuStrip

$menuOpen = $contextMenu.Items.Add('Open Jangbogo Dashboard')
$menuOpen.Add_Click({ Open-Dashboard })

$contextMenu.Items.Add('-') | Out-Null

$menuStatus = $contextMenu.Items.Add('Service Status')
$menuStatus.Add_Click({ Invoke-ServiceCommand -Command 'status' })

$menuStart = $contextMenu.Items.Add('Start Service')
$menuStart.Add_Click({ Invoke-ServiceCommand -Command 'start' })

$menuStop = $contextMenu.Items.Add('Stop Service')
$menuStop.Add_Click({ Invoke-ServiceCommand -Command 'stop' })

$menuRestart = $contextMenu.Items.Add('Restart Service')
$menuRestart.Add_Click({ Invoke-ServiceCommand -Command 'restart' })

$contextMenu.Items.Add('-') | Out-Null

$menuExit = $contextMenu.Items.Add('Exit Tray')
$menuExit.Add_Click({
    $notifyIcon.Visible = $false
    $notifyIcon.Dispose()
    if ($singleInstanceMutex) {
        try { $singleInstanceMutex.ReleaseMutex() } catch { }
        $singleInstanceMutex.Dispose()
    }
    [System.Windows.Forms.Application]::Exit()
})

$notifyIcon.ContextMenuStrip = $contextMenu
$notifyIcon.Add_DoubleClick({ Open-Dashboard })
$notifyIcon.ShowBalloonTip(3000, 'Jangbogo Tray', 'Right-click for dashboard link and service controls.', [System.Windows.Forms.ToolTipIcon]::Info)

[System.Windows.Forms.Application]::Run()
