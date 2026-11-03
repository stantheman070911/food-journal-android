# Development Notes

Working notes for building Food Journal. The [product document](<Food Journal — Product Document.md>) is the source of truth. This file records what it leaves open and how each open point was settled.

## Toolchain

Checked Nov 3, 2026.

| Tool | Found | Notes |
| --- | --- | --- |
| JDK | JetBrains Runtime 25.0.4, bundled with Android Studio | No system JDK. Studio uses its own. For the command line, set `JAVA_HOME` (below). |
| Android Studio | 2026.2 | Bundles AGP 9.5.0 |
| SDK Platform | Android 17, API 37 (rev 2); 37.2 also installed | |
| Build Tools | 37.0.0 | |
| Platform Tools | 37.0.1 | `adb` is not on `PATH` |
| Gradle | 9.8.2, through the wrapper | |
| Test phone | Pixel 8a, Android 17 (API 37), 1080 × 2400 at 420 dpi | |

For command-line builds, add this to `~/.zshrc`:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$PATH:$ANDROID_HOME/platform-tools"
```

## Versions

The latest stable version of each library on Nov 3, 2026, from Google Maven and Maven Central. Declared in `gradle/libs.versions.toml`.

| Library | Version | Used from |
| --- | --- | --- |
| Android Gradle Plugin | 9.5.0 | Phase 1 |
| Kotlin | 2.4.21 | Phase 1 |
| KSP | 2.3.13 | Phase 1 |
| Compose BOM | 2026.09.00 (UI 1.12.1, Material 3 1.4.0) | Phase 1 |
| Activity Compose | 1.13.0 | Phase 1 |
| Lifecycle | 2.11.0 | Phase 1 |
| Navigation Compose | 2.10.2 | Phase 1 |
| Room | 2.8.5 | Phase 1 |
| kotlinx.serialization | 1.12.0 | Core: Phase 1 (routes). JSON: Phase 6 (backup). |
| CameraX | 1.6.2 | Phase 2 |
| Coil 3 | 3.6.3 | Phase 2 |
| DataStore | 1.3.0 | Phase 6 |
| JUnit 4 (tests only) | 4.13.2 | Phase 1 |

The app does not use the phone's location (see "Places without phone location" below), so there is no location library. DataStore is declared in the catalog but not used until Phase 6.

## Document review

### Contradictions

1. **Empty states.** Design direction says an empty state is "one short line and one button". Spending says an empty period "shows one line: 'No meals'". *Settled:* Spending shows the line alone. The Add button is on the tab anyway.
2. **No extra permissions vs. the camera.** The rules allow only the permissions the document names. In-app capture with CameraX requires the camera permission, which the document never names. *Settled:* the camera permission is implied by CameraX and is added in Phase 2, asked on first use.
3. **"Auto Backup is off" vs. Android's behavior.** On Android 12 and later, `allowBackup="false"` stops cloud backup but not device-to-device transfer. *Settled:* both are excluded with data extraction rules, so the exported ZIP really is the only backup.

### Ambiguities

| Point | Proposed answer | Phase |
| --- | --- | --- |
| Time zone of `eatenAt` | Wall-clock time where the meal was eaten (see Phase 1 decisions) | 1 |
| Empty-state wording | Journal: "No meals yet" with "Log your first meal". Search: "Nothing to search yet" with "Log your first meal". Spending: "No meals". | 1 |
| Read vs. edit on the Meal screen | One screen, always editable. Delete on a new meal discards it. | 2 |
| Changing the photo of a saved meal | Tap the photo to retake or pick another | 2 |
| When location permission is asked | The first time Add is tapped | 2 |
| Place-name matching | Trimmed, ignoring case. Typing an existing name picks that place. | 2 |
| Coordinates after a merge | The surviving place keeps its own; it takes the other's only if it has none | 2 |
| Largest price | Six digits (999,999) | 2 |
| Gallery photo with no date (screenshots, images saved from LINE) | The time it is added, editable like any other | 2 |
| Year in week headers | Shown only for weeks outside the current year: "Dec 29, 2025 – Jan 4" | 3 |
| Weeks with no meals | Left out | 3 |
| Tile order in a week | Monday to Sunday, left to right | 3 |
| "Visits" | Number of meals at the place in the period | 4 |
| Change-line label | Week: "vs last week". Month: "vs Oct". Year: "vs 2025". | 4 |
| Previous period with meals but a total of 0 | Change line omitted, as when it has no meals | 4 |
| Unfinished period longer than the previous one | Compare up to the previous period's last day | 4 |
| Swiping to future periods | Stops at the current period | 4 |
| An empty period once other meals exist | Keeps the period switch and swiping, with "No meals" in place of the figures, so an empty month never strands you | 4 |
| Ties in top places and priciest meals | Most recent first | 4 |
| Search before typing | All meals, newest first, with their total | 5 |
| What "original photos" means in the backup | The stored 1600 px photos. Camera originals are never kept. | 6 |
| Where the backup reminder shows | One quiet line at the top of Journal, also shown when no export has ever been made and the first meal is over 30 days old | 6 |
| Settings contents | A small menu from the settings icon (Export, Import), not a separate screen | 6 |

### Gaps

- **Speed of the 15-second target.** A first GPS fix can take several seconds. Phase 2 starts finding the location when the camera opens, shows the Meal screen at once, and fills the place when the fix arrives. It never blocks Save.
- **Imprecise location.** A 150 m match needs a precise fix. Phase 2 skips the prefill whenever the fix is less accurate than about 100 m, which also covers approximate-only permission.
- **Photo Picker location option.** The exact API, and whether it needs `ACCESS_MEDIA_LOCATION`, must be confirmed in Phase 2 before anything is added.
- **Orphaned files.** If the app is killed mid-flow, photos and empty places can be left behind. See the Phase 2 plan for how they are cleaned up without touching unsaved work.
- **Font.** No family is named. The interface uses the system sans-serif (Roboto). Fraunces was added after Phase 1 for big numbers and headings (see Type below).
- **Light and dark.** No switch is specified. The app follows the system setting.
- **Number format.** "2,340" uses English grouping on every device, since the app is English only.
- **App icon.** Not specified. Phase 1 uses a monochrome plate: two thin rings, white on black, with a themed-icon layer.
- **Test libraries.** Unit tests need JUnit 4. It is test-only and never ships in the app.

## Decisions

### Phase 1

- **Room 2.8, not Room 3.** Room 3.0 is stable but moves to new `androidx.room3` packages. The product document only says Room, and 2.8 is the mature line. Moving to Room 3 later would be a dependency change, not a data migration.
- **Kotlin through AGP's built-in support.** AGP 9 compiles Kotlin itself. The Kotlin Gradle plugin is declared but not applied, which pins the compiler to 2.4.21.
- **SDK levels.** compileSdk, minSdk, and targetSdk are all 37. Bytecode targets Java 17.
- **`eatenAt` is wall-clock time.** It is stored as text such as `2026-11-03T12:30:00`. A meal's day and time-of-day band never shift when the phone changes time zone. Gallery metadata (EXIF) records wall-clock time too, and the text sorts in time order.
- **`photo` holds a file name**, not a full path, so restored backups work on a fresh install.
- **Place names are unique, ignoring case**, enforced by the database. Merging relies on it.
- **Deleting a place empties `placeId`** on its meals rather than deleting them.
- **Schema export** goes to `app/schemas/`, so each version is kept in Git for future migrations.
- **No INTERNET permission.** The manifest also strips it in case a library ever tries to add it. Gradle prints a merge warning about this, which is expected. The only merged permission is AndroidX's internal `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, which is private to the app.
- **Icons are drawn in code**: outlined, 24-unit grid, one 1.5 stroke weight. The Material icon libraries are not in the stack.
- **Bottom bar.** Icons only, 56 dp tall. The selected tab is in full black (white in dark), the others grey. No highlight pill. A hairline separates the bar from content.
- **Add button.** Round, black in light and white in dark.
- **Navigation.** Navigation Compose with type-safe routes. One host holds the tabs and the Meal screen, so the Meal screen covers the bottom bar. A nested host switches the tabs, keeping each tab's state; Back returns to Journal, then exits.
- **Motion.** Tabs crossfade in 150 ms. The Meal screen fades in with a slight rise over 220 ms.
- **Meal screen in Phase 1** is a shell with Back only. Add opens it directly until the camera arrives in Phase 2.
- **Version catalog** declares the whole stack. The app module only uses what each phase needs.
- **Typography.** The Material 3 type scale. The sans-serif styles use tabular figures, so prices line up wherever they appear. Changed after Phase 1; see Type below.
- **Window and splash** backgrounds are white, or black in dark, so there is no colored flash at launch.
- **Dark theme parent.** In dark mode the window theme has a dark parent, so Android knows the app is already dark. On the Android 17 emulator, force-dark had no visible effect with or without it, so this is a safeguard.
- **Android Studio files** (`.idea/`) are not committed. Studio regenerates them from Gradle.
- **Empty states inside scrolling content** stack from the top instead of centering, so they can never crash for want of a height.

