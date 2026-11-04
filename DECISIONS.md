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

The app does not use the phone's location (see "Places without phone location" below), so there is no location library. DataStore holds the theme choice and the last backup's day, from Phase 6.

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
| Settings contents | A small menu from the settings icon (Export, Import), not a separate screen. *Superseded on Nov 4, 2026: a Settings screen; see Phase 6.* | 6 |

### Gaps

- **Speed of the 15-second target.** A first GPS fix can take several seconds. Phase 2 starts finding the location when the camera opens, shows the Meal screen at once, and fills the place when the fix arrives. It never blocks Save.
- **Imprecise location.** A 150 m match needs a precise fix. Phase 2 skips the prefill whenever the fix is less accurate than about 100 m, which also covers approximate-only permission.
- **Photo Picker location option.** The exact API, and whether it needs `ACCESS_MEDIA_LOCATION`, must be confirmed in Phase 2 before anything is added.
- **Orphaned files.** If the app is killed mid-flow, photos and empty places can be left behind. See the Phase 2 plan for how they are cleaned up without touching unsaved work.
- **Font.** No family is named. The interface uses the system sans-serif (Roboto). Fraunces was added after Phase 1 for big numbers and headings (see Type below).
- **Light and dark.** No switch is specified. The app follows the system setting. *From Phase 6, on request, Settings offers System, Light, and Dark; System stays the default.*
- **Number format.** "2,340" uses English grouping on every device, since the app is English only.
- **App icon.** Not specified. Phase 1 used a monochrome plate: two thin rings, white on black. On Nov 4, 2026, the user chose a filmstrip instead: three white portrait tiles on black, rounded only at the strip's ends, like a week in the Journal. The same drawing is the themed-icon layer; its solid shapes hold up at small sizes better than thin rings.
- **Test libraries.** Unit tests need JUnit 4. It is test-only and never ships in the app.

## Decisions

### Phase 1

- **Room 2.8, not Room 3.** Room 3.0 is stable but moves to new `androidx.room3` packages. The product document only says Room, and 2.8 is the mature line. Moving to Room 3 later would be a dependency change, not a data migration.
- **Kotlin through AGP's built-in support.** AGP 9 compiles Kotlin itself. The Kotlin Gradle plugin is declared but not applied, which pins the compiler to 2.4.21.
- **SDK levels.** compileSdk, minSdk, and targetSdk are all 37. Bytecode targets Java 17. *From version 1.00.01, minSdk is 36; see Android 16 below.*
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
6. **Device tests never touch the phone.** `connected…AndroidTest` tasks refuse to run unless `ANDROID_SERIAL` names an emulator, before anything is installed. Run them as `ANDROID_SERIAL=emulator-5560 ./gradlew :app:connectedDebugAndroidTest`. Since the Phase 3 review, the whole value must be one emulator serial: a comma-separated list such as `emulator-5560,<phone>` is refused, since it would reach the phone too.
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
  - A name whose longest word is too wide for the tile, as at a large font size, first shrinks by up to a fifth until the word fits. Past that, words break with a hyphen ("Extraordi-narily"), using English patterns first, as the interface is English, then the phone's own locales, so Chinese names keep their glyphs. A word with no hyphenation point that still doesn't fit at four-fifths size can still break mid-word. Chinese text breaks between characters, as before. Added after the Phase 3 review.
  - A thumbnail that can't be read, such as one not yet rebuilt after a restore, gives way to the full photo, then to the plain tile with the name. Added after the Phase 3 review.
  - No prices on tiles: totals are visible but never the focus.
- **Scroll position.** The current week's strip opens at its newest meal, so a meal just logged is in view. Strips of earlier weeks open at Monday. A meal that turns up later in a strip, logged meanwhile or brought back by Undo, scrolls into view. Decided on Nov 3, 2026, after the Phase 3 review.
- **Opening a meal.** Tapping a tile opens the Meal screen, with the photo at its full 3:4. A second tile tapped while the first opens is ignored.
- **Settings icon.** Three sliders, top right. A gear's small teeth blur at this stroke weight. It opened nothing until Phase 6, when it opens Settings.
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

