$ErrorActionPreference = 'Stop'

$baseDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$desktop = [Environment]::GetFolderPath('Desktop')
$startMenu = [Environment]::GetFolderPath('Programs')
$faviconPath = Join-Path $baseDir 'img\favicon.ico'
$shortcutIcon = if (Test-Path $faviconPath) { "$faviconPath,0" } else { "$env:SystemRoot\System32\shell32.dll,220" }
$dashboardIconFile = if (Test-Path $faviconPath) { $faviconPath } else { "$env:SystemRoot\System32\shell32.dll" }
$dashboardIconIndex = if (Test-Path $faviconPath) { 0 } else { 220 }

$shell = New-Object -ComObject WScript.Shell

# 1) Tray app shortcut on Desktop
$trayShortcutPath = Join-Path $desktop 'Jangbogo Tray.lnk'
$trayShortcut = $shell.CreateShortcut($trayShortcutPath)
$trayShortcut.TargetPath = 'powershell.exe'
$trayShortcut.Arguments = '-NoProfile -ExecutionPolicy Bypass -STA -WindowStyle Hidden -File "' + (Join-Path $baseDir 'Jangbogo-Tray.ps1') + '"'
$trayShortcut.WorkingDirectory = $baseDir
$trayShortcut.Description = 'Launch Jangbogo tray app'
$trayShortcut.IconLocation = $shortcutIcon
$trayShortcut.Save()

# 2) Dashboard URL shortcut on Desktop
$dashboardShortcutPath = Join-Path $desktop 'Jangbogo Dashboard.url'
@(
    '[InternetShortcut]'
    'URL=http://127.0.0.1:8282'
    "IconFile=$dashboardIconFile"
    "IconIndex=$dashboardIconIndex"
) | Set-Content -Path $dashboardShortcutPath -Encoding ASCII

# 3) Start Menu shortcut
$startMenuShortcutPath = Join-Path $startMenu 'Jangbogo Tray.lnk'
$startMenuShortcut = $shell.CreateShortcut($startMenuShortcutPath)
$startMenuShortcut.TargetPath = 'powershell.exe'
$startMenuShortcut.Arguments = '-NoProfile -ExecutionPolicy Bypass -STA -WindowStyle Hidden -File "' + (Join-Path $baseDir 'Jangbogo-Tray.ps1') + '"'
$startMenuShortcut.WorkingDirectory = $baseDir
$startMenuShortcut.Description = 'Launch Jangbogo tray app'
$startMenuShortcut.IconLocation = $shortcutIcon
$startMenuShortcut.Save()

# 4) Restart Tray shortcuts (for cases where the tray icon disappears after reboot)
$restartTrayArgs = '-NoProfile -ExecutionPolicy Bypass -STA -WindowStyle Hidden -File "' + (Join-Path $baseDir 'Jangbogo-Tray.ps1') + '" -Restart'

$restartDesktopPath = Join-Path $desktop 'Restart Jangbogo Tray.lnk'
$restartDesktop = $shell.CreateShortcut($restartDesktopPath)
$restartDesktop.TargetPath = 'powershell.exe'
$restartDesktop.Arguments = $restartTrayArgs
$restartDesktop.WorkingDirectory = $baseDir
$restartDesktop.Description = 'Restart Jangbogo tray icon (use when the icon is missing after reboot)'
$restartDesktop.IconLocation = $shortcutIcon
$restartDesktop.Save()

$restartStartMenuPath = Join-Path $startMenu 'Restart Jangbogo Tray.lnk'
$restartStartMenu = $shell.CreateShortcut($restartStartMenuPath)
$restartStartMenu.TargetPath = 'powershell.exe'
$restartStartMenu.Arguments = $restartTrayArgs
$restartStartMenu.WorkingDirectory = $baseDir
$restartStartMenu.Description = 'Restart Jangbogo tray icon (use when the icon is missing after reboot)'
$restartStartMenu.IconLocation = $shortcutIcon
$restartStartMenu.Save()

Write-Host 'Shortcuts created:'
Write-Host " - $trayShortcutPath"
Write-Host " - $dashboardShortcutPath"
Write-Host " - $startMenuShortcutPath"
Write-Host " - $restartDesktopPath"
Write-Host " - $restartStartMenuPath"
Write-Host ''
Write-Host 'Pin to taskbar:'
Write-Host '  1) Right-click "Jangbogo Tray" on Desktop'
Write-Host '  2) Select "Pin to taskbar"'
Write-Host ''
Write-Host 'Tip: If the tray icon is missing after a Windows reboot, double-click'
Write-Host '     "Restart Jangbogo Tray" on Desktop or run Restart-Tray.bat.'
