# Karaoke plan

Six features that make Earacle better for singing along. They were chosen by the owner on 2026-11-24 after a
look at how Apple Music (Sing), Spotify, Musixmatch and YouTube Music show lyrics. Each point is done in its
own session, branch and pull request, **one after the other, in the order below**: all of them change the
lyrics screen (`MainActivity.kt`), so running them in parallel would cause conflicts, and later points build
on earlier ones.

**How to use this plan in a new session:** the owner writes "Setze Punkt N aus docs/karaoke-plan.md um."
The session then:

1. Checks that every earlier point is marked done below and merged into `main` (points 7 to 9 have their
   own order, see "Second round"). If not, it says so and asks before going on.
2. Creates the branch named for the point from the latest `main`.
3. Builds the point as described, with the tests and sabotage checks AGENTS.md asks for.
4. In the same pull request: updates the README ("Features", "Planned" where it applies), the relevant part
   of "Project knowledge" in AGENTS.md if a decision or trap is worth keeping, and ticks the point off below
   (with the PR number).
5. Tells the owner how to try it on the phone (Actions -> Build -> Run workflow on the branch, artifact
   `earacle-test-apk`) and exactly what to look for.

Not part of this plan (decided against, or later): lowering the singer's voice and scoring the singing (the
radio plays the music, not the app, and no free source has the vocal melody), translations (needs a new
paid or limited service that would read the lyrics), listening in the background (less important while
singing, since one looks at the screen anyway).

## Status

- [x] 1. Tap to sync and fine offset - branch `feature/tap-to-sync` 
- [x] 2. Countdown before singing starts again - branch `feature/countdown`, PR #29
- [x] 3. The current line fills up - branch `feature/line-fill`, PR #30
- [x] 4. Word timing where the lyrics have it - branch `feature/word-timing`, PR #32
- [x] 5. Backing vocals smaller and muted - branch `feature/backing-vocals`, PR #33
- [x] 6. Stage mode - branch `feature/stage-mode`, PR #35
- [ ] 7. Search a song by hand - branch `feature/manual-search`
- [ ] 8. Recently heard songs - branch `feature/song-history`
- [ ] 9. Real word and line times from LRCLIB's lyricsfile - branch `feature/lyricsfile` (proposed, waiting
  for the owner's decision)

## Ground rules for all points

- **Pure logic, testable.** Every calculation (offset, countdown, fill progress, parsing) lives in plain
  Kotlin without `android.*` or Compose imports, in `app/src/main/java/io/github/drnerdlove/earacle/lyrics/`
  (or `recognition/` for positions), with JUnit 4 tests in `app/src/test`. The Compose code only draws what
  these functions return. How to run the tests in a cloud session is in AGENTS.md ("Build and test").
- **Texts in both languages.** Every new text (labels, and the `contentDescription` of every icon button)
  goes into `values/strings.xml` and `values-de/strings.xml` in the same change.
- **State that must survive a rotation** (the activity has no fixed orientation, so turning the phone
  recreates it) lives in `MainViewModel` / `UiState`, not in `remember { }`.
- **Smooth drawing.** `FoundContent` updates the position every 200 ms today, which is fine for jumping from
  line to line but too coarse for points 2 and 3. Those points move to a per-frame clock
  (`withFrameMillis`) and read the position where it is drawn (for example in `drawWithContent` or a
  `graphicsLayer`), so a frame redraws one line instead of recomposing the whole list. The LazyColumn must
  not recompose 60 times a second.
- **What is checked where.** Unit tests cover the logic. Whether the timing feels right, the animation is
  smooth and the layout fits can only be checked on the owner's phone; every report says so plainly.
- **Tunable constants in one place.** Values that will need tuning on the phone (character speed, minimum
  pause, step sizes) are named constants next to the logic that uses them, with a comment on what they mean.

## What the real lyrics look like

Checked on 2026-11-24 against the real LRCLIB API (`/api/search` for ten popular songs in several languages,
200 entries, 159 with synced lyrics, 9,023 lines):