## Deleting places

Requested on Nov 3, 2026, after the change above, for a clean start: saved places still held coordinates taken from the phone. The product document only says a place with no meals left is removed.

- **Where.** In the place picker, a place's rename row has a delete icon beside the check.
- **No confirmation, with Undo**, like every delete. The grey bar shows inside the sheet.
- **Meals are kept**, without a place. A meal open on the Meal screen loses the place too.
- **Undo** brings the place back with its id and coordinates, and puts it back on its meals, except any given another place meanwhile. If a place with its name was created meanwhile, the meals join that one instead, which takes the coordinates if it has none. The open meal gets its place back if it has none.
- **How the open meal follows an undo.** `UndoableEdits` announces each undo once its data is back, and the Meal screen follows that, for merges as well as deletions. It used to infer the undo from the place list showing the place gone and then back, which a quick undo could skip, leaving the meal on the survivor or without a place.

## Phase 4: Spending

Settled on Nov 4, 2026, before any Phase 4 code, all as recommended. These add to the Phase 4 rows of the Ambiguities table above.

- **The switch.** Week, Month, and Year sit in a full-width segmented pill above the periods, after Retro's Recap screen: a grey track with the selected segment filled in ink. It stays put while the periods swipe beneath it.
- **Swiping.** Back to the period of the earliest meal, forward to the current period. With no meals at all, the tab shows only "No meals", with no switch.
- **Switching Week, Month, or Year** keeps the date in view: the new period is the one holding the shown period's last day, or today if that comes sooner. The tab opens on the current month at launch and keeps its selection while switching tabs. The selection is not kept across a cold start.
- **"Today" is read again on resume.** If the current period was in view, the new current period comes into view. An older period stays put.
- **Meals dated in the future,** such as later today, a camera with a wrong clock, or a flight west, count toward the period they fall in. Elapsed days still end at today. A meal in a later period than today's shows in the Journal but not in Spending.
- **Period names.** Week "Nov 2 – 8", as in the Journal. Month "November", with the year only outside the current year: "November 2025". Year "2026". The name is set in the serif headline, the total in the serif display.
- **Change line.** A whole percent with a true minus: "−12%", "+4%", "0%". Anything that rounds to zero shows "0%" with no sign, and halves round away from zero. Week: "vs last week" for the current week, "vs previous week" for older ones. Month: "vs Oct", and "vs Dec" in January, with no year. Year: "vs 2025".
- **Unfinished periods** compare whole days counted from the start, so a year's comparison counts days from Jan 1 rather than matching dates, and Feb 29 never shifts it. The period's own total is the one shown, including any meal later today.
- **Averages line.** "312 a day · 156 a meal", grey sans, each rounded to whole dollars, halves up.
- **Bars.**
  - Grey bars on a hairline baseline. A tap shows a small sans amount above the bar and turns it ink. A second tap clears it, and changing period resets it.
  - Days with nothing spent can be tapped and show "0". Future days have no bar and can't be tapped.
  - No y-axis. The axis labels are "M T W T F S S" for a week, "1 8 15 22 29" for a month, and month initials for a year.
  - Each bar is one tap target the full height of the chart, and reads aloud as, for example, "Nov 12, 340".
- **By place.** Not tappable until Phase 5, which opens Search. Each place shows its total, its visits ("4 visits", the number of its meals in the period), and a share bar. Other shows its total, its meals ("6 meals"), and a share bar, and is hidden only when it holds no meals.
- **By time of day.** All four bands always show, with a total and a share bar.
- **Share bars** are measured against the period total, so every bar is on one scale. They are empty when the total is 0. No percent text.
- **Priciest.**
  - Three tiles fill the row, in the Journal's tile style, and a tap opens the meal. Each tile shows its price in the lower left where the Journal shows the weekday, since the price is why it's there.
  - Fewer tiles show when there are fewer meals. Meals priced 0 are left out, since they are not pricey, and the section is hidden when no meal costs anything.
  - Ties go to the most recent meal.