### Type: Fraunces for big numbers and headings

Requested on Nov 3, 2026, after Phase 1. This overrides "One sans-serif family" in the product document's Design direction.

- **The split.** The interface stays in the system sans-serif. Fraunces, a soft serif, sets only Material's display styles (big numbers) and headline styles (headings). Title, body, and label styles stay sans.
- **Where it appears.** A few places only, each confirmed in its phase:
  - Spending: the period total (display) and the period name (headline). Phase 4.
  - Journal: each week's date range (headline). The week total beside it stays sans, in grey. Phase 3.
  - Meal screen: the price as it is typed (display), if it reads well at that size. Phase 2.
  - Empty states: the line (headline small), from the Retro references below. Phase 2.
- **Axis settings.** Fraunces is a variable font with four axes.
  - Display: weight 300, optical size 144, softness 100, wonky 0. Light numerals with high contrast.
  - Headline: weight 500, optical size 32, softness 100, wonky 0. Heavier, so headings hold their own beside the sans.
- **No tabular figures.** Fraunces only has proportional figures. Neither the Google Fonts build nor the designers' own build includes `tnum`. So Fraunces sets only standalone numbers. Numbers that line up stay in the sans with tabular figures: lists, tiles, chart labels, the total beside a heading, and the search total.
- **Latin only.** Fraunces has no Chinese characters. Headings are dates and month names, so this doesn't come up. Any Chinese text in a Fraunces style falls back to the system font.
- **File.** The Google Fonts build of the variable font, `Fraunces[SOFT,WONK,opsz,wght].ttf`: 360 KB, SHA-256 `177ff6c0f14e5550a3c624247cd1189611d4eb65d000b14944c63d967958abbb`. It is bundled as `app/src/main/res/font/fraunces.ttf`, so nothing is downloaded when the app runs. Its license, the SIL Open Font License 1.1, is in `licenses/Fraunces-OFL.txt`.
- **Preview.** `ui/preview/TypePreview.kt` shows the styles together, in light and dark. The axis settings were checked on the API 37 emulator.
- **Tracking.** Display and headline styles are set 2% tight, after the Retro references.

