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

Location uses Android's built-in fused provider, so there is no location library (decision 6 under Phase 2 plan).

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
- **Axis settings.** Fraunces is a variable font with four axes.
  - Display: weight 300, optical size 144, softness 100, wonky 0. Light numerals with high contrast.
  - Headline: weight 500, optical size 32, softness 100, wonky 0. Heavier, so headings hold their own beside the sans.
- **No tabular figures.** Fraunces only has proportional figures. Neither the Google Fonts build nor the designers' own build includes `tnum`. So Fraunces sets only standalone numbers. Numbers that line up stay in the sans with tabular figures: lists, tiles, chart labels, the total beside a heading, and the search total.
- **Latin only.** Fraunces has no Chinese characters. Headings are dates and month names, so this doesn't come up. Any Chinese text in a Fraunces style falls back to the system font.
- **File.** The Google Fonts build of the variable font, `Fraunces[SOFT,WONK,opsz,wght].ttf`: 360 KB, SHA-256 `177ff6c0f14e5550a3c624247cd1189611d4eb65d000b14944c63d967958abbb`. It is bundled as `app/src/main/res/font/fraunces.ttf`, so nothing is downloaded when the app runs. Its license, the SIL Open Font License 1.1, is in `licenses/Fraunces-OFL.txt`.
- **Preview.** `ui/preview/TypePreview.kt` shows the styles together, in light and dark. The axis settings were checked on the API 37 emulator.

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
| 6 | Location provider | Android's built-in fused provider (`LocationManager.FUSED_PROVIDER`). No dependency, and on a Pixel it uses the same location source as Play services. Play Services Location is removed from the catalog. |
| 7 | Database tests | AndroidX Test, test-only, run on the emulator. Added in step 1 of Phase 2. |
| 8 | Size of Phase 2 | Four steps, each building, running, and committed: data layer and tests; Meal form; camera, gallery, and photos; location and places. One report at the end. |

### Signing

- Gradle reads the key from four properties in `~/.gradle/gradle.properties`, outside the repo: `foodJournal.signing.storeFile`, `.storePassword`, `.keyAlias`, and `.keyPassword`.
- With the key set, debug and release builds are both signed with it. Either can be installed over the other without losing data, and debug builds can still be inspected in Android Studio.
- Without the key, release builds come out unsigned and cannot be installed. A release build signed with any other key can never reach the phone.
- Release builds are shrunk and optimized with R8.
- Switching the phone from today's debug-key build to the personal key needs one uninstall. That is free now, since no meals exist yet.
- Losing the key means never updating the installed app again without uninstalling it, which deletes every meal. Keep a copy of the key file and its password somewhere safe besides this Mac.