**Built** on Nov 4, 2026.

- **Layout, top to bottom.** The switch, then the period name, the total, the change line, and the averages. Then four sections under small grey labels: Over time, By place, By time of day, and Priciest. Sections sit on the plain background rather than grey cards, so the bars have room. An empty period shows its name above "No meals".
- **Rules in one place.** `logic/Spending.kt` holds periods, comparisons, averages, bars, places, bands, and labels, with JVM tests. The ViewModel groups meals by period once and builds every page for the chosen kind off the main thread.
- **The Journal's tile is shared.** It moved to `ui/components/MealTile.kt`, taking its label and size as parameters. The Journal looks as before.
- **Place names** come from a new `PlaceDao.observeAll()`. No schema change.
- **Swiping** uses Compose Foundation's `HorizontalPager`, keyed by period, so a backfilled older meal doesn't shift the period in view.
- **Checked on the emulator** with seeded meals, in light and dark: each kind, swiping, switching kinds mid-browse, a tapped bar, an empty week, and a Priciest tile opening its meal.

**Review fixes**, from an independent review of `af5bc77`, made on Nov 4, 2026.

1. **The pager's page is no longer saved.** The view model holds the selection. A page restored on its own, after Android closed the app in the background, could disagree with it and be reported as one swiped to. The tab now always opens on the view model's period: the current month after Android closes the app, and the period you were on after a tab switch. Both were checked on the emulator.
2. **A period's last day is still unfinished.** On Nov 30, November is compared with Oct 1 – 30, not all of October. All of October counts from Dec 1. The product document says "the same number of days" for an unfinished period, and its last day is not yet over.
3. **Future bars carry no amount,** so a meal dated later, such as one from a camera with a wrong clock, no longer sets the chart's scale and shrinks the bars that show.
4. **Visit and meal counts are grouped,** as every other number: "1,043 meals".
5. **The percent can't overflow.** A change too large for an Int, from a tiny previous total, is held at the largest one. Only absurd totals reach it.
6. **The chart measures its labels,** so at large font sizes the tapped amount and the axis stay inside the chart, and the bars give up the room. Checked at twice the default size.
7. **Bars say what a tap does:** "Show amount", as a button. Each month bar is still only about 12 dp wide, one target per bar as settled; the slots cover the chart with no gaps, so every tap lands on a bar.

**Left open:** one stray old date, such as a camera's default of 1980, would add thousands of empty weeks to swipe through. That is a product question for later, not a bug.

## Phase 5: Search

Settled on Nov 4, 2026, before any Phase 5 code, all as recommended. These add to the Search rows of the Ambiguities table and to "Opening Search with a place" under the Phase 2 plan.

- **Result rows.** A 3:4 thumbnail, or a grey tile when the meal has no photo. Beside it, the name, with "Tue, Nov 3 · 林家乾麵" in grey below and the price on the right in tabular sans. The year shows only outside the current year. A small star marks favorites. Tapping a row opens the meal.
- **Matching.** Ignores case and full-width letters, cleaned the way place names are. Each typed word must appear in the name, the note, or the place, so "beef soup" finds "Beef noodle soup". Chinese works by substring. Filtering happens in memory, from every meal, as a pure function with JVM tests.
- **Layout.** The search field is a grey pill with the place chip inside it, before the text. Below it sit two pill filters, "Dates" and "★ Favorites". Then the total, "42 meals · 12,340", in sans. Then the results, newest first.
- **Dates.** "Dates" opens Material's date-range dialog, which can't pick future days. Once set, the pill reads "Oct 1 – Nov 4", written as the Journal writes weeks, with an × that clears it. Both days are included in full. The dialog starts weeks on Sunday, the same known nit as the Meal screen.
- **The place chip.** With a chip, the text filters within that place. Its × clears it. Place names in result rows aren't tappable, so a row always opens its meal.
- **Arriving with a place** opens the Search tab with a fresh search holding only that place: no text and no other filters. Back follows the tab rule and returns to Journal.
- **"Meals here" in the place picker** shows only for a saved meal whose place is saved, at the top of the list, before anything is typed. It closes the Meal screen as Back does, so valid edits are kept, then opens Search. A new meal never shows it, since leaving would discard the meal.
- **From Spending,** a place's row opens Search with that place and the period on screen as the dates, so the totals match. The dates run up to today, or to the period's end once it is over, or to a later meal's day when one is dated after today. Other isn't tappable.
- **Empty results** show one line, "No meals found". With no meals at all, the tab keeps its empty state, "Nothing to search yet".
- **Focus and memory.** The field doesn't take focus when the tab opens. A search is kept while switching tabs, but not across a cold start.