### Phase 2 review fixes

From the review of commit `02e208e`, fixed on Nov 3, 2026, in the order given.

1. **A double tap on Save stored the meal twice.** The Meal screen now acts once: Save, Back, and Delete do nothing after the first of them, in the ViewModel, and their buttons stop responding while the screen fades out. They keep their look, so nothing flashes grey on the way out. This covers the keyboard's ✓ followed by a tap on Save.
2. **A replacement photo that failed deleted the saved photo.** The photo before it is kept as a fallback until the new one is ready, and comes back if the new one can't be read. The photo a save deletes is decided only after resizing finishes, so a saved meal never loses its original to an unreadable replacement.
3. **Save during resizing could lose the meal.** The screen now waits for the photo, about a second at most, with Save held. If the photo turns out unreadable, the meal stays open with the line "That photo couldn't be read. Try another." The same line shows whenever a photo fails.
4. **Background errors crashed the app.**
   - The app-wide scope logs a failure instead of crashing, and other work carries on.
   - Photo processing catches every error, including running out of memory, and removes half-written files.
   - Undoing a merge, when a place with the old name was created during the undo window, moves the meals to that place instead of failing on the unique name.
5. **Leftover pending files.** A capture that lands after the screen closed is deleted. So is a photo replaced while it was still being resized, and a pending fallback when a new meal is discarded.
6. **Device tests never touch the phone.** `connected…AndroidTest` tasks refuse to run unless `ANDROID_SERIAL` names an emulator, before anything is installed. Run them as `ANDROID_SERIAL=emulator-5560 ./gradlew :app:connectedDebugAndroidTest`.
7. **Deviations.**
   - **The photo shows at 3:4**, the camera's frame, so none of the shot is cut away. With the number pad up, the form scrolls so the price and Save stay above it; the photo's top scrolls out of view while typing.
   - **The price field's ✓ always closes the keyboard**, and still saves a new meal that is ready.
   - **The place picker's ✓** picks the saved place whose name was typed in full, adds a new name, or keeps a pending new place typed again. With nothing typed, it only closes the keyboard.
   - **The undo bar can be swiped away sideways**, which closes it as the timer would.
