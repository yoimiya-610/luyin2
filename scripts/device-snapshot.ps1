param(
    [string]$DeviceSerial,
    [ValidateSet('baseline','idle','recording','spectrum','trial','after')][string]$Label='baseline'
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$adb=Join-Path $projectRoot '.toolchain/sdk/platform-tools/adb.exe'
if(-not(Test-Path -LiteralPath $adb)){throw '未找到 adb，请安装 Android Platform Tools。'}
$devices=@(& $adb devices)
if(-not $DeviceSerial){
    $connected=@($devices | Where-Object {$_ -match '^\S+\s+device$'} | ForEach-Object {($_ -split '\s+')[0]})
    if($connected.Count -ne 1){throw '需要连接并授权一台手机；多设备时请使用 -DeviceSerial 指定。'}
    $DeviceSerial=$connected[0]
}
function Read-Adb([string[]]$Arguments){
    $result=& $adb -s $DeviceSerial @Arguments
    if($LASTEXITCODE -ne 0){throw ('ADB 读取失败：'+($Arguments -join ' '))}
    return $result
}
if((Read-Adb -Arguments @('get-state')) -ne 'device'){throw '手机未授权或未连接。'}
$directory=Join-Path $projectRoot ('.toolchain/device-tests/'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+$Label)
New-Item -ItemType Directory -Force -Path $directory | Out-Null
# Local-only diagnostics: no install, data reset, log clearing, battery reset or device settings changes.
$device=[ordered]@{
    capturedAt=(Get-Date -Format o)
    label=$Label
    manufacturer=((Read-Adb -Arguments @('shell','getprop','ro.product.manufacturer')) -join '').Trim()
    model=((Read-Adb -Arguments @('shell','getprop','ro.product.model')) -join '').Trim()
    android=((Read-Adb -Arguments @('shell','getprop','ro.build.version.release')) -join '').Trim()
    sdk=((Read-Adb -Arguments @('shell','getprop','ro.build.version.sdk')) -join '').Trim()
}
$device | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $directory 'device.json') -Encoding UTF8
(Read-Adb -Arguments @('shell','dumpsys','package','cn.luyin.player')) | Select-String 'versionCode=|versionName=|userId=' | ForEach-Object Line | Set-Content -LiteralPath (Join-Path $directory 'app.txt') -Encoding UTF8
(Read-Adb -Arguments @('shell','dumpsys','battery')) | Set-Content -LiteralPath (Join-Path $directory 'battery.txt') -Encoding UTF8
(Read-Adb -Arguments @('shell','dumpsys','meminfo','cn.luyin.player')) | Set-Content -LiteralPath (Join-Path $directory 'memory.txt') -Encoding UTF8
(Read-Adb -Arguments @('shell','dumpsys','batterystats','cn.luyin.player')) | Set-Content -LiteralPath (Join-Path $directory 'battery-stats.txt') -Encoding UTF8
(Read-Adb -Arguments @('shell','dumpsys','cpuinfo')) | Select-String 'cn.luyin.player' | ForEach-Object Line | Set-Content -LiteralPath (Join-Path $directory 'cpu.txt') -Encoding UTF8
(Read-Adb -Arguments @('shell','dumpsys','power')) | Select-String 'RecordingFlow:Capture|cn.luyin.player|mWakefulness=|mIsPowered=' | ForEach-Object Line | Set-Content -LiteralPath (Join-Path $directory 'wake-state.txt') -Encoding UTF8
Write-Output ('诊断快照已保存到 '+$directory+'；日志留在本机，不上传仓库。USB 充电期间的电量变化不能作为实际耗电结论。')
