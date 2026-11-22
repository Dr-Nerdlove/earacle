# AGENTS.md

Instructions for every AI tool working on this repository.

<!-- owner-rules v19: general (AGENTS.md) - begin -->

## General rules

### People

- Unless "Project knowledge" says otherwise, assume that the people you work with **do not program**: they
  build their projects together with AI assistants and rely on you for the code, its quality and its
  explanation. "Project knowledge" lists who works on the project.
- Talk to them in **German**, in plain words, without jargon. Say what changes for them, not which file
  changed.
- When they ask for ideas or something is unclear, **offer choices and let them pick before building**, with
  a recommendation. Do not build a proposal nobody chose.
- **Report honestly**: a test that failed, a step that was skipped, a fix that turned out to be wrong. Say
  what was **really tested** and what was only built or compiled, and what it means for them.
- **Merge, release or publish only when the person asks for it.** Never merge or overwrite the work of
  someone else without their consent; propose it to them (pull request, comment) instead.

### Language

- **Everything in the repository is English**: files and documentation, code comments, commit messages,
  branch names, pull requests, issues and release notes. Only the conversation with the people is German.
- Text that the product itself shows to its users (an app's interface, a device's display, its store page)
  stays in the language or languages the product is made for. "Project knowledge" names them.
- **A product in several languages stays in all of them.** Every new or changed text goes into every
  language the product offers, in the same change; never leave one language behind, drop one, or fall back
  to another language silently. Write each language naturally (not word for word), with its proper
  characters (ä, ö, ü, ß in German). Check that no text is missing in any language before calling the change
  done.
- Where a project still has older content in another language, do not translate it on your own: offer it as
  a choice.

### Testing

- Where the project has automated tests, **every new behaviour that can be tested automatically gets a test,
  and a sabotage check**: break the behaviour on purpose (it must still compile, and it must break the
  behaviour, not just a selector or a text), run the test, see it fail, put the code back and confirm that
  the file is byte for byte what it was (`cmp`). A test that passes against broken code is worthless.
- **What cannot be tested automatically** (hardware, devices, displays, animations, things that need a real
  account or service) gets no test suite of its own. Check it as far as possible (build, compile, a preview),
  then on the real device or service, and say plainly which part is checked how.
- Where the project has no tests at all, do not set up a test suite on your own: when a change would be
  risky without one, offer it to the owner as a choice.
- Run the project's checks before pushing, as "Project knowledge" or the project's own instructions say.
  Where they say nothing: the checks that cover what changed, and the whole suite when the change could have
  effects elsewhere.

### Safety and data

- **Never put secrets into the repository**: passwords, API keys, tokens, private keys, keystores and `.env`
  files with values. Use the project's way of keeping them out (environment, settings, `.gitignore`); if there
  is none, ask.
- **Nor what tells others about the people or their machines**: addresses and host names in the home network,
  local paths with a user name (`C:\Users\<name>\…`, `/home/<name>/…`; use paths relative to the
  repository), and hidden metadata in files: images, PDFs and office documents can carry the path they were
  made from, an author's name or, in photos, the place (GPS). Remove it before committing such a file, and
  switch it off in the tool or script that makes the file, so it does not come back.
- **Never write a secret into a command**, not even for a quick test: tools remember commands word for word
  (Claude Code keeps every allowed one in `.claude/settings.local.json`), and the session's output is not
  private. Read secrets from the environment or a file that is not committed, or ask for them through the
  session's secure input where it has one. Never print one; show at most its first characters.
- **If a secret got out anyway** (pushed, or shown in a log, a pull request or an issue), tell the owner at
  once, without repeating the value. The secret counts as burnt: it must be **changed at the provider**, which
  makes the leaked copy worthless. Deleting it from the history alone is not enough (copies, caches, forks and
  logs keep it), and rewriting history happens only when the owner explicitly agrees.
- **Before a repository is made public**, check its whole history (all commits and branches) for secrets and
  for what tells others about the people or their machines (see above). Report what you find without showing
  the values, and change nothing in the history without the owner's consent. Anything found counts as burnt
  once the repository is public. If it is public already, do the check at once.
