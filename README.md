# Earacle

🔮👂 **The oracle for your ears – recognizes songs from the radio and shows the lyrics live, perfectly in sync.**

Earacle (ear + oracle) is a personal Android app. It listens through the microphone to music playing nearby (for
example a radio), recognizes the song and shows its lyrics line by line, in sync with the music.

The app will be available in German and English.

## Status

**Early stage (version 0.3.0).** Earacle can now recognize a song: tap "Recognize song", it listens through the
microphone for 12 seconds, asks the unofficial Shazam service and shows the title, the artist, the current
position in the song and the lyrics from LRCLIB, with the current line highlighted and kept in the middle (scroll by hand to read ahead; a round button with a sync arrow jumps back to the current line). While the app is open it keeps listening along: it recognizes the song
again (every 27 seconds at first, then less often while the song is confirmed, and always around the end of the
song), skips silence, follows song changes and corrects drift; it stops after about two minutes without music. Background
listening is not built yet; it is listed under "Features (planned)".

## Features (planned)

- Listens to nearby music through the microphone, also with the screen off
- Recognizes the song that is playing (works today: title, artist and position in the song)
- Shows synced lyrics, one line at a time, following the music
- Notices when the song changes
- Manual offset slider to fix the timing by hand
- Interface in German and English

## How it works

Planned design:

1. **Listening:** the microphone is captured in a foreground service, so listening continues in the background.
2. **Recognition:** the song is identified through the unofficial (reverse-engineered) Shazam API, as used by
   open-source projects such as SongRec, ShazamIO and MusicRecognizer (Audile).
3. **Lyrics:** synced lyrics (LRC format) come from [LRCLIB](https://lrclib.net), which is free and needs no
   API key.
4. **Sync:** the position in the song comes from the recognition result plus the time elapsed since then. Earacle
   re-recognizes the song every 20-30 seconds to correct drift. If the timing is still off, the manual offset
   slider is the fallback.

**Please note:** the unofficial Shazam API is not endorsed by Apple or Shazam and may break at any time. Earacle
is a personal project and is not published in any store.

Built with Kotlin and Jetpack Compose.

## Install

Download the APK from the latest release:

1. On your phone, open the repository's **Releases** page on GitHub and download `earacle-vX.Y.Z.apk` from
   the latest release.
2. Open the file and install it (allow installing from this source if Android asks), or install it from a
   computer with `adb install -r earacle-vX.Y.Z.apk`.

Releases are signed with a fixed key kept in the repository's GitHub secrets `EARACLE_KEYSTORE_BASE64` and
`EARACLE_KEYSTORE_PASSWORD`, so a newer version installs over the old one and keeps the app's data. Keep a
backup of the keystore and its password outside GitHub (GitHub secrets cannot be read back).

To try a branch before it is merged: **Actions → Build → Run workflow**, pick the branch, then download
**earacle-test-apk** under "Artifacts" on the run's page (kept for one day; a zip file with the `.apk` inside).

## Release

1. Raise `versionName` and `versionCode` in `app/build.gradle.kts` in a pull request and merge it.
2. **Actions → Release → Run workflow**, enter the version with a `v` (for example `v0.4.0`), **Run workflow**.

The workflow checks the version, builds and signs the APK, creates the tag and the GitHub Release and attaches
the APK.

## Development

- Earacle is built together with AI assistants. Instructions for AI tools are in [AGENTS.md](AGENTS.md)
  (for Claude: [CLAUDE.md](CLAUDE.md)).
- Kotlin + Jetpack Compose, one module (`app`). Versions are kept in `gradle/libs.versions.toml`.
- Build locally with Android Studio or `./gradlew assembleDebug` (JDK 17 or newer, Android SDK).
- GitHub Actions runs the unit tests and checks that the app compiles on every pull request
  (`.github/workflows/build.yml`), builds releases (`.github/workflows/release.yml`) and scans every push and
  pull request for leaked secrets with gitleaks (`.github/workflows/gitleaks.yml`).
- Unit tests (JUnit 4) for the fingerprint, the Shazam request and answer handling and the position maths live
  in `app/src/test`. Run them with `./gradlew testDebugUnitTest`; CI runs them on every pull request.
  Microphone, real recognition and the screen can only be checked on a real phone.

## Credits

- [LRCLIB](https://lrclib.net) for synced lyrics
- SongRec, ShazamIO and MusicRecognizer (Audile) as references for the Shazam-based recognition
- The audio fingerprinting (`app/src/main/java/io/github/drnerdlove/earacle/recognition`) is ported from
  [SongRec](https://github.com/marin-m/SongRec) by marin-m (GPL-3.0-or-later), used under this project's AGPL-3.0

## License

GNU AGPL-3.0, see [LICENSE](LICENSE).
