# Roomie

Android app for fast, swipe-based gallery cleanup (Tinder-style cards), built per the MVP TZ.
Kotlin + Jetpack Compose, 100% offline, deleted files go to the system trash
(`MediaStore.createTrashRequest`) with a configurable auto-purge timer.

## Status

This is a from-scratch implementation of the full MVP spec — no prior code existed. All screens,
the data layer, and the background cleanup worker are written. **It has not been compiled or run**
by hand: it was written in a sandbox whose network policy blocks `dl.google.com`, so the Android
Gradle Plugin and Android SDK platform couldn't be downloaded there, and no emulator/device was
attached. `.github/workflows/build.yml` builds a debug APK on GitHub's own runners on every push —
check the Actions tab for the first real compiler feedback. Before relying on the app, also walk
through the flows in "Suggested manual QA" below on a real device — a green build only proves it
compiles, not that every gesture feels right.

## Project layout

```
roomie/
  app/src/main/java/com/roomie/app/
    data/
      media/       MediaStore access (MediaRepository), burst/video grouping, empty-folder cleanup
      db/          Room: trash registry only
      settings/    DataStore-backed user settings, per-direction swipe actions, session swipe counter
      trash/       TrashRepository: system trash dialog, retention countdown
      monetization/ MonetizationGateway interface (no-op stub; extension point for Ads/Billing)
    ui/
      screens/folders   Auto-discovered folder grid + period filter
      screens/swipe      The card stack: 4-direction drag gesture, spring physics, undo, limit
      screens/trash      Pre-deletion review grid ("uncheck to keep")
      screens/summary    Post-deletion summary (count + freed space)
      screens/limit      Swipe-limit paywall (ad / one-time purchase stubs)
      screens/settings   Sort order, retention days, auto-delete-empty-folders, monetization toggle
      navigation         Single-Activity NavHost wiring all of the above
      theme              Warm & Cozy color palette, shapes, spring constants
    work/          TrashCleanupWorker (WorkManager, device-idle + battery-not-low)
    AppContainer / RoomieApplication / MainActivity   manual DI wiring (no DI framework)
```

## Key design decisions (and why)

- **Manual DI, no Hilt.** The app is small enough that a DI framework would add build complexity
  without buying much; `AppContainer` + `ViewModelFactory` cover every screen.
- **Room stores the trash registry**, not just a DataStore flag, because the app enforces its own
  configurable retention (1/3/7/30 days) independent of whatever the OS's own trash auto-purge
  window is. On API 30+, `createTrashRequest` hides the file immediately (`IS_TRASHED`); Roomie's
  own worker permanently deletes it once *its* countdown elapses. On API 26-29 (no system trash),
  the file stays visible until that same countdown fires — an accepted MVP simplification for
  legacy Android, called out in the TZ (section 8.2).
- **Each swipe direction maps to a configurable action** (`SwipeCardAction`: delete / keep / move
  to folder / postpone / do nothing), set per-direction in Settings — defaults are right=keep,
  left=delete, up=move to a single pre-chosen folder, down=postpone to the back of this session's
  queue. "Move to folder" reuses the trash flow's "one system dialog" pattern
  (`MediaStore.createWriteRequest`, API 30+) to get write access, then updates the file's
  `RELATIVE_PATH`; below API 29 (no `RELATIVE_PATH` column) it's a no-op.
- **Burst grouping is timestamp-based**, not `burst_id`-based, because there is no public,
  cross-device MediaStore column exposing a burst id to third-party apps. Consecutive photos in the
  same folder taken within ~1.5s of each other collapse into one card; short videos are always their
  own single-item unit. See `BurstGrouping.kt` for the exact heuristic and how to plug in a real
  burst id if a target device happens to expose one.
- **Empty-folder deletion only touches directories Roomie itself just vacated** (passed in
  explicitly by the cleanup worker), and never removes a top-level media folder (DCIM, Pictures,
  WhatsApp, ...) even if it's empty — those are here for other apps to write into. It requires
  `MANAGE_EXTERNAL_STORAGE` and is a no-op without it.
- **Ads/Billing are a `NoOpMonetizationGateway` stub.** The swipe limit, paywall screen, and
  settings toggle are fully wired; swap the gateway implementation for real AdMob/Play Billing
  calls without touching any caller. Monetization is off by default (`monetizationEnabled = false`
  in `RoomieSettings`), matching "off during personal use" from the TZ.
- **The trash-confirmation dialog listener lives at the NavHost level**, not inside the swipe
  screen. "Delete all" is pressed from the trash-preview screen, one navigation hop after the swipe
  screen has already left composition — so the `IntentSender` launcher has to live somewhere that
  outlives individual screens (see `RoomieNavHost.kt`).

## Suggested manual QA

Once this builds in a real environment, exercise at minimum:

1. Grant/deny the media permission dialog on first launch; deny → rationale screen → grant.
2. Open a folder with bursts (rapid continuous shots) and confirm they collapse into one card.
3. Swipe in all four directions, confirm rotation + spring-back/spring-out animation and haptic
   tick at the threshold, and that the next card is swipeable immediately (no waiting for the
   previous card's exit animation).
4. Undo several times in a row (up to 10) and confirm cards return to the front correctly,
   including undoing a postponed card and a move-to-folder swipe.
5. In Settings, reassign a direction's action (e.g. swap left/right) and a "move to folder"
   destination, then confirm the swipe screen picks up the new mapping.
6. Exhaust a folder's stack, review the trash grid, uncheck an item, then "Delete all" — confirm
   exactly one system trash dialog appears (API 30+) and the summary screen shows correct
   count/size. Also open the trash preview mid-session via the top-bar icon.
7. Swipe a card in the "move to folder" direction and confirm the system write-access dialog
   appears once (API 30+) and the file ends up in the configured folder.
8. In Settings, flip "Enable swipe limit" on, set a low value in code temporarily (or swipe ~100
   times) to confirm the limit screen appears and both stub buttons report "not available" cleanly.
9. Change sort order and retention days in Settings and confirm they take effect on the next
   session / cleanup run.
10. Turn off networking entirely and confirm nothing breaks (there should be no network calls at
    all outside a real Ads SDK, which isn't wired in).

## Known gaps vs. a production build

- No real Ads SDK / Billing Library integration (by design for this MVP — see TZ section 11).
- No automated tests yet (no test runner available in this sandbox to scaffold against).
- `EmptyFolderCleaner` and the legacy (<API 30) trash path use `MediaStore.MediaColumns.DATA`,
  which is deprecated and only returns real paths with All Files Access granted; without it, both
  features degrade to no-ops rather than crashing.