**Built** on Nov 4, 2026.

- **Rules in one place.** `logic/Search.kt` holds the matching, with JVM tests. A space joins a meal's name, note, and place before matching, so no word matches across two of them.
- **The route.** `SearchRoute` carries an optional place and first and last days. A screen above the tabs, the Meal screen, hands its place to the tabs, which open Search once they show again. Opening Search this way replaces any Search showing, and drops the tab's saved search for good, so the next tab switch brings back this search rather than the older one.
- **Back** from a search opened with a place returns to Journal and drops that search, as Back drops any tab's state. The Search tab then shows a search without the place.
- **The dates are one saved value,** so the first and last day always change together. Kept as two, they briefly disagreed, and results ran past the last day for a moment; a device test caught it.
- **The date-range dialog** sets its title and headline with dialog padding, since the defaults leave room for a full-screen picker's close button. The days between the two ends get a band in the ink at 12%, because Material's default grey matched the dialog's own and vanished.
- **A chip whose place is gone,** merged away or deleted, disappears and filters nothing out. It comes back if the change is undone.
- **Meals without a name** show their date and place as their only line.
- **Spending's place rows** are buttons labelled "Show meals".
- **Checked on the emulator** with seeded meals, in light and dark: typing, Favorites, picking a range, Spending's October place row giving the same 8 meals and 5,440 in Search, and "Meals here" from a saved meal.

**Review fixes**, from an independent review of `6636cfa`, made on Nov 4, 2026.

1. **A search opened with a place survives a tab switch.** Opening Search from Spending or "Meals here" used to save the replaced search, so switching to Journal and back brought the older search back and lost the place. The older one is now dropped. Checked by hand on the emulator.
2. **Spending's dates match its totals.** A meal dated after today, which counts toward its period, fell outside the dates Search was given, so Search showed less than Spending. The last day is now the later of today (or the period's end) and the period's latest meal.
3. **"Meals here" never offers a place Back would drop.** With edits that can't be saved, such as an empty price, Back keeps the saved meal at its saved place. A place picked in those edits no longer offers "Meals here".
4. **Accessibility.** Spending's place rows are buttons. Favorites reads as a switch, on or off, in place of a separate "On". The filter pills and the place chip are still drawn at 36 and 32 dp, but take taps over a full 48 dp.
5. **The keyboard no longer hides results.** The Search column sits above it, so "No meals found" and the last results stay in view.
6. **"Meals here" survives a rotation** between the Meal screen closing and Search opening.

**Left open:** tab navigation and the Meal screen's hand-off to Search have no automated test; that needs the Compose UI test or navigation-testing library, which would need an OK first.

## Phase 6: Settings, backup, and restore

Settled on Nov 4, 2026, all as recommended. On request, Settings grows from the small Export and Import menu the Ambiguities table first settled into a screen with a theme choice and an About section. This overrides that row, and the "Light and dark" gap, which said the app only follows the system.