- **Word timing:** none in the `syncedLyrics` field: not one entry had per-word timestamps (`<mm:ss.xx>`,
  the "A2" or enhanced LRC format) or timestamps in the middle of a line. Point 4 is therefore small and
  will rarely show. **Correction (2026-11-25):** this check only looked at `syncedLyrics` and missed that
  LRCLIB answers also carry a `lyricsfile` field (YAML with line end times and word times) and a
  `hasWordSync` flag. In the same sample, 52 of the 159 synced entries (33 %) have word timing there, and all
  159 have line end times. Point 9 uses them.
- **Backing vocals in parentheses:** 1,102 lines (12 %), e.g. `Looking out for a place to go (hmm)`,
  `(See you groove)`, `Dancing (Dancing)`. Point 5 matters.
- **Empty lines** (instrumental parts, shown as ♪ today): 332, about two per song.
- **Gaps between lines:** median 3.0 s; 357 gaps (4 %) are longer than 8 s, about two per song. These are
  where the countdown of point 2 helps.
- **Singer markers** for duets (`M:`, `F:`, `D:` before the text): none. A duet view like Apple's is not
  possible with this data and is not planned.

## 1. Tap to sync and fine offset

**Why:** Shazam's position, the radio's delay and the timing in the LRC file are often off by a second or
more. Today nothing can be done about it. For singing along, the line has to change when the singer starts
it.

**What the owner sees:**

- Tapping a lyric line means "this line is being sung right now": the display shifts so that this line
  becomes the current one at this moment, and the list follows the current line again (as the sync button
  does). Taps only count for lines up to 30 s before or after the current position, so a stray tap while
  scrolling far away cannot throw the timing off; tapping a line farther away does nothing.
- A small row under the position: `−0.5 s`, the current shift (e.g. `+1.5 s`, or `±0 s`), `+0.5 s` (which way each button moves the lyrics: see "Direction" below). Tapping the
  shown value sets it back to zero.
- The shift belongs to the song: it stays when the app listens along and confirms the same song (Shazam
  then corrects the position, the shift is added on top), and it is set back to zero when a different song
  is recognized or "Try again" is used.

**Direction (decide once, write it in the code comment):** shift = milliseconds added to the position. A
positive shift moves the lyrics ahead (the next line comes sooner). The `+0.5 s` button therefore helps when
the lyrics lag behind the singer. The label of the buttons must make this clear without words like
"offset"; suggestion: icons or text "früher / später" for the lyrics. Check the wording with the owner.

**Logic (pure, tested):**

- `syncedPositionMs(rawPositionMs, shiftMs)` = raw + shift.
- `shiftForTap(lineTimeMs, rawPositionMs)` = lineTimeMs − rawPositionMs, so that after the tap the tapped
  line is exactly the current one. Plus a small lead (e.g. 0 ms, tunable) if it feels late on the phone.
- `isTapInRange(lineTimeMs, syncedPositionMs)` for the 30 s rule.
- Clamping: the shift stays within ±30 s.
- The position shown as text ("Position in song") also uses the shifted position.

**Where:** `UiState.Found` gets `shiftMs: Long = 0`; `MainViewModel` gets `tapLine(lineTimeMs)`,
`nudgeShift(deltaMs)`, `resetShift()`. In `trackLoop`, the "same song" branch keeps `shiftMs` (the `copy`
already does), a new song starts at 0. `SongTracking.nextListenDelayMs` keeps using the unshifted position
(the shift is about display, not about when to listen again).

**Tests:** the three functions and the clamping; that the same song keeps the shift and a new song resets it
(as far as testable without Android; otherwise a pure helper that decides it). Sabotage: e.g. swap the sign
in `shiftForTap`.

**On the phone:** play a song, tap the line that is being sung, watch the next line changes come on time;
try `+0.5 s` / `−0.5 s`; let the app listen along once and check the shift stays; switch songs and check it
is back at zero; rotate the phone and check it stays.

## 2. Countdown before singing starts again

**Why:** after an intro or an instrumental part, it is hard to know when to come in again. Apple Music shows
dots in such pauses.

