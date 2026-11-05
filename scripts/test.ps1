$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = (Get-ChildItem .toolchain\jdk -Directory | Select-Object -First 1).FullName }
New-Item -ItemType Directory -Force .toolchain\tests | Out-Null
& "$env:JAVA_HOME\bin\javac.exe" -encoding UTF-8 -d .toolchain\tests app\src\main\java\cn\luyin\player\SourceRules.java app\src\main\java\cn\luyin\player\NameRules.java app\src\main\java\cn\luyin\player\SegmentLedger.java tests\SourceRulesTest.java tests\RecordingFeatureTest.java
if ($LASTEXITCODE -ne 0) { throw '测试编译失败' }
& "$env:JAVA_HOME\bin\java.exe" -cp .toolchain\tests cn.luyin.player.SourceRulesTest
if ($LASTEXITCODE -ne 0) { throw '来源分类测试失败' }
& "$env:JAVA_HOME\bin\java.exe" -cp .toolchain\tests cn.luyin.player.RecordingFeatureTest
if ($LASTEXITCODE -ne 0) { throw '长录音与命名规则测试失败' }
