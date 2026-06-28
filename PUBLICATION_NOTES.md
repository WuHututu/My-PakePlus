# Publication Notes

This repository is a source publication copy of the SPlayer Android wrapper built from PakePlus-Android.

The following local-only files are intentionally not published:

- `pakeplus.keystore`
- `local.properties`
- `node_modules/`
- `.gradle/`
- APK/AAB build outputs
- crash logs and build logs

The public copy uses `http://YOUR_SERVER_IP:25884/#/` as the placeholder WebView URL. Replace it in `app/src/main/assets/app.json` and `scripts/ppconfig.json` before building your own APK.

Release signing is intentionally not hard-coded. To build a signed release, provide these Gradle properties or environment variables:

```powershell
$env:SPLAYER_KEYSTORE="C:\path\to\release.keystore"
$env:SPLAYER_KEYSTORE_PASSWORD="your-store-password"
$env:SPLAYER_KEY_PASSWORD="your-key-password"
$env:SPLAYER_KEY_ALIAS="your-key-alias"
.\gradlew.bat assembleRelease
```

Without those values, `assembleRelease` falls back to Android debug signing so the public repository stays buildable without publishing private signing material.