**What the owner sees:** in a long pause (before the first line, after an empty line, or after a line whose
sung part is over while the next one is still far away), three dots appear where the next line will start
(between the current and the next line, or in place of the ♪). They go out one by one in the last 3 s before
the next line (3, 2, 1), and the next line is already shown a little brighter as a preview.

**Logic (pure, tested):**

- `estimatedLineEndMs(lines, index)`: a line has no end time in LRC, so its sung part is estimated:
  `start + min(nextStart − start, max(MIN_LINE_MS, MS_PER_CHAR × visible characters))`, with
  `MIN_LINE_MS = 1500` and `MS_PER_CHAR = 90` as first guesses, tuned on the phone. An empty line ends at its
  start. This function is also used by point 3.
- `countdown(lines, positionMs): Countdown?` returns null outside a long pause, otherwise the index of the
  next line, the time left until it, and how many dots are still lit (3 to 0). A pause is "long" when the
  time from the estimated end of the current line (or from 0 before the first line) to the next line is at
  least `MIN_PAUSE_MS = 6000`. The dots are visible from the start of the pause, all lit, and go out at 3 s,
  2 s and 1 s before the next line.

**Where:** a new composable for the dots in `SyncedLyrics`; it reads the position each frame (see ground
rules). The dots use the theme's colours.

**Tests:** before the first line, after an empty line, after a normal line in a long gap, no countdown in a
short gap, the dot count at the boundaries (exactly 3 s, 2 s, 1 s, 0 s before), the last line of the song
(no next line: no countdown). Sabotage: e.g. off by one in the dot count, or `>=` turned into `>` at
`MIN_PAUSE_MS`.

**On the phone:** a song with a long intro and one with an instrumental break; the dots must go out in time
with the music and the last one must go out when the singer starts.

**Built (point 2):** two choices the plan left open. (a) The dots show `ceil(seconds left)` of three, so with
exactly 3 s, 2 s, 1 s left there are 3, 2, 1 dots and the last one goes out at the start of the line (this matches
"3, 2, 1" and the phone check; the sentence "go out at 3 s, 2 s and 1 s before" would have left the last
second empty). (b) The "next line" is the next line **with text**, and the pause starts at the end of the last
line with text, so an empty line in the middle of a pause does not restart or end the countdown. The preview
(brighter next line) lasts for the whole pause. No new texts, so nothing to translate.

## 3. The current line fills up

**Why:** shows where in the line the singer is, almost like word-by-word karaoke, without needing word
timing in the data.

**What the owner sees:** the current line is drawn in a muted colour and fills from left to right in the
highlight colour while it is being sung, at an even pace from the line's start to its estimated end (point
2). Wrapped lines fill one visual line after the other, not all at once. Once full, the line stays full until
the next one starts. Lines before it are shown normally (not filled); lines after it are unchanged.

**Logic (pure, tested):** `lineProgress(lines, index, positionMs)` from 0 to 1 using
`estimatedLineEndMs`; then `filledCharacters(text, progress)`, counting visible characters, so the fill
moves through the text and wraps with it.

**Drawing:** use the `TextLayoutResult` of the line (`onTextLayout`) to find where the filled character
count ends on each visual line, draw the line once in the muted colour and once in the highlight colour
clipped to the filled part (per visual line), in the draw phase so that only this line redraws each frame.
Centred text: take each visual line's left and right edge from the layout, not 0 and the full width.

**Tests:** progress at start, middle, end and after the end; an empty line; a line whose next line comes
before the estimated end; the character count for text with spaces and multi-byte characters (umlauts,
Korean, emoji must not be cut in half: count by code points or graphemes, test it). Sabotage: e.g. progress
not clamped to 1.

**On the phone:** does the fill keep pace with the singer for normal and fast lines (tune `MS_PER_CHAR`)?
Is it smooth? Are wrapped lines filled in the right order? Light and dark mode.

