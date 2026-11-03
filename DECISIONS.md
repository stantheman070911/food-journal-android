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
| kotlinx.serialization | 1.12.0 | Phase 1 (routes), Phase 6 (backup) |
| CameraX | 1.6.2 | Phase 2 |
| Coil 3 | 3.6.3 | Phase 2 |
| Play Services Location | 21.4.0 | Phase 2 |
| DataStore | 1.3.0 | Phase 6 |
| JUnit 4 (tests only) | 4.13.2 | Phase 1 |

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
| Year in week headers | Shown only for weeks outside the current year: "Dec 29, 2025 – Jan 4" | 3 |
| Weeks with no meals | Left out | 3 |
| Tile order in a week | Monday to Sunday, left to right | 3 |
| "Visits" | Number of meals at the place in the period | 4 |
| Change-line label | Week: "vs last week". Month: "vs Oct". Year: "vs 2025". | 4 |
| Previous period with meals but a total of 0 | Change line omitted, as when it has no meals | 4 |
| Unfinished period longer than the previous one | Compare up to the previous period's last day | 4 |
| Swiping to future periods | Stops at the current period | 4 |
| Ties in top places and priciest meals | Most recent first | 4 |
| Search before typing | All meals, newest first, with their total | 5 |
| What "original photos" means in the backup | The stored 1600 px photos. Camera originals are never kept. | 6 |
| Where the backup reminder shows | One quiet line at the top of Journal, also shown when no export has ever been made and the first meal is over 30 days old | 6 |
| Settings contents | A small menu from the settings icon (Export, Import), not a separate screen | 6 |

### Gaps

- **Speed of the 15-second target.** A first GPS fix can take several seconds. Phase 2 will show the Meal screen at once and fill the place when the fix arrives, never blocking Save.
- **Approximate location.** If only approximate location is granted, a 150 m match is unreliable. Phase 2 will skip the prefill in that case.
- **Photo Picker location option.** The exact API, and whether it needs `ACCESS_MEDIA_LOCATION`, must be confirmed in Phase 2 before anything is added.
- **Orphaned photos.** If the app is killed while an undo bar is open, the photo is never deleted. Phase 2 will remove unreferenced photos on start.
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