- **Personal data** (of the people or of the product's users) is not sent anywhere new, logged or published
  without asking.
- **What users have stays usable after an update**: their settings, saved data, accounts. When a change
  would break or reset them, say so before building and offer a way to carry them over.
- **Never overwrite `main` with a force-push and never delete it.** Not even when asked in passing; the
  owner does that themselves if it is ever needed.
- **Ask before anything hard to undo**: deleting data or files that are not part of the change, rewriting
  history, changing a live service, publishing to a store.
- **New libraries or services** only with a reason, said in plain words; prefer what the project already
  uses.

### Changes

- **One topic per change** (branch, pull request): easier to check, to release and to undo.
- **One branch per task.** Create it from the latest default branch (usually `main`) with a name that says
  what the change is about: `type/short-topic`, lower case, English, words joined by hyphens - `feature/` for
  something new, `fix/` for a bug, `docs/` for documentation, `chore/` for tidying up (e.g.
  `feature/online-update`, `fix/ota-redirect-query`, `docs/ai-rules`). Not who or when, and nothing vague like
  `test` or `update2`. Push to that branch instead of the random branch name the environment gives the
  session; this is the owner's standing permission to push to such branches. A new task in the same session
  gets a new branch. Propose every change as a pull request into the default branch. It is merged when the person merges it on GitHub or asks for it ("übernehmen"); then merge it yourself (never squash away someone else's commits). Where the project has its own naming habits, follow
  them.
- **After a merge or release, the new version is not everywhere at once.** GitHub keeps copies for a while:
  GitHub Pages is usually live a minute or two after its run has finished, but browsers may show the old page
  for up to 10 minutes (raw files and download links up to about 5) unless reloaded without the cache;
  whatever a workflow builds or publishes only exists once its run has finished. Tell the person **how long to wait** (from the cache times
  and how long the project's last run took) and **how to check that the new version has arrived**: the run
  finished green under "Actions", the new version number in the app, on the release page or in the file, the
  new text on the page after a reload that bypasses the browser's cache. Where you can check it yourself, do
  so before calling it done, and say what you saw.
- **Commit messages and pull request titles** say in one line what changes, e.g. "Fix online update: keep
  the signed download URL"; more detail, and why, goes below.
- Changes that users notice go into the release notes or changelog in plain words (in English, like
  everything in the repository; texts shown to users inside the product or in a store, such as "what's new",
  in every language the product offers).

### Repository

Aim for what is common practice on GitHub, in proportion to the project: a small experiment needs less than
something others install. **Do not restructure an existing repository on your own**: point out what is
missing, with a recommendation, and let the people choose.

- **README**: what the project is (with a picture where it helps), how to install and use it, how to work on
  it (build, test, release), and its licence. Keep it up to date with every change that affects it.
- **Pull request descriptions**: what changes, why, how it was tested (and what was not), and anything the
  reviewer should know.
- **Automatic checks (CI)**: every pull request runs the project's tests and checks that the code compiles,
  also where there are no tests; merge only when this passes. Keep these checks quick and keep nothing they
  build; files to publish are built for releases (see "GitHub Actions"). Where a project has none, offer to
  set it up. Where they do not look for secrets yet, offer a secret scan of every push (e.g. gitleaks); add it
  only when the owner chooses it.
- **Releases**: version numbers `vMAJOR.MINOR.PATCH` (e.g. `v1.4.2`: major for breaking changes, minor for
  new features, patch for fixes). Files that are published are built by CI, never by hand. Each release says
  in plain words what is new and how to install or update.
- **Basics**: a `.gitignore` for build output and local files, a **licence** file (without one, nobody may
  legally reuse the code, even in a public repository; the choice of licence is the people's), and a
  `CHANGELOG.md` where releases alone do not tell the story.

- **Protecting `main`**: when the rules are installed, check whether the repository is public or private.
  - **Public**: recommend to protect `main` against force-pushes and deletion, and give the steps below. The
    owner of the repository decides whether it makes sense for this project. You cannot set it up yourself,
    because you have no access to the repository settings.
  - **Private**: recommend nothing where the repository belongs to the rules' owner: they have no GitHub Pro,
    and in a free account branch protection and rulesets exist only for public repositories (source:
    `github/docs`, file `data/reusables/gated-features/protected-branches.md`). They can ignore GitHub's
    notes about missing protection there. Where the repository belongs to someone else, their plan decides
    and you cannot see it: mention once that protection on a private repository needs a paid plan (Pro or
    Team), and leave the decision to its owner.
  - **If you cannot tell** whether the repository is public or private (no way to look it up), ask instead of
    guessing.
  - **Whose repository it is matters**: only the repository's owner (or an admin) can open its settings. Where
    it belongs to someone else, say so and tell the person to pass the steps on to them.

  Steps for the owner (give them in German):

  1. Auf GitHub das Repository öffnen: **Settings → Rules → Rulesets → New ruleset → New branch ruleset**.
  2. Name: `main schützen`. **Enforcement status**: `Active`.
  3. **Target branches → Add target → Include default branch**.
  4. **Restrict deletions** und **Block force pushes** angehakt lassen.
  5. **Require status checks to pass** nur anhaken, wenn das Projekt CI hat; dann die CI-Prüfung auswählen.
     Achtung: Dann kann niemand mehr direkt auf `main` pushen, jede Änderung muss erst auf einem anderen
     Branch die Prüfung bestehen. Das passt nur, wenn ohnehin nichts direkt auf `main` geschrieben wird
     (auch kein Workflow). Bleibt ein Merge hängen, zuerst dort nachsehen: ob die Prüfung umbenannt wurde
     oder bei dieser Änderung gar nicht lief.
  6. **Nicht** "Require a pull request before merging" mit Approvals anhaken: Wer allein arbeitet, kann eigene
     Pull Requests nicht freigeben und würde sich selbst aussperren. Auch zu zweit ist es nicht nötig und
     bremst nur jeden Merge; der Schutz vor Force-Push und Löschen reicht.
  7. Auf **Create** klicken.

  Geklappt hat es, wenn das Ruleset in der Liste unter **Rulesets** als "Active" steht.
  Für weitere Projekte: das Ruleset dort über **Export ruleset** speichern und im nächsten Projekt über
  **Import a ruleset** übernehmen.

### GitHub Actions

The owner's account is on GitHub's free plan: 0.5 GB of storage and a limited number of minutes. Where the
repository belongs to someone else, these points still apply unless "Project knowledge" says otherwise.

- **Storage counts in every repository.** Artifacts that are kept (test builds, APKs from pull requests) are
  what fills it; keep as little as possible, as short as possible.
- **Minutes count only in private repositories.** In public ones, GitHub-hosted runners are free. Check whether
  the repository is public or private before weighing a change: in a public one, saving minutes is tidiness,
  not money; in a private one, it matters.
- Apply the points below in both cases; they keep the project tidy and make it ready to become private.

- **Builds** (an APK and the like) run only for release tags (`v*`) and when started by hand
  (`workflow_dispatch`), not on every push.
- **Releases are started by hand**, because a cloud session cannot push tags: the release workflow has
  `workflow_dispatch` with the version as input, creates the tag and the GitHub Release itself
  (`permissions: contents: write`) and builds in the same run, since a tag created by a workflow does not
  start other workflows. Tell the person how to start it (Actions → the release workflow → Run workflow →
  enter the version) and describe it under "Release" in "Project knowledge". If the tag exists already, the
  workflow stops at the start with a clear message.
- Where a build on push is needed (e.g. publishing to GitHub Pages after a merge): only on `main`, and with
  `paths-ignore:` so that changes to documentation, the README and the AI instructions (`AGENTS.md`,
  `CLAUDE.md`, `.claude/**`) do not start it.
- Checks on pull requests skip changes that only touch those files (`paths-ignore:`). Not where `main`
  requires the check (see "Protecting `main`"): a required check that never ran blocks the merge.
- Every workflow sets `concurrency` with `cancel-in-progress: true`, except the release workflow: there
  `cancel-in-progress: false`, so a second run waits instead of breaking off a release halfway.
- Every job has `timeout-minutes`.
- Dependencies are cached (e.g. `gradle/actions/setup-gradle`, `actions/setup-node` with `cache:`).
- Finished APKs are attached to a GitHub Release instead of kept as an artifact.
- Where an artifact is needed: only the final file, with `retention-days: 1`.
- Build only the one variant that is needed (e.g. release, not debug as well).
- **A release APK must be signed**, always with the same key, the keystore and its passwords kept as
  repository secrets. An app signed with a different key cannot be installed as an update: the old one has to
  be removed first, and its data with it. Before switching from a debug build, check whether the earlier APKs
  had a fixed key at all (on GitHub's runners the debug key is made anew on every run unless the project
  keeps one), tell the owner which case it is and whether users will have to reinstall, and ask. When the
  keystore is set up, ask the owner to keep a copy of it and its passwords outside GitHub (e.g. in a password
  manager): a secret cannot be read back, and without the key no update can ever be built again.
- When the rules are installed, check the project's workflows against these points and point out what
  differs, with a proposed change.

### Showing changes

- Show visual changes rather than describing them: **screenshots** where the result runs on a computer.
- Where no screenshot is possible (a device, a display, a phone app, print): build a **preview** (a mock-up
  page, a pixel preview), or ask for a **photo** of the real thing.

### Checking facts at the source

- Do not build on memory where the real thing can be checked: read the actual source of a library, look at
  the real answer of a server or service, read the real data. Behaviour that "should" be so is often not.
- When a cause is unclear, **ask for a log or an error message before guessing**, and say what it would show.

### Keeping knowledge in the repository

- Sessions end and are deleted; the repository stays. What a later session needs to know goes into the
  project's files ("Project knowledge", the README, other docs, commit messages), not only into the
  conversation: decisions and why, traps that cost time, how to build, test and release, and the limits of
  the environment (what a cloud session cannot do there).

<!-- owner-rules v19: general (AGENTS.md) - end -->

## Project knowledge

- **Project:** Earacle, a private Android app that listens through the microphone to music playing nearby
  (e.g. a radio), recognizes the song and shows its lyrics in sync with the music, line by line.
- **People:** the owner (GitHub: Dr-Nerdlove) builds the project together with AI assistants and does not
  program. The owner alone decides about merging and releases.
- **Product languages:** German and English. Every user-facing text exists in both (`values/strings.xml`
  for English as the default, `values-de/strings.xml` for German), in the same change.
- **Planned architecture (decided, not built yet):**
  - Kotlin + Jetpack Compose; microphone capture with `AudioRecord` (PCM 16-bit mono) in a foreground
    service of type `microphone`, so it keeps listening with the screen off.
  - Song recognition through the **unofficial (reverse-engineered) Shazam API**, as used by open-source
    projects such as SongRec, ShazamIO and MusicRecognizer (Audile, a Kotlin Android app and the best
    reference). Chosen because it is free and returns the position within the song. Acceptable only
    because the app is private and never published.
  - Synced lyrics (LRC format) from **LRCLIB** (`lrclib.net`, free, no API key). Send title, artist and
    duration so the right version (radio edit, live, remix) is found.
  - Sync: position from the recognition result + time elapsed since the audio sample. A manual offset
    slider (± a few seconds) as fallback (not built yet).
  - Listening along (built, `SongTracking` + `MainViewModel`): after a first match from the button, the app
    records 12 s again after a pause that grows while the same song keeps being confirmed (15 s, then
    45 s, then 105 s after a recognition finished: about 27 s, 1 min, 2 min between recordings; at most 45 s
    while the song length is unknown; any miss or a different song starts again at 15 s). Silent
    recordings (RMS below 60) are not sent to Shazam and count as a miss (the button always sends). The
    next recording is brought forward to the song's end, so a song change is still noticed; once the end has passed without a new song (e.g. a presenter talking), the normal pause applies again instead of back-to-back recordings. While the app is visible
    (the microphone is silenced in the background on Android 11+, so it waits). The same song only
    replaces the position (lyrics are kept); a different song loads new lyrics. After 4 answers in a
    row without a match it stops (display stays); a rate-limit answer waits 60 s. The song length from
    LRCLIB caps the shown position.
  - Possible later fallback: own fingerprinting with **Olaf** (open source) against the owner's music
    library, in case the unofficial Shazam API stops working.
- **Rejected options and why:** official ShazamKit for Android (needs a developer token from the paid Apple
  Developer Program; developers reported offsets off by seconds on Android); ACRCloud and AudD (paid after a
  trial or small free quota).
- **Code layout:** one Gradle module `app` (Kotlin + Jetpack Compose), package and applicationId
  `io.github.drnerdlove.earacle`, minSdk 29 (Android 10), compileSdk/targetSdk 37. All versions (AGP,
  Kotlin, Compose BOM, libraries) live in `gradle/libs.versions.toml`; the Gradle version is in
  `gradle/wrapper/gradle-wrapper.properties`.
- **Build and test:** `.github/workflows/build.yml` runs on every pull request (skipped when only `*.md`
  files, `LICENSE` or `.claude/**` change) `./gradlew testDebugUnitTest assembleRelease`: unit tests and an
  unsigned release APK that is not kept. Started by hand (Actions -> Build -> Run workflow, any branch) it also
  signs the APK and uploads it as the artifact `earacle-test-apk`, kept 1 day, to try a branch on the phone
  before merging (on the run's page under "Artifacts", downloaded as a zip). Nothing is built on pushes to
  `main`. Only the release variant is built (owner-rules: one variant). Signing: CI signs with a fixed key
  (PKCS12, alias `earacle`, same password for store and key) from the repository secrets
  `EARACLE_KEYSTORE_BASE64` and `EARACLE_KEYSTORE_PASSWORD`, passed to Gradle as `EARACLE_KEYSTORE_FILE` /
  `EARACLE_KEYSTORE_PASSWORD`; `app/build.gradle.kts` applies it to the debug and the release build. Its
  certificate SHA-256 is `80f3eb07a537585ed98b1461c9c488e441a2d1aa321ffb83443ff86b0c561ecb` (public); signed
  runs fail when the APK has another one, or when the secrets are missing. Every CI build since PR #5
  (2026-11-18) had this certificate; only the first three builds before it used throwaway debug keys.
  Repository secrets, not environment secrets: environments are not available in private repositories on a
  free plan (source: `github/docs`, file `data/reusables/gated-features/environments.md`). The keystore is
  never committed; the owner keeps a backup outside GitHub. JVM unit tests live in
  `app/src/test` (JUnit 4; org.json is a `testImplementation` dependency because `android.jar` only contains
  stubs of it) and CI runs them with `./gradlew testDebugUnitTest`. Keep the
  recognition logic free of `android.*` imports so it can be tested. The cloud session cannot run the Android
  build, but the tests can be run there with the local Gradle (`/opt/gradle`, 8.14) from a scratch JVM project
  (`kotlin("jvm")`, junit 4.13.2, org.json) whose source sets point at the pure sources in `recognition/`
  (without `ShazamRecognizer.kt`) and at `app/src/test`. Microphone, real recognition, sync and the UI can
  only be checked on a real phone; say so when reporting.
- **Release:** an APK for the owner's own phone, never published to the Play Store or any other store. It is
  published as a GitHub Release of this public repository, so anyone can download it (the owner chose this on
  2026-11-22, over a draft release only they could see). How to release:
  1. Raise `versionName` (e.g. `0.4.0`) and `versionCode` (+1) in `app/build.gradle.kts` in a pull request,
     with the release notes in its description, and merge it.
  2. Actions -> **Release** -> **Run workflow** (branch `main`) -> enter the version with a `v` (e.g.
     `v0.4.0`) -> **Run workflow**.
  3. `.github/workflows/release.yml` checks the version (format, tag not taken yet, equal to `versionName`),
     builds and signs the release APK, checks the certificate, then creates the tag and the GitHub Release
     with `earacle-vX.Y.Z.apk` attached and notes generated from the merged pull requests. It takes a few
     minutes (a build takes about 1-2 min); the release then appears under **Releases** on the repository
     page.
  Never tag or upload APKs by hand. The release APK has the same applicationId and key as the earlier CI debug
  builds, so it installs over them and keeps the app's data. Installing via ADB / Android Studio stays exempt
  from Android's developer verification; normal sideloading may need verification once it is enforced in
  Germany (announced for 2027 and later).
- **Repository:** public since 2026-11-18 (private before), owned by Dr-Nerdlove alone (personal account). Licence: AGPL-3.0 (`LICENSE`). Versions
  `vMAJOR.MINOR.PATCH`. CI: GitHub Actions, `build.yml` (tests and compile check on pull requests, test APK by hand), `release.yml`
  (releases, by hand) and `gitleaks.yml` (secret scan of
  every push and pull request with `gitleaks/gitleaks-action`; no licence key needed because the repository
  belongs to a personal account, not an organization). Branch protection is available now that the
  repository is public (free plan); whether to set it up is the owner's decision.
  Because the repository is public, anyone can read the code, the pull requests and the Actions logs, and any
  signed-in GitHub user can download workflow artifacts (source: `github/docs`, download-workflow-artifacts).
  Only people with push access see draft releases (GitHub REST API description, releases).
- **Traps:**
  - AGP 9 has built-in Kotlin: do **not** apply `org.jetbrains.kotlin.android`. AGP bundles an older Kotlin
    Gradle plugin; the root `build.gradle.kts` puts the catalog's Kotlin version on the buildscript classpath
    so it is used instead. Keep the Compose compiler plugin version equal to the Kotlin version.
  - The Gradle wrapper was generated offline in the cloud session (`services.gradle.org` is blocked there),
    so `gradle-wrapper.properties` has no `distributionSha256Sum`. `setup-gradle` in CI validates the wrapper
    jar.
  - The unofficial Shazam API can change or break at any time without notice; keep recognition behind
    an interface so another backend can be plugged in.
  - Shazam returns no match for audio windows of 15 s or more; send 12 s (SongRec does the same).
  - Shazam's `offset` is the position in the song at the start of the sample, so the current position is
    offset + time since the recording started (confirmed on the owner's phone with v0.2.0).
  - Not every LRCLIB track has synced lyrics; fall back to plain lyrics or a clear message.
  - Lyrics come from LRCLIB's `/api/search` (checked against the real API): `/api/get` needs the exact album
    and duration, which the recognition result does not give (the Shazam answer is parsed for title, artist,
    offset only; whether it carries a duration or album was not verified, so none is used). `LrclibApi.pickBest`
    keeps only entries with the same title and artist, drops live/remix/acoustic versions unless the song is
    one, prefers synced over plain lyrics, and among versions of different length takes the length most
    entries agree on (or the closest to a known duration). Wrong lyrics are worse than none. Artists are compared as lists of names (split at `,` `&` `·` `/` `;` `+` and feat./with/and/et...): a
    match is the first artist of one side being among the names of the other, or equal names joined (LRCLIB spells
    multi-artist credits in many ways).
  - `lrclib.net` is not on the cloud environment's network allow-list by default; add the host (via the
    environment tools) to check the real API there.
- **Limits of the environment:** the Claude Code cloud session has no Android SDK and cannot reach Google's
  Maven repository (`dl.google.com`) or `services.gradle.org`, so Gradle cannot build the app there. Build through the GitHub
  Actions workflow and read its logs with `gh api` (GitHub's GraphQL API is blocked in the cloud session;
  use REST). A cloud session cannot push tags, so releases are started by hand in
  GitHub Actions (see "Release").