**Built (point 3):** `lyrics/LineFill.kt` (`lineProgress`, `filledCharacters`, `filledTextIndex`). An empty
line has nothing to fill (progress 0); a line whose next line starts at the same moment is full as soon as it
has started. Characters are counted by code points (as `visibleCharacterCount` does), so emoji and other
characters made of two UTF-16 units are never cut in half; a letter with a separate combining accent may count
as two, which only shifts the fill by one character. Drawing (`FillingLine` in `MainActivity.kt`): the Text is
drawn in the highlight colour in an offscreen `graphicsLayer`, and the part not sung yet is painted over in
the muted colour with `BlendMode.SrcAtop`, so only the letters change colour; per visual line, from
`TextLayoutResult.getHorizontalPosition` to the edge (right-to-left text from the left edge). No experimental
text API is needed. No new texts, so nothing to translate.

## 4. Word timing where the lyrics have it

**Why:** some LRC files (format "A2" / enhanced LRC) have a time for every word:
`[00:12.00] <00:12.00> Hello <00:12.50> world`. LRCLIB had none in the check above, so this will rarely
show; the point is small and keeps such files from showing timestamp clutter.

**What the owner sees:** for a line with word times, the fill of point 3 jumps word by word on the real
times instead of the estimate. Lines without them behave as in point 3. No timestamps are ever shown as text.

**Logic (pure, tested):** `Lrc.parse` recognises `<mm:ss.xx>` tags (and `[mm:ss.xx]` in the middle of a line)
inside the text: they are removed from `LyricLine.text` and kept as a list of (character index, time) in a
new optional field of `LyricLine` (default empty, so nothing else changes). `lineProgress` / the filled
character count use these times when present. Existing parser tests must keep passing.

**Tests:** a line with word tags is parsed to clean text plus the right word times; mixed files; a tag at the
end of a line (end time of the last word); malformed tags are dropped, not shown; the fill uses the word
times. Sabotage: e.g. leave the tags in the text.

**On the phone:** only if a song with word times turns up; otherwise say plainly that it was only checked
by tests. (Optional: the session may search LRCLIB once more for a real example to test with.)

## 5. Backing vocals smaller and muted

**Why:** 12 % of the lines contain backing vocals in parentheses. Apple shows them smaller, so the lead line
is easy to follow and one knows which part is the echo.

**What the owner sees:** text in parentheses is drawn smaller and in a muted colour, in every line. A line
that is all in parentheses is smaller as a whole. In the current line the backing part is muted as well, and
it fills along with the rest (point 3).

**Logic (pure, tested):** `splitBacking(text): List<Segment>` with `Segment(text, backing: Boolean)`. Only
balanced, non-nested `( … )` count; with unbalanced parentheses the whole line stays normal text. Spaces at
the borders are kept, so the joined segments give back the original text exactly (the fill counts on this).

**Drawing:** an `AnnotatedString` with a `SpanStyle` (smaller size, muted colour) for backing segments; the
fill of point 3 keeps working on the same text layout.

**Tests:** `Dancing (Dancing)`, `(See you groove)`, a line with two parts in parentheses, unbalanced
`(oh`, nested, empty `()`, and that joining the segments gives the original text. Sabotage: e.g. mark the
parentheses part as not backing.

**On the phone:** a song with many backing parts; the lead line must stay easy to read.

**Built (point 5):** `lyrics/Backing.kt` (`splitBacking`). Backing segments include their parentheses; an empty
`()` counts as backing. Unbalanced or nested parentheses anywhere in a line leave the whole line normal. In
`MainActivity.kt`, `lyricText` builds the `AnnotatedString` (size 0.75 em, muted colour) for every line, including
the line being sung: there the backing part keeps its muted colour in the already sung part too, so it does not
light up like the lead; the fill still runs over the same text. No new texts, so nothing to translate.

## 6. Stage mode

**Why:** for singing along from a distance (phone on a table or a stand, several people singing), the normal
screen is too small and too busy.

**What the owner sees:**

- A button on the lyrics screen switches stage mode on and off (icon button, next to the sync button;
  content description in both languages).
- In stage mode: black background, very large text, the current line in the middle (much larger than now),
  the next line below it smaller, all other lines hidden. Title and artist shrink to one small line at the
  top (or are hidden), the shift row of point 1 and "Try again" stay reachable but small. Countdown, fill
  and backing vocals work as before.