8. **Tests.** The ViewModel now takes its photo processing and location through small interfaces (`PhotoProcessing`, `LocationSource`), so device tests drive it with fakes. Added:
   - **MealViewModel:**
     - a double save, and Save followed by Back or Delete
     - a failed new photo, and a failed replacement on a saved meal
     - Save during resizing, both when the photo succeeds and when it fails
     - late gallery date and location
     - prefill after a manual pick
     - following a merge and its undo
     - a capture landing after close, and a photo replaced while resizing
   - **Gallery reading on real JPEG files:** the date with its fallback, GPS, a zeroed location, no metadata, and an unreadable photo.
   - **Location:** the two-minute freshness rule and fixes without an accuracy, as pure functions the tracker now uses.
   - **UndoableEdits:** a second delete closing the first, a rename during a pending delete, and a merge undo with the old name taken.
   - **Place picker:** what its ✓ does.

**Left as they were**

- **Places saved without coordinates** keep none, so they never prefill. The user chose on Nov 3, 2026 to leave it so: a place's coordinates stay where it was first saved, as the product document says.
- **Gallery date and place still apply after resizing**, about a second later. It was not on the list to fix.
- **Not tested on a device:** Photo Picker location on a real phone, HEIC date and location, photo orientation with auto-rotate off, an end-to-end UI test of the logging flow, a run of the shrunk release build on the phone, and release timing of the 15-second target.

## Phase 3: Journal

Built on Nov 3, 2026, after the Phase 2 review fixes.

- **Week headers.** The date range in the serif, the total beside it in grey sans: "Oct 26 – Nov 1 · 2,340".
  - A week within one month names the month once: "Nov 2 – 8".
  - The year shows only on dates outside the current year: "Oct 20 – 26, 2025", and across New Year "Dec 29, 2025 – Jan 4".