- **Settings screen.** The Journal's settings icon opens it as a full screen over the tabs, like the Meal screen, with a back arrow and the title "Settings". Rows sit on rounded grey cards under grey section labels, after Retro's Settings screen.
- **Appearance.** A System, Light, Dark switch, as the segmented pill Spending uses. System is the default. The choice goes to Android's own per-app night mode (`UiModeManager.setApplicationNightMode`), which Android keeps and applies before the app draws, so launch never flashes the wrong theme. Android offers no way to read it back, so DataStore keeps the choice for the switch, and the app sets it again at launch in case the two ever drift apart.
- **No color preferences.** The interface is black, white, and greys, so the food photos are the only color. Asked for and declined on the product document's own principle.
- **Backup.** Export, Import, and a grey line with the last backup's date, or "Never backed up".
  - **Export** opens Android's save dialog with the name `food-journal-2026-11-04.zip`. While it runs, its row reads "Exporting…", and leaving the screen doesn't stop it.
  - **Import** opens a file, reads and checks it, then asks before replacing anything: "This backup has 412 meals, the last on Oct 30. It replaces the 99 meals on this phone." An open undo bar closes first. Thumbnails are rebuilt in the background; tiles show the full photo until then.
  - **The reminder line** on the Journal starts Export directly.
- **About.** "Version 0.1", and "Open-source licenses", a plain text page. Fraunces' license asks that its text travel with the font, and the AndroidX libraries carry Apache 2.0 notices.
- **Not added:** week start, currency, and language, which the product document fixes; a switch for the reminder; and "delete all data", since importing a backup already gives a fresh start.
- **The file.** One ZIP. `backup.json` holds a format version, the time of the export, the places, and the meals, with their ids. `photos/` holds the stored 1600 px photos, uncompressed. Thumbnails are left out.
- **Checks on import,** before anything changes: the format version; every meal has a photo or a name and a price from 0 to 999,999, and its time reads; ids are unique; every place a meal names is in the file; place names are present and unique, ignoring case; every photo a meal names is in the file. Entries outside `backup.json` and `photos/<name>` are ignored, and a name with a folder in it is refused, so no file can land outside the import folder.
- **Replacing the data.** The backup unpacks into a folder beside the app's own. The photo folders are swapped, then one database transaction replaces every place and meal. If the transaction fails, the folders swap back. A failed import leaves the meals and photos as they were.
- **Last backup after an import** is the backup's own export date, since the phone then holds exactly what that backup holds.

**Built** on Nov 4, 2026.