- Text size can be changed in stage mode with `A−` / `A+` (a few steps).
- Works in portrait and landscape; landscape gives longer lines.
- Stage mode and the text size are remembered after the app is closed (SharedPreferences, no new library).
  Leaving the app or rotating keeps the mode.

**Logic (pure, tested):** the size steps (`nextTextSize(current, up)` with lower and upper limit) and which
lines are visible in stage mode (current and next, also at the start and end of the song and during a
countdown). The rest is layout.

**Tests:** size steps at the limits; visible lines before the first line, in the middle, at the last line.
Sabotage: e.g. no upper limit.

**On the phone:** readable from 2-3 m in portrait and landscape; long lines wrap rather than being cut; the
mode and size are still set after closing and reopening the app; light and dark system theme (stage mode is
always dark).

**Built (point 6):** `lyrics/StageMode.kt` (`nextTextSize`, `clampTextSize`, `stageLines`, `showsStageMode`,
`StageSettings`, `StageStore`) and `StagePreferences.kt` (SharedPreferences, file `stage_mode`). Choices the plan
left open: (a) the text size goes in 5 steps from 32 to 64 sp (`StageText`, default 40, `A−` / `A+` in 8 sp steps);
the next line is drawn at 0.6 of that. (b) The entry button (fullscreen icon) sits at the *left* end of the row
with "Try again", not next to the sync button, so the buttons do not jump when the sync button appears and goes.
(c) Stage mode only applies to a song with synced lyrics (or while they load, so there is no flicker between
songs); plain lyrics and messages use the normal screen, and the saved choice applies again with the next
synced song. While "Try again" listens the screen stays dark. (d) The theme is forced dark with a black
background, and the status and navigation bar icons turn light (`SystemBarStyle.dark`). (e) The block of
current line, dots and next line is centred vertically as a whole, so it moves a little when a line wraps
differently; a line too long for the screen at the chosen size scrolls inside the middle area. (f) The countdown
dots grow with the text (a third of the size). (g) Tapping a line does not set the timing in stage mode (there
is no list); use the `±0.5 s` buttons. Four new texts, in English and German.

## Second round (planned 2026-11-25)

Planned after points 1 to 6 were released as v0.6.0. Point 8 builds on point 7 and comes after it. Point 9
does not depend on 7 and 8 and may be done before, between or after them; it is only built once the owner has
agreed to it (then remove "proposed" from its status line). The ground rules above apply to all three.

At the same time, the owner dropped two older ideas (see "Rejected options" in AGENTS.md): listening in the
background with the screen off, and own recognition with Olaf against the music files on the phone.

## 7. Search a song by hand

**Why:** when Shazam does not recognize a song, or the unofficial Shazam API stops working, the app is
useless today. With a search by hand and tap to sync (point 1), one can still sing along. This is the
fallback for Shazam instead of Olaf.

**What the owner sees:**

- A second, less prominent button **"Song suchen" / "Search song"** under the main button on the start screen,
  and also on the "not recognized" and error screens (that is where it matters most). On the lyrics screen it
  appears when no synced lyrics were found (plain lyrics or "no lyrics found"), with the recognized title
  and artist already filled in, so a better version can be picked.
- A search screen with one text field (title and/or artist, as one types it) and a search button. LRCLIB's
  free search (`/api/search?q=`, all words must appear in title, artist or album) answers with up to 20
  entries. Each result shows title, artist, album, length (m:ss) and whether it has synced lyrics, plain
  lyrics only, or is instrumental. Entries with synced lyrics come first, otherwise LRCLIB's order. Entries
  without any lyrics and not instrumental are left out. Clear messages for "nothing found" and "no
  connection".
- The system back gesture/button leaves the search and returns to the screen before it (`BackHandler`, no
  new navigation library).
- Picking a result opens the lyrics screen with these lyrics, without a second lookup. Since the app does not
  know where in the song the music is, the clock does not run yet: nothing is highlighted, and a hint says
  **"Tippe auf die Zeile, die gerade gesungen wird." / "Tap the line that is being sung right now."** The
  first tap starts the clock at that line; from then on everything works as for a recognized song (fill,
  countdown, backing vocals, stage mode, the ±0.5 s buttons).
