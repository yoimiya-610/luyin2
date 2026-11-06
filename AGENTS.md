# Project workflow

- Android app: `cn.luyin.player`, project name 录音随听; keep existing app data and signing identity compatible.
- The user requests a checkpoint commit and push before starting a new update, and a commit and push after each completed update.
- Git remote: `origin`, GitHub repository `yoimiya-610/luyin2`; default branch `main`. Do not force-push.
- Before an update is published, run `scripts/test.ps1` and `scripts/build.ps1` (build, Lint, Android JVM tests), verify APK signing, and update `output/验证记录.md`.
- Include source, documentation, and `output/录音随听.apk`; exclude `.toolchain`, build caches, credentials, and signing keys.
- Keep UI text functional. Put explanatory instructions in 使用帮助 and README rather than permanent page subtitles.