- **The screen.** "← Settings", the title in the serif headline, then Appearance, Backup, and About under grey labels. Rows have a semibold title and a grey line below it, as Retro's do, and a row that opens a page ends in a chevron.
- **Two backup rows, not three.** The last backup's date is Export's grey line: "Last backup Oct 2, 2026", or "Never backed up". Import's reads "Replaces the meals on this phone". The date always shows its year here, since it says how old the backup is.
- **The theme switch** is Spending's segmented pill, now one shared component, so the two can't drift apart.
- **Busy rows.** Export reads "Exporting…", and Import "Reading backup…" while it unpacks and checks the file, then "Restoring…". Export and Import each wait while the other is busy; the rows turn grey.
- **The confirmation** reads "This backup has 412 meals, the last on Oct 30. It replaces the 99 meals on this phone.", with Cancel and Replace. A backup without meals says "This backup has no meals.", and a phone without any, "This phone has no meals yet." The last meal's year shows only outside the current year, as elsewhere.
- **Refusals** are one line and OK, each ending "Nothing on this phone changed.": not a Food Journal backup; from a newer version of the app; damaged; or couldn't be read, such as when the phone runs out of room unpacking it. The format is read before the records, so a newer backup is called newer even if its records no longer read. A backup.json over 64 MB is taken as damaged.
- **After a restore** the Settings screen closes onto the Journal, and the tabs start afresh. A search or period kept from before could hold a place id that now means another place. Back waits the moment a restore takes.
- **No edit runs during a restore.** It closes an open undo bar and holds the undo lock until the meals are replaced, so a deleted meal's photo is removed before the photo folders swap, never after.
- **Thumbnails.** Photos the phone already had keep their thumbnails, since every photo has its own random name. The rest are rebuilt in the background from the stored photos.
- **An import cut short**, by the app being closed mid-restore, leaves nothing missing. At the next launch, photos set aside go back into the photo folder beside the new ones, the launch sweep removes those no meal uses, and a half-unpacked import is deleted. Whichever meals the database holds keep their photos.
- **Names that leave the folder.** Android's own ZIP reader refuses an entry whose name holds "..", and with it the whole file, which is then reported as damaged. Other stray entries are skipped.
- **The reminder** sits at the left of the Journal's top bar, beside the settings icon: "Last backup Oct 2 · Back up", or "Not backed up yet · Back up", grey with the action in ink. It shows only while there are meals. A tap opens the save dialog at once, the line reads "Exporting…" while it runs, and it disappears when the export is done. A failed export shows its reason where it was started, on the Journal or in Settings.
- **Export failures.** A meal whose photo file is missing stops the export before anything is written: "A meal's photo is missing, so the backup wasn't made." A file that can't be written, such as on a full drive, is deleted rather than left half-written. If a meal is deleted during an export and its undo bar closes before the export reaches its photo, that export fails and can be run again.
- **Import picks** ZIP files, including those other file apps label as "x-zip-compressed" or plain binary.
- **About.** The version comes from the app itself: "0.1" when Phase 6 was built, "1.00.00" from Nov 4, 2026, as the user asked, "1.00.01" with Android 16 support, "1.01.00" with Share Recap, and "1.01.01" with the choice of a recap's figures. The version code stays 1, so the new build installs over the old one with its data. The licenses page holds Fraunces' Open Font License; the Apache License 2.0 for AndroidX, Coil, Dagger, Guava, Kotlin, kotlinx.coroutines, kotlinx.serialization, and Okio; and the BSD license of Protocol Buffers, which DataStore carries inside it, found by listing what the release build ships. The texts are wrapped to the screen. The license file in `licenses/` is copied into the app's resources.
- **Known nit.** The reminder reads today's date when the meals or the backup day change, so if the app stays open across midnight, a reminder due that day can appear only once something changes.

## Android 16

Requested on Nov 4, 2026, after version 1.00.00, so friends on Android 16 can install the app. This overrides the product document's Targets, which set the minimum SDK to the test phone's Android version.

- **minSdk is 36.** compileSdk and targetSdk stay 37. With minSdk 36, lint finds no call that needs API 37, and the unit tests pass.
- **Gallery location needs Android 17.** The Photo Picker's location option is new in Android 17. Android 16's picker doesn't know it and is expected to strip the location, so a gallery meal there takes the photo's date and time, but its place is picked by hand. Android 16 would need `ACCESS_MEDIA_LOCATION` for the location; it was not added, so the camera stays the only permission.
- **Not tested on Android 16,** on a device or an emulator, at the user's request. The device tests have run on Android 17 only.
- **Version 1.00.01.** The version code stays 1, so it installs over 1.00.00 with its data.

## Share Recap

Requested on Nov 4, 2026, after version 1.00.01, with the Retro monthly recap as the visual reference. This overrides the product document's out-of-scope "social sharing" and DECISIONS' "Not adopted: … sharing" from Retro: a recap leaves the phone only when Share is tapped, and holds only photos, dates, and totals. Settled before any code, all as recommended, except where noted.

Shipped as version 1.01.00, at the user's request; the version code stays 1.

