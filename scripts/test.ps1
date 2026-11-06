$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = (Get-ChildItem .toolchain\jdk -Directory | Select-Object -First 1).FullName }
New-Item -ItemType Directory -Force .toolchain\tests | Out-Null
& "$env:JAVA_HOME\bin\javac.exe" -encoding UTF-8 -d .toolchain\tests app\src\main\java\cn\luyin\player\SourceRules.java app\src\main\java\cn\luyin\player\NameRules.java app\src\main\java\cn\luyin\player\SegmentLedger.java app\src\main\java\cn\luyin\player\WaveformBuffer.java app\src\main\java\cn\luyin\player\ExportFiles.java app\src\main\java\cn\luyin\player\CaptureFormat.java app\src\main\java\cn\luyin\player\WavHeader.java tests\SourceRulesTest.java tests\RecordingFeatureTest.java tests\ExportWaveformTest.java tests\RecordingFormatsTest.java app/src/main/java/cn/luyin/player/AudioFft.java app/src/main/java/cn/luyin/player/PitchDetector.java app/src/main/java/cn/luyin/player/SoundFrame.java app/src/main/java/cn/luyin/player/StabilityMetrics.java app/src/main/java/cn/luyin/player/AcousticAnalyzer.java app/src/main/java/cn/luyin/player/AdtsHeader.java app/src/main/java/cn/luyin/player/PianoKeys.java app/src/main/java/cn/luyin/player/PianoSynth.java app/src/main/java/cn/luyin/player/LiveAnalysis.java tests/AcousticAnalysisTest.java
if ($LASTEXITCODE -ne 0) { throw '测试编译失败' }
& "$env:JAVA_HOME\bin\java.exe" -cp .toolchain\tests cn.luyin.player.SourceRulesTest
if ($LASTEXITCODE -ne 0) { throw '来源分类测试失败' }
& "$env:JAVA_HOME\bin\java.exe" -cp .toolchain\tests cn.luyin.player.RecordingFeatureTest
if ($LASTEXITCODE -ne 0) { throw '长录音与命名规则测试失败' }
& "$env:JAVA_HOME\bin\java.exe" -cp .toolchain\tests cn.luyin.player.ExportWaveformTest
if ($LASTEXITCODE -ne 0) { throw '波形与导出测试失败' }
& "$env:JAVA_HOME\bin\java.exe" -cp .toolchain\tests cn.luyin.player.RecordingFormatsTest
if ($LASTEXITCODE -ne 0) { throw '录音格式测试失败' }

& "$env:JAVA_HOME\bin\java.exe" -cp .toolchain\tests cn.luyin.player.AcousticAnalysisTest
if ($LASTEXITCODE -ne 0) { throw '声学分析与钢琴合成测试失败' }