- **Order.** Weeks run newest first. Within a week, tiles run from Monday to Sunday by time eaten, and meals saved at the same moment keep the order they were saved. Weeks without meals are left out.
- **Tiles.**
  - Portrait, 88 × 117 dp (the photos' 3:4), from the 400 px thumbnails. About four and a half fit across, so a long week invites a swipe.
  - Tiles sit 2 dp apart, and only the strip's two ends are rounded, so a week reads as one band.
  - Each tile names its weekday in the lower left: white with a soft shadow over a photo, grey on a plain tile. A day with several meals shows a tile for each.
  - A meal without a photo is a plain grey tile with its name, up to four lines.
  - No prices on tiles: totals are visible but never the focus.
- **Opening a meal.** Tapping a tile opens the Meal screen, with the photo at its full 3:4. A second tile tapped while the first opens is ignored.
- **Settings icon.** Three sliders, top right. A gear's small teeth blur at this stroke weight. It does nothing until Phase 6 adds Export and Import.
- **No page title.** The first week's header heads the screen; the product document lists no title.
- **Not adopted from Retro:**
  - Pale tiles for days without meals: the document makes tiles meals, and two kinds of grey tile would compete.
  - A separate photo viewer: the Meal screen is one screen to add, read, and edit.
- **Release build.** With the personal key in place, the shrunk release build was installed and run on the emulator: logging, the Journal, and opening a meal work, with no crashes.

## Places without phone location

Requested on Nov 3, 2026, after Phase 3. This overrides the product document in three places. Quick logging no longer says "Place is filled when exactly one saved place is within 150 m" or "Typing a new place name creates the place at the current location", nor "Location permission is asked on first use only". The Tech stack's Fused Location Provider is no longer used.

- **Why.** The phone's position is wherever the meal is logged, not where it was eaten. A lunch logged that evening at home pinned the restaurant to home, and since a place keeps its first coordinates, it stayed wrong.
- **What is gone.** The location tracker, both location permissions, the "location asked" setting, and with it DataStore until Phase 6. The first Add asks for the camera only.
- **What stays.**
  - **Gallery photos.** A gallery photo whose location the Photo Picker shares still places the meal. A new place takes the photo's coordinates, and the one saved place within 150 m of the photo, if there is exactly one, is filled in. The photo's own location is where the meal was eaten.
  - **Coordinates are still stored** on places, for future use.
- **What follows.**
  - **Camera and Skip meals carry no location.** The in-app camera can't tag a photo with GPS without location permission. Their places are saved by name alone and are never filled in automatically.
  - **The picker lists places by most recent visit,** except for a gallery photo with a location, where it lists them nearest first, with distances.
  - **Places saved earlier keep the coordinates they have,** including any taken from the phone.
- **Autocomplete.** Typing in the picker already filtered saved places by any part of the name. Now names that start with what is typed come first, then names that contain it. Typing 林 lists 林家乾麵 before 小林拉麵. Matching ignores case and full-width letters, as before.

## Design references: Retro

Added on Nov 3, 2026, during the Phase 2 review. Screenshots of [Retro](https://retro.app/), the weekly photo journal the product document already names, are in `UI inspo(retro-screenshots)/`. They are a standing visual reference for every phase. Ideas are adapted to Food Journal's monochrome, photo-first identity, never copied: no Retro branding, art, or layouts are reproduced, and nothing they show is added unless the product document names it.

**Applied in Phase 2**

- **Pill buttons.** The main action is a solid pill, 56 dp, with a semibold label: black, or white in dark. Disabled, it turns plain mid grey rather than faded. Secondary actions, such as "Log your first meal", are outlined pills in the ink color. Both are in `ui/components/PillButton.kt`.
- **Grouped card.** The Meal form's Name, Place, Time, and Note rows sit on one soft grey card (`surfaceContainer`, 16 dp corners), divided by hairlines inset to the labels. The price label and figure line up with the labels on the card.
- **Rounded photos.** The Meal photo is inset 16 dp with 20 dp corners. Without a photo, a short grey tile reads "+ Add photo".
- **Pills for small things.** Name suggestions are borderless pills, white on the grey card (black in dark). The place search field is a grey pill.
- **Sheet header.** The place picker has a centered title and a close button instead of a drag handle. Swiping down still closes it.
- **Undo bar** corners are 16 dp.
- **Empty states.** The line is a short serif heading in the ink color, above an outlined pill.
- **Camera.** The close button sits on a dark grey disc.

**For later phases**

- **Phase 3, Journal.** A big serif heading. Week rows as rounded filmstrips. Empty days as pale grey tiles with the month, date, and weekday stacked in grey. Day names set in white over the photo's lower-left corner. Opening a meal can borrow Retro's photo viewer: the photo first, then the place in bold and the date in grey below it.
- **Phase 4, Spending.** The Week, Month, Year switch as a segmented pill, with the selected segment filled.
- **Phase 6, Settings menu.** Rows grouped on rounded grey cards, with grey section labels.
- **Sheets.** Action sheets can float inset from the screen edges, rounded on all corners.

**Not adopted:** red notification dots and badges, colored buttons or text (Retro's blue Upgrade, red Delete), and everything social: friends, feeds, sharing, captions, comments, profiles, and reminders. They break the monochrome rule or the scope.

**Found while applying them.** Name suggestions appeared below the Name field, hidden behind the keyboard and Save, so they were never seen. They now scroll into view when they appear.

## Phase 2 plan

Settled after the Phase 1 review, before any Phase 2 code.

### Photos

- One class owns every photo file. There are three folders in the app's private storage: `photos/` (1600 px), `thumbs/` (400 px tiles), and `pending/` (taken or picked, not yet saved).
- Every photo gets a random file name. Replacing a photo writes a new file under a new name, so the image loader can never show a stale cached copy.
- A new photo lands in `pending/` and moves to `photos/` only when its meal is saved. Discarding a new meal deletes its pending photo.
- The startup sweep removes files in `photos/` and `thumbs/` that no meal references. It leaves recent files in `pending/` alone, so a Meal screen restored after Android closed the app keeps its photo. Pending files older than seven days are removed.

### Delete and undo

- Undo lives at app level, in the app container, so it keeps running after the Meal screen closes.
- Deleting a meal removes its row at once. Its photo, and any place left with no meals, stay until the undo bar closes.
- Undo puts the same meal row back with its original id. Its place still exists, so the foreign key holds.
- When the bar closes, the photo is deleted and empty places are removed. If the app is killed with a bar open, the startup sweep finishes the job.
- Undoing a merge recreates the merged-away place under its original id and moves its meals back. The bar keeps both until it closes.

### Places

- One function cleans every place name before it is saved or compared. It applies Unicode NFKC, which turns full-width letters into plain ones, trims the ends, and collapses runs of spaces. Comparison ignores case in Kotlin, so `Café` and `CAFÉ` match. The database's case-insensitive unique index stays as a backstop.
- A rename checks for the name among the other places only, so changing only the capitalization never merges a place with itself.
- Empty places are found with `NOT EXISTS (SELECT 1 FROM meal WHERE meal.placeId = place.id)`. `NOT IN` matches nothing once any meal has no place.

### Location

- Precise and approximate location are requested together, as Android requires, on the first Add.
- Finding the location starts when the camera opens, so it is usually ready by the Meal screen.
- The 150 m prefill is skipped when the fix is less accurate than about 100 m.

### Gallery

- Date and time come from the photo's metadata, read with Android's built-in `ExifInterface`. No library is needed.
- Before using the Photo Picker's location option, confirm whether it needs `ACCESS_MEDIA_LOCATION`. If it does, ask first, since the document doesn't name that permission.

### Opening Search with a place (Phase 5)

- The Search route takes an optional place. The Meal and Spending screens pass the place explicitly, so the tab's saved state can never bring back an older search in its place.

### Decisions

Settled on Nov 3, 2026, all as recommended.

| # | Question | Decision |
| --- | --- | --- |
| 1 | Signing | One personal key, kept outside the repo and backed up, signs both debug and release builds. Daily use and timing the 15-second target happen on release builds. Must be in place before the first real meal. See Signing below. |
| 2 | Tapping the place on the Meal screen | Opens the place picker. A "Meals here" row at its top opens Search. |
| 3 | Back after editing a saved meal | Saves automatically if the meal is still valid. Otherwise the last saved version stays. |
| 4 | Which place survives a rename that merges | The existing one. It keeps its name and coordinates; it takes the other's coordinates only if it has none. |
| 5 | Back from the Meal screen after the camera | Returns to the tabs, not the camera |
| 6 | Location provider | Android's built-in fused provider (`LocationManager.FUSED_PROVIDER`). No dependency, and on a Pixel it uses the same location source as Play services. Play Services Location is removed from the catalog. *Superseded on Nov 3, 2026: the phone's location is no longer used.* |
| 7 | Database tests | AndroidX Test, test-only, run on the emulator. Added in step 1 of Phase 2. |
| 8 | Size of Phase 2 | Four steps, each building, running, and committed: data layer and tests; Meal form; camera, gallery, and photos; location and places. One report at the end. |

### Signing

- Gradle reads the key from four properties in `~/.gradle/gradle.properties`, outside the repo: `foodJournal.signing.storeFile`, `.storePassword`, `.keyAlias`, and `.keyPassword`.
- With the key set, debug and release builds are both signed with it. Either can be installed over the other without losing data, and debug builds can still be inspected in Android Studio.
- Without the key, release builds come out unsigned and cannot be installed. A release build signed with any other key can never reach the phone.
- Release builds are shrunk and optimized with R8.
- Switching the phone from today's debug-key build to the personal key needs one uninstall. That is free now, since no meals exist yet.
- Losing the key means never updating the installed app again without uninstalling it, which deletes every meal. Keep a copy of the key file and its password somewhere safe besides this Mac.

### Phase 2 progress

All four steps are done and committed: data layer and tests; Meal form; camera, gallery, and photos; location and places.

- **The camera is the first stage of the new-meal Meal screen**, not a separate destination. One ViewModel holds the photo, its metadata, and the draft. Back from the form returns to the tabs (decision 5). Replacing a photo opens the camera stage, and Back returns to the form.
- **Favorite sits at the top**, as the Screens table says, though Quick logging lists it "below the price".
- **The draft is Compose snapshot state, copied to saved state on each change.** A flow-based draft lost keystrokes when typing fast.
- **Save** shows for a new meal, and for a saved meal once something changes. The number pad's Done saves a valid new meal. Deleting a new meal discards it with no undo bar. The undo window is 4 seconds.
- **Camera permission** is asked when the camera stage opens, and again on later Adds while it is refused; Android limits how often. Refused, the stage shows "Camera access is off" with "Open settings", and gallery and Skip still work.
- **Capture** aims for 2048 × 1536 at 4:3 with minimum latency. The form appears as soon as the capture file is written (under a second on the emulator), and resizing finishes in the app scope; Save waits for it.
- **Processing.** `ImageDecoder` applies the EXIF rotation and reports the rotated size, which was checked with a sideways 3000 × 2000 photo. Photos are stored at up to 1600 px with a 400 px thumbnail, JPEG 85, and no metadata.
- **The form showed the photo at 4:3**, so photo, price, and Save fit above the number pad. Changed after the Phase 2 review: see below.
- **Gallery backfill** reads `DateTimeOriginal` (falling back to `DateTime`) and GPS with the framework `ExifInterface`. A photo without a date keeps the time Add was tapped.
- **Photo Picker location.** The picker gets `MediaStore.EXTRA_REQUEST_LOCATION_METADATA_ACCESS = true`. The constant is behind a feature flag and missing from the public API 37 jar, so its documented value `"android.provider.extra.REQUEST_LOCATION_METADATA_ACCESS"` is used. For apps targeting above Android 16 it is the only control, so `ACCESS_MEDIA_LOCATION` is not needed. On the emulator (picker version 17), there was no location prompt and picking needs a Done tap. Whether location arrives on the phone is still unverified.
- **Known nit.** The date picker starts weeks on Sunday, following the locale, while the app's weeks run Monday to Sunday.
- **Saved meals can't be reopened from the UI** until the Journal arrives in Phase 3. Editing was tested with a throwaway build.

#### Step 4: location, place picker, rename and merge

- **Permissions.** The Meal screen asks as the camera opens, in one request: the camera whenever it is off, and both location permissions on the first Add only. A DataStore flag records that location was asked, set as the request goes out, so it is never asked again whatever the answer. Camera access is checked again on resume, so turning it on in Settings works on return.
- **DataStore** is in the app from now, for that flag. `AppSettings` in the container will hold the last backup date in Phase 6.
- **Finding the location.** `LocationTracker` uses `LocationManager.FUSED_PROVIDER`. It offers a fix under two minutes old at once, then asks for a current one at high accuracy, for up to 30 seconds. The default balanced quality returned nothing within 30 seconds on the emulator. Android may answer with a fix it cached in the last 30 seconds or so. A phone fix without an accuracy is treated as vague.
- **Where a meal is.** Camera and Skip meals use the phone's fix when it is within 100 m accuracy. Gallery meals use only the photo's location, and none without one. A saved meal has none, since the phone is no longer where it was eaten: its picker lists recent places first, and a new place typed for it is saved without coordinates. Taking a photo to replace a gallery photo switches the meal back to the phone's fix; the time stays as it was.
- **Prefill** applies to new meals until a place is chosen by hand. It runs again whenever the places, the fix, or the photo change, so a late fix still fills the place.
- **A zeroed photo location counts as none.** The emulator's picker withholds location by setting the coordinates to 0, 0 rather than removing them, which first saved a place in the Gulf of Guinea. Coordinates of exactly 0, 0, or out of range, are now ignored.
- **The place row** sits between Name and Time and opens the picker. An empty place shows nothing, like an empty name.
- **The picker** is a full-height sheet. The field has focus when it opens, since a place with no saved match is usually a new one; the nearest places stay in view above the keyboard. A narrow column on the left holds the icons (search, add, check), so the names line up. "No place" shows only before anything is typed. A new place chosen but not yet saved is listed first, marked "New". Distances show only when the meal has a usable location. Long distances are grouped: "1,240 km".
- **Rename** happens in the row. The name is selected, so typing replaces it, and Done or the check saves it. Renaming the meal's own place counts as choosing it by hand. A merge moves the meal to the surviving place, and Undo moves it back. The undo bar shows inside the sheet.
- **A place that vanished before Save** is left off the meal instead of failing the save. This can happen when an empty place, kept for an open undo bar, is prefilled and the bar then closes.
- **Save reads the latest draft** once the photo is ready, so gallery metadata that lands while Save is tapped is no longer lost. This was a gap from step 3.
- **Not built yet.** The "Meals here" row at the top of the picker (decision 2) comes in Phase 5, with Search.
- **Photo Picker location on the phone** is still unverified. On the emulator, the photo's location arrived as 0, 0, so gallery prefill was tested only through the database and unit tests.