- For a song picked by hand, every tap on a line sets the clock to that line (no 30 s limit and no ±30 s
  shift limit as in point 1: without recognition there is nothing else to correct the timing). The ±0.5 s
  buttons work as usual.
- Listening along does not run for a song picked by hand (Shazam may be down, and the search needs no
  microphone). "Try again" recognizes as usual and replaces the song when Shazam finds one.

**Logic (pure, tested):**

- `LrclibApi.buildQueryUrl(query)` (trimmed, URL-encoded; empty query: no request) and the parsing of the
  extra fields the results need: `id` and `albumName` go into `LrclibTrack`.
- `searchResultsForPicker(tracks)`: filtering and order as above.
- Anchoring: `UiState.Found` gets a field for where the song came from (recognized / picked by hand) and
  whether the clock is running yet. A pure helper decides what a tap does: in a recognized song as in point 1
  (`shiftForTap`, range check); in a picked song, it anchors the clock at the tapped line (offset = line time,
  start = now, shift = 0). The position while waiting is the start of the song and nothing counts as current.
- `LrclibClient` gets `search(query): List<LrclibTrack>?` (null on failure), reusing its `get`.

**Where:** a new `UiState` for the search (query, results, loading, error, and the state to return to),
`MainViewModel.openSearch(prefill)`, `search(query)`, `pick(track)`, `closeSearch()`; the search screen as
its own composable in `MainActivity.kt` or a new file next to it. Lyrics of the picked entry via
`LrclibApi.toLyrics`.

**Tests:** URL building (umlauts, spaces, empty), result filtering and order, parsing of `id` and
`albumName` (also missing), the tap rules for both kinds of song, that the waiting state highlights
nothing, and that a picked song starts no listening along (as far as testable without Android; otherwise a
pure helper that decides it). Sabotage: e.g. apply the 30 s limit to picked songs, or sort plain before
synced.

**On the phone:** search a song that is playing, pick it, tap the line being sung, check that the next lines
change on time; search while in airplane mode (clear message); back gesture from the search; search from
the "not recognized" screen; "Try again" after a picked song.

## 8. Recently heard songs

**Why:** to call up a song again quickly (e.g. the same radio hit an hour later, or to sing it again), without
listening or typing.

**What the owner sees:**

- In the search screen of point 7, while the search field is empty: a list "Zuletzt gehört" / "Recently
  heard" with the last 20 songs, newest first, recognized ones and those picked by hand, each with title,
  artist and when (e.g. "heute 14:32" / "today 2:32 PM", or the date). A song that is already in the list is
  not added twice; it moves to the top.
- Tapping an entry opens it like a song picked by hand (point 7: the clock starts at the first tap).
- A button "Verlauf löschen" / "Clear history" with a short confirmation.

**Data and privacy:** the list is kept on the phone only (SharedPreferences, as JSON with org.json, which
Android already has; no new library), never sent anywhere and not logged. Stored per entry: title, artist,
the time, and the LRCLIB `id` of the lyrics that were shown, if any. Opening an entry with an id loads
exactly that version via `/api/get/{id}` (checked 2026-11-25: answers 200 with the same fields as a search
result); without an id, the normal lookup by title and artist runs. The stored JSON has a format version, so
a later change can carry the list over; broken or unknown data gives an empty list, never a crash.

**Logic (pure, tested):** `SongHistory` with `add(entry, now)` (dedupe by normalized title and artist, move
to top, keep 20), `clear()`, JSON to and from text; a `HistoryStore` interface like `StageStore`, with the
SharedPreferences version next to `StagePreferences.kt`. When a song counts as "heard": once its lyrics have
been looked up (recognized) or when it is picked (by hand).

**Tests:** adding, moving to the top, the limit, dedupe with different spelling ("Atemlos" / "atemlos"),
clear, JSON round trip, broken JSON and an unknown format version give an empty list, the `/api/get/{id}`
URL. Sabotage: e.g. no limit, or no dedupe.

