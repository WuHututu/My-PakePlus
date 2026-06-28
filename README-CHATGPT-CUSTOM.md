# PakePlus Android SPlayer Custom Build

This repository is a customized PakePlus-Android source tree for packaging SPlayer as an Android APK.

## Upstream and License

- Upstream PakePlus: <https://github.com/Sjj1024/PakePlus>
- Upstream PakePlus-Android: <https://github.com/Sjj1024/PakePlus-Android>
- License: MIT License, preserved in `LICENSE`

## Custom Work

The SPlayer-specific Android wrapper changes in this repository were implemented by ChatGPT / Codex under user direction. They include Android media-session integration, background playback handling, file chooser support for WebView, local cache/audio proxy support, logging improvements, app icon/resource updates, and build-publication cleanup.

## Build

Set your SPlayer web deployment URL in:

- `app/src/main/assets/app.json`
- `scripts/ppconfig.json`

Then build:

```powershell
.\gradlew.bat assembleDebug
```

For signed releases, see `PUBLICATION_NOTES.md`.
