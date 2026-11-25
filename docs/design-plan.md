# Design plan: Material 3 Expressive

Decided by the owner on 2026-11-25. The app looked dated in places; the owner picked proposal **B
("Expressive")** out of three (A: tidy standard Material 3, B: Material 3 Expressive, C: B plus colours taken
from the album cover). Mock-ups of all three, the three loading animations and the chosen sequence are on the
preview page made in that session (a private claude.ai artifact of the owner's:
https://claude.ai/artifact/SznkPjaPNjAG9iNvpcZrg2; a later session can read it with the Artifact tool).

Step 1 (start screen and listening animation) is built on the branch `feature/expressive-start-screen`; it needs
Material 3 1.5.0 (Compose BOM 2026.11.01), because 1.4.0 (BOM 2026.09.00) has none of the Expressive components
(checked in its sources: `LoadingIndicator`, `MaterialShapes` and `CircularWavyProgressIndicator` are missing,
`MaterialExpressiveTheme` is internal). Steps 2 and 3 are not built yet.

## Why it looked dated

- The start screen is an empty screen with the word "Earacle" and three buttons stacked, two of them plain
  text buttons; nothing shows that the app listens (no microphone).
- Search and history have a text button "Back" at the top instead of a top bar with an arrow.
- In the lyrics, the line being sung differs from the others only by colour, not by size or weight.
- The buttons under the lyrics sit loosely in one row, without a common bar.

## Ground rules

- Keep the colours of the phone (Material You, dynamic colour, as now) and the stronger fill colour of point 10
  of `docs/karaoke-plan.md` (`LocalLyricFill`).
- Stage mode stays as it is.
- Settings and the history stay as they are (no change to stored data).
- Every new or changed text in English and German, in the same change.
- No new library. Material 3 Expressive components come from `androidx.compose.material3`, which the project
  already uses. Several of them are marked `@ExperimentalMaterial3ExpressiveApi`; before building, check at the
  source which ones the Material 3 version in the Compose BOM of `gradle/libs.versions.toml` (2026.09.00 on
  2026-11-25) contains and with which parameters (e.g. `LoadingIndicator`, `ContainedLoadingIndicator`,
  `CircularWavyProgressIndicator` and whether its wave amplitude can be set per frame, `MaterialShapes`,
  `HorizontalFloatingToolbar`, `MaterialExpressiveTheme`). Where a needed one is missing, say so to the owner
  before working around it.
- The back gesture behaviour (`BackNavigation.kt`) stays. Predictive back (the system shows the screen behind
  while swiping) should keep working with the new top bars.
- One screen per pull request, each tried on the phone with a test APK (Actions -> Build -> Run workflow on the
  branch) before merging.

## Order of work

1. **Start screen and listening animation** (the biggest visible change).
2. **Lyrics screen** (song card, emphasized current line, floating toolbar).
3. **Search and history** (top bar with back arrow, rounded search field, list styling).

## 1. Start screen

- At the top a rounded search bar "Search song" / "Song suchen" (like Google's own apps) that opens the search;
  the history icon sits at its end and opens the history.
- A large emphasized headline: "What's playing?" / "Was läuft gerade?".
- In the middle the big recognize button: a Material 3 Expressive shape with a wavy edge (9-scallop "cookie",
  `MaterialShapes.Cookie9Sided` if available), colour `primary`, the microphone icon in `onPrimary`. The button
  has an accessible label ("Recognize song" / "Lied erkennen").
- Below, "Recently heard" / "Zuletzt gehört": the last two or three songs of the history as rounded list items
  (title, artist, when), opening like an entry of the history page. Hidden when the history is empty.
- The not-recognized, error and permission messages appear on this screen as now, in the new style.

### Listening and recognizing animation

One element in the middle of the screen; **the microphone icon has the same size in all phases** (the size it
has while listening, about 1.8 times a normal 24 dp icon, i.e. about 44 dp).

| Phase | Title | What is shown |
| --- | --- | --- |
| Idle | "What's playing?" | The cookie-shaped button, at rest, microphone on it. |
| Recording, 12 s | "Listening…" / "Höre zu…" | **Only the microphone** (in `primary`, no button shape behind it) **and a wavy ring around it.** The ring fills clockwise from the top in the 12 s of the recording (`UiState.Listening.progress`). The height of its waves follows the loudness at the microphone: almost flat when silent, higher with the beat of the music, so one sees whether the music arrives. A faint full circle shows the track the ring fills. Hint below as now ("Hold the phone near the music." / "Halte das Handy nah an die Musik."). |
| Waiting for Shazam, about 1-3 s | "Recognizing…" / "Erkenne…" | The ring disappears; the Material 3 Expressive loading indicator (shapes morphing into each other while turning, `LoadingIndicator`) in `primary`, the microphone in `onPrimary` on it, same size as before. |
| Match | - | The lyrics screen. |

The owner rejected a version where the cookie shape stayed under the microphone while the ring waved around it
("doubled, not nice"): while recording there is only the ring.

The loudness needs a level from the recorder while it records. `MicrophoneRecorder.record` reports only the
progress now; it has to report a level too (e.g. the RMS of the last chunk, scaled and smoothed), without
changing what is sent to Shazam. The silence check of listening along (RMS below 60) shows the scale. Keep the
mapping from RMS to wave height a pure function with a unit test. Listening along (the automatic recordings
after a match) shows no animation, only the "listening" hint on the lyrics screen.

Built in `StartScreen.kt` and `audio/LoudnessLevel.kt`: the level is `waveAmplitude(chunkRms(...))` of each
chunk the recorder reads (log scale from `SILENCE_RMS` 60 to `LOUD_RMS` 4000, floor 0.05 so the wave keeps
moving), smoothed with `smoothAmplitude` (fast up, slow down). `CircularWavyProgressIndicator` reads the amplitude
lambda while drawing but eases every change over about 0.5 s (its own tween, checked in the 1.5.0 source), so the
waves follow the loudness in half-second steps, not each single beat.

Respect the system setting for reduced animations: then the ring only fills, without waves, and the loading
indicator may stay still.

## 2. Lyrics screen

- At the top a song card (rounded container): back arrow (same as the back gesture: to the start screen), title
  in an emphasized style, artist below, and a small "listening" / "hört mit" badge while listening along runs.
- The current line large and bold (emphasized typography), the next line a little smaller and brighter than the
  rest, the other lines muted as now. Line fill, word timing, backing vocals and countdown keep working; check
  that the larger line still wraps instead of being cut.
- At the bottom a floating toolbar (pill shaped) with history, stage mode, back to the current line (only while
  scrolled away), and the main action "Try again" / "Nochmal" as its emphasized button. The "Search song" button
  for songs without synced lyrics stays reachable.

## 3. Search and history

- A top bar with a back arrow and the title instead of the text button "Back".
- The search field rounded like the search bar on the start screen.
- Result and history entries as rounded list items; swipe to delete and "Undo" in the history stay as they are
  (keep the `HistoryRow` fix for the swipe state).

## Not chosen

- **A (tidy)**: too close to what there is.
- **C (album cover colours)**: needs an image library and loading cover images from the net; songs picked by
  hand have no cover. Can be added after B if the owner wants it.