**On the phone:** recognize two songs and pick one by hand, check the list and its order; open an entry;
close and reopen the app (the list stays); clear it; install the update over the old version (the app
starts with an empty list, nothing breaks).

## 9. Real word and line times from LRCLIB's lyricsfile (proposed)

**Why:** LRCLIB answers carry a `lyricsfile` field: YAML with the start and end time of every line and, for
about a third of the synced entries (see the correction under "What the real lyrics look like"), the start
of every word. With it, the fill (point 3) follows the singer word by word instead of an estimate, and line
ends are real, which makes the fill and the countdown (point 2) exact. Song by song in the sample: e.g. 13
of 17 synced versions of "Blinding Lights" and 8 of 18 of "Dancing Queen" have word timing; 2 of 10 songs had
none.

**Facts checked at the source (2026-11-25):** the fields are documented in LRCLIB's own project (search and
get return `hasWordSync`, a boolean, and `lyricsfile`, "raw Lyricsfile YAML when present"; LRCLIB's design
says that when a lyricsfile is present, the older lyric fields should be ignored). LRCGET's release notes
warn that the format may still change. A real answer looks like this (structure only):

```yaml
version: '1.0'
metadata:
  title: ...
  artist: ...
  duration_ms: 372000
  instrumental: false
lines:
- text: First line of the song
  start_ms: 13770
  end_ms: 15330
  words:
  - text: 'First '
    start_ms: 13770
  - text: 'line '
    start_ms: 13890
  ...
```

Not every line has `end_ms` or `words` (LRCLIB's own examples have lines with `start_ms` only).

**What the owner sees:** for songs with word timing, the current line fills word by word on the real times;
for all songs from the lyricsfile, the fill and the countdown use the real line ends. Songs without a
lyricsfile behave as today.

**Logic (pure, tested):**

- `LrclibTrack` gets `lyricsfile` and `hasWordSync`.
- `Lyricsfile.parse(yaml): List<LyricLine>?`: each line becomes a `LyricLine` with `timeMs = start_ms`,
  `endTimeMs = end_ms` (if given), and `words` from the word list, where each word's `charIndex` is the
  length of the words before it. If the words do not join to exactly the line's text, the words are dropped
  and the line is kept. Lines without `start_ms` are skipped; the result is sorted. Anything that does not
  parse gives null.
- `LrclibApi.toLyrics` uses the lyricsfile when it parses into at least one line, otherwise `syncedLyrics` as
  today, then plain lyrics. Never a crash on odd data.
- `pickBest` (optional, careful): among the versions it would already accept and that have the length it
  chose, prefer one with `hasWordSync`. It must never change which song, artist, variant or length is chosen
  ("wrong lyrics are worse than none").

**YAML:** the app has no YAML reader. Recommended: the small, pure-Java library **SnakeYAML Engine**
(`org.snakeyaml:snakeyaml-engine`), because real YAML has quoting and escapes (`'Qui '`, `"..."`) that a
hand-made reader gets wrong. The alternative, a small own reader for just this structure, needs no library
but would break on such cases. The session asks the owner before adding the library (AGENTS.md: new
libraries only with a reason), checks the version's licence (Apache 2.0) and that it works on Android, and
adds it to `gradle/libs.versions.toml`.

**Tests:** a made-up lyricsfile (no real lyrics in the repository: they are copyrighted) with words, one
without words, a line without `end_ms`, words that do not join to the line text, quoting with spaces and
umlauts, invalid YAML (falls back to `syncedLyrics`), the `pickBest` tie-break (and that it never picks
another length). Sabotage: e.g. off-by-one in `charIndex`, or prefer `syncedLyrics` over the lyricsfile.

**On the phone:** a song with many word-synced versions (e.g. "Blinding Lights" by The Weeknd): the fill
must follow the words; a song without word timing still fills as before; the countdown after a line now
starts when the line really ends.

## After the points

Releases (points 1-3 were `v0.5.0`, points 4-6 `v0.6.0`) are the owner's decision; the steps are in
AGENTS.md ("Release").