- **The button.** A small outlined "Share" pill beside the period name and total, on Week, Month, and Year. It recaps the period in view, shows only for a period with meals, and reads "Making…" while the image is drawn. *Changed in version 1.01.01: it opens a sheet, and "Making…" moved there. See "Choosing a recap's figures".*
- **Straight to the share sheet,** which shows the image as its preview, with no screen of its own. The image goes through AndroidX's `FileProvider`, which the app already shipped, over the cache's `recaps/` folder only; each recap replaces the last. No new library or permission. *Changed in version 1.01.01: the sheet comes first.*
- **Every meal, however many.** At first settled as a 24-tile cap, favorites first; changed the same day at the user's request: the collage shows the real data, every meal in the order eaten, and the layout adapts. The columns keep tiles closest to the photos' 3:4; rows fill evenly, fuller rows first, and a row with fewer tiles widens them, so there are never gaps. Gaps narrow as tiles multiply.
- **The image.** A lossless PNG, 3240 × 5760: three times Instagram Stories' 1080 × 1920, as the user asked for the highest resolution. Each photo is decoded at the pixels its tile covers, from the 400 px thumbnail when the tile is no larger, so a year's recap stays quick. Stored photos are 1600 px, so a recap of a few meals gains nothing above this size.
- **Meals without a photo** are the Journal's plain grey tile with the name, as the user asked.
- **The band** follows the app's theme, white in light and black in dark, in the app's own greys. The period is set in Fraunces' display cut, as the app writes it ("Nov 2 – 8", "November", "2026"), shrinking if it would run into the year at its right, which is "2025 – 26" for a week across New Year and left out for a year. "Food Journal" sits below the year, as Retro's recap signs itself.
- **Figures.** "24 meals · 12 places · 3,450": places with at least one meal, left out when none. Below, in grey, the difference from the period before, stated plainly ("120 less than last week", "120 more than Oct", "The same as 2025") rather than as "saved" or "spent more", since numbers are shown, never judged. It shows only when the Spending tab would show its change line, by the same rules, which now live in one function, `comparedTotal`.
- **Rules in one place.** `logic/Recap.kt` holds the meal order, the layout, and the words, with JVM tests; `recap/RecapImages.kt` draws them with Android's Canvas, off the main thread.

## Choosing a recap's figures

Requested on Nov 4, 2026, after version 1.01.00: each figure on a recap should be optional. Shipped as version 1.01.01, at the user's request; the version code stays 1, so it installs over 1.01.00 with its data.

- **The sheet.** The Share pill opens a small sheet, headed like the place picker's, with a switch for Meals, Places, Total spent, and Change, each showing the value it would add. A figure with nothing to say, such as Change with nothing to compare, has no switch. The sheet's Share makes the image and hands it to Android's share sheet, as before.
- **The image.** The period and year always show. Figures switched off drop out of their line, and if all three are off, the comparison moves up into its place.
- **Making and closing.** Share reads "Making…" while the image is drawn. The close button slides the sheet away, as on the place picker, but while the image is being drawn it closes at once and drops the image, so it can't reach Android's share sheet afterward. A recap that can't be drawn is logged and leaves the sheet open; one whose meals or saved choice can't be read is logged and leaves it closed.
- **A short window,** such as a phone on its side: the rows scroll inside the card, and Share stays in view.
- **The choice is remembered** in DataStore for the next recap, across launches, from the moment a switch changes, whether or not that recap is shared. A save that fails is logged by the app's scope, and the sheet keeps the choice. All four are on until the first change. Added without asking, since choosing again for every recap would be a chore; it is one small step to take out.
- **Left open.** The sheet's layout and taps have no automated test, since that needs the Compose UI test library, which would need an OK first. The ViewModel, the saved choice, and the image each have device tests, and the short window was checked by hand on the emulator.

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
- **Phase 4, Spending.** The Week, Month, Year switch as a segmented pill, with the selected segment filled. Applied in Phase 4.
- **Phase 6, Settings menu.** Rows grouped on rounded grey cards, with grey section labels. Applied in Phase 6.
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
- **"Meals here"** at the top of the picker (decision 2) was built in Phase 5, with Search.
- **Photo Picker location on the phone** is still unverified. On the emulator, the photo's location arrived as 0, 0, so gallery prefill was tested only through the database and unit tests.
