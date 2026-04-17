Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$ErrorActionPreference = 'Stop'

$baseDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$serviceExe = Join-Path $baseDir 'service\jangbogo-service.exe'
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
    [System.Windows.Forms.Application]::Exit()
})

$notifyIcon.ContextMenuStrip = $contextMenu
$notifyIcon.Add_DoubleClick({ Open-Dashboard })
$notifyIcon.ShowBalloonTip(3000, 'Jangbogo Tray', 'Right-click for dashboard link and service controls.', [System.Windows.Forms.ToolTipIcon]::Info)

[System.Windows.Forms.Application]::Run()
