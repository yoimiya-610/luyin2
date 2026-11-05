$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $jdk = Get-ChildItem "$projectRoot\.toolchain\jdk" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $jdk) { throw '请先安装 JDK 17 并设置 JAVA_HOME，或将 JDK 解压到 .toolchain/jdk/ 下。' }
    $env:JAVA_HOME = $jdk.FullName
}
$env:ANDROID_USER_HOME = "$projectRoot\.toolchain\android-user"
$env:GRADLE_USER_HOME = "$projectRoot\.toolchain\gradle-home"
New-Item -ItemType Directory -Force "$projectRoot\.toolchain\tmp" | Out-Null
# Avoid Windows short-name temp paths breaking JDK Unix-domain loopback pipes.
$env:JAVA_TOOL_OPTIONS = "-Djava.io.tmpdir=$projectRoot\.toolchain\tmp -Djdk.net.unixdomain.tmpdir=$projectRoot\.toolchain\tmp"
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = "$projectRoot\.toolchain\sdk" }
if (-not (Test-Path "$projectRoot\.toolchain\debug.keystore")) {
    & "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -keystore "$projectRoot\.toolchain\debug.keystore" -storepass android -keypass android -alias androiddebugkey -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000
    if ($LASTEXITCODE -ne 0) { throw '生成调试签名失败' }
}
& "$projectRoot\.toolchain\gradle-8.11.1\bin\gradle.bat" --no-daemon assembleDebug lintDebug testDebugUnitTest
if ($LASTEXITCODE -ne 0) { throw 'Android 构建或检查失败' }
New-Item -ItemType Directory -Force "$projectRoot\output" | Out-Null
Copy-Item -LiteralPath "$projectRoot\app\build\outputs\apk\debug\app-debug.apk" -Destination "$projectRoot\output\录音随听.apk" -Force
Write-Output "APK: $projectRoot\output\录音随听.apk"
