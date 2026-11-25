# Karaoke plan

Six features that make Earacle better for singing along. They were chosen by the owner on 2026-11-24 after a
look at how Apple Music (Sing), Spotify, Musixmatch and YouTube Music show lyrics. Each point is done in its
own session, branch and pull request, **one after the other, in the order below**: all of them change the
lyrics screen (`MainActivity.kt`), so running them in parallel would cause conflicts, and later points build
on earlier ones.

**How to use this plan in a new session:** the owner writes "Setze Punkt N aus docs/karaoke-plan.md um."
The session then:

1. Checks that every earlier point is marked done below and merged into `main`. If not, it says so and asks
   before going on.
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
- [ ] 5. Backing vocals smaller and muted - branch `feature/backing-vocals`
- [ ] 6. Stage mode - branch `feature/stage-mode`

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

- **Word timing:** none. Not one entry had per-word timestamps (`<mm:ss.xx>`, the "A2" or enhanced LRC
  format) or timestamps in the middle of a line. Point 4 is therefore small and will rarely show.
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

## After the points

A release (e.g. after points 1-3 as `v0.5.0`, after all six as `v0.6.0`) is the owner's decision; the steps
are in AGENTS.md ("Release").
