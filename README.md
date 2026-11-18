# Earacle

🔮👂 **The oracle for your ears – recognizes songs from the radio and shows the lyrics live, perfectly in sync.**

Earacle (ear + oracle) is a private Android app. It listens through the microphone to music playing nearby (for
example a radio), recognizes the song and shows its lyrics line by line, in sync with the music.

The app will be available in German and English.

## Status

**Early stage.** The app is only a skeleton so far: it opens a single placeholder screen and does nothing
else yet. Everything below under "Features" and "How it works" describes what Earacle is planned to do, not
what it does today.

## Features (planned)

- Listens to nearby music through the microphone, also with the screen off
- Recognizes the song that is playing
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

Every pull request and every change on `main` builds a debug APK in GitHub Actions. To install it:

1. On GitHub, open **Actions → Build**, pick the latest green run of the branch you want (usually `main`)
   and download **earacle-debug-apk** under "Artifacts" (kept for 30 days). It is a zip file; unpack it to
   get the `.apk`.
2. Install it on your own phone, for example with `adb install app-debug.apk`.

Each build is signed with a temporary debug key that changes from run to run. If Android refuses to install
a newer build over an older one, uninstall the old one first (this deletes the app's data).

## Development

- Earacle is built together with AI assistants. Instructions for AI tools are in [AGENTS.md](AGENTS.md)
  (for Claude: [CLAUDE.md](CLAUDE.md)).
- Kotlin + Jetpack Compose, one module (`app`). Versions are kept in `gradle/libs.versions.toml`.
- Build locally with Android Studio or `./gradlew assembleDebug` (JDK 17 or newer, Android SDK).
- GitHub Actions builds the debug APK on every pull request and every push to `main`
  (`.github/workflows/build.yml`) and scans every push and pull request for leaked secrets with gitleaks
  (`.github/workflows/gitleaks.yml`).
- There are no automated tests yet. Recognition and sync are checked on a real phone.

## Credits

- [LRCLIB](https://lrclib.net) for synced lyrics
- SongRec, ShazamIO and MusicRecognizer (Audile) as references for the Shazam-based recognition

## License

GNU AGPL-3.0, see [LICENSE](LICENSE).
