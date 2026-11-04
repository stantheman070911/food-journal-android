# Food Journal

This document describes Food Journal at version 1.01.01: what it does, how it looks, and how it is built.

## Overview

Food Journal is a personal Android app that records what I eat, where, and what it cost. It is a diary, not a diet tracker: there are no calories, goals, or scores.

The ideas behind it:

- **Less is more.** Every screen, field, and dependency earns its place.
- **Fast logging.** Add, shutter, price, Save: under 15 seconds.
- **Little is required.** A meal needs a photo or a name, plus a price.
- **Photos lead.** Numbers support them. The interface is monochrome, so the food photos are the only color.
- **Numbers are shown, never judged.** No budgets, targets, streaks, badges, or warnings.
- **Data stays on the phone.** No account, no sign-in, no network.
- **One currency.** New Taiwan dollars, stored as whole dollars and shown with no prefix: "280".
- **English interface.**

## Data model

Two records, Meal and Place. A meal belongs to at most one place.

**Meal**

| Field | Type | Notes |
| --- | --- | --- |
| id | Long | Auto |
| eatenAt | Local date-time | Required. Set to now; editable. |
| photo | Text | File name in `photos/`. A meal has a photo, a name, or both. |
| name | Text | |
| price | Int | Required. 0 to 999,999. |
| isFavorite | Boolean | Default false |
| note | Text | |
| placeId | Long | Optional link to Place. Set to empty when the place is deleted. |

**Place**

| Field | Type | Notes |
| --- | --- | --- |
| id | Long | Auto |
| name | Text | Required. Unique, ignoring case. |
| latitude, longitude | Double | Where the place was first saved. Empty without a location. |

**Rules**

- **`eatenAt` is wall-clock time**, stored as text such as `2026-11-03T12:30:00`. A meal's day and time of day never shift when the phone changes time zone. Photo metadata records wall-clock time too, and the text sorts in time order.
- **A meal's day** is the date of `eatenAt`, so a 01:00 snack counts toward that day. Weeks run Monday to Sunday.
- **`photo` is a file name**, not a path, so a restored backup works on a fresh install.
- **Place names are cleaned** before they are saved or compared: Unicode NFKC (full-width letters become plain), ends trimmed, runs of spaces collapsed. Comparison ignores case, so `Café` and `CAFÉ` match. A case-insensitive unique index in the database is the backstop.
- **Coordinates** come from a gallery photo's location when the place is created for that meal. Places created any other way have none.
- **A place with no meals left is removed**, once any undo bar closes.
- **Nothing derived is stored.** Visits, totals, last visit, and time of day are computed from meals.
- **Schema.** Room exports each schema version to `app/schemas/`, kept in Git for migrations. The current version is 1.

## Navigation

- **Tabs.** Journal, Spending, and Search sit in an icon-only bottom bar, 56 dp tall. The selected tab is ink (black, or white in dark), the others grey. There is no highlight pill. A hairline separates the bar from the content.
- **Add.** A round button, ink on every tab, opens a new meal.
- **Screens above the tabs.** The Meal and Settings screens cover the bottom bar.
- **Back.** From a tab, Back returns to Journal, then exits. Each tab keeps its state while switching tabs, but not across a cold start.
- **Motion.** Tabs crossfade in 150 ms. The Meal and Settings screens fade in with a slight rise over 220 ms. Nothing slows logging.

## Logging a meal

**The flow**

1. **Tap Add.** The Meal screen opens on its camera stage, with a gallery button beside the shutter and Skip.
2. **Take the photo.** There is no confirm step. The form appears as soon as the capture is written. Resizing finishes in the background.
3. **Type the price.** The price field has focus and the number pad is open.
4. **Tap Save.**

**Camera**

- The camera permission is asked when the camera stage opens. It is asked again on later Adds while it is refused, as often as Android allows.
- Refused, the stage shows "Camera access is off" with "Open settings". Gallery and Skip still work. Access is checked again on resume.
- Capture aims for 2048 × 1536 at 4:3, with minimum latency.
- The close button sits on a dark grey disc.

**The Meal screen** adds, reads, and edits a meal, and is always editable.

- **Top.** Back, Favorite, and Delete.
- **Photo.** Shown at 3:4, inset 16 dp with 20 dp corners. Tap it to retake or pick another; Back from that camera returns to the form. Without a photo, a short grey tile reads "+ Add photo".
- **Price.** Typed in the serif display style.
- **Card.** Name, Place, Time, and Note sit on one grey card, divided by hairlines.
- **Time.** Written with the date and weekday, as Search writes them: "Tue, Nov 3 · 18:03", or "Mon, Oct 27, 2025 · 19:05" outside the current year.
- **A saved meal's date.** A saved meal has no Time row. Its date heads the screen instead, between the top bar and the photo, in grey sans from the photo's left edge. Tapping it opens the date and time dialogs, and its action reads "Change time".
- **Name suggestions.** Recent names, as borderless pills, scroll into view as they appear.
- **Keyboard.** With the number pad up, the form scrolls so the price and Save stay above it.
- **Save.**
  - Enabled once there is a photo or a name, and a price.
  - Shown for a new meal, and for a saved meal once something changes.
  - The number pad's ✓ closes the keyboard, and also saves a new meal that is ready.
  - Save waits for a photo that is still being resized, about a second at most. If the photo can't be read, the meal stays open with "That photo couldn't be read. Try another." The same line shows whenever a photo fails.
- **Acts once.** Save, Back, and Delete do nothing after the first of them, and their buttons stop responding while the screen fades out.
- **Back.**
  - On a new meal, Back discards it and returns to the tabs, not the camera.
  - On a saved meal, Back saves the edits if they are valid. Otherwise the last saved version stays.
- **Delete.**
  - On a new meal, Delete discards it with no undo bar.
  - On a saved meal, Delete removes it with Undo.
- **Replacing a photo.** The old photo is kept until the new one is ready. If the new one can't be read, the old one comes back.

**From the gallery**

- Photos come from Android's Photo Picker, which needs no storage permission.
- **Date and time** come from the photo's EXIF `DateTimeOriginal`, falling back to `DateTime`. A photo with no date keeps the time Add was tapped.
- **Location** is asked for with the picker's location option, `"android.provider.extra.REQUEST_LOCATION_METADATA_ACCESS"`. Its constant is missing from the public API 37 jar, so the documented value is used. Apps targeting above Android 16 need no `ACCESS_MEDIA_LOCATION`.
- **Android 16.** The picker has no location option, so gallery meals there get a date and time but no location.
- **Ignored coordinates.** Exactly 0, 0, or out of range, count as no location. The emulator's picker withholds location that way.
- **Timing.** The gallery date and place apply once resizing finishes, about a second after picking.
- **The photo's location is where the meal was eaten.** A camera photo, or any saved meal, has no location. The phone's own location is never used, since it is wherever the meal is logged, not where it was eaten.

## Places

Places have no screen of their own. The Place row, between Name and Time, opens the place picker.

**The picker**

- **Layout.** A full-height sheet with a centered title and a close button. Swiping down also closes it. The search field is a grey pill, and it has focus when the sheet opens.
- **Order.** Places are listed by most recent visit. For a new meal with a gallery location, they are listed nearest first, with distances such as "1,240 km".
- **Typing.**
  - Names that start with the text come first, then names that contain it. Typing 林 lists 林家乾麵 before 小林拉麵.
  - Typing an existing name in full picks that place.
  - A new name is listed first, marked "New", and is created when the meal is saved, with the photo's coordinates if there are any.
- **"No place"** shows before anything is typed. An empty place saves the meal without one.
- **The ✓** picks the saved place typed in full, adds a new name, or keeps a pending new place typed again. With nothing typed, it only closes the keyboard.

**Prefill.** A new meal with a gallery location is given the one saved place within 150 m of the photo, if exactly one is that close. It stops once a place is chosen by hand. Camera and Skip meals are never prefilled.

**Rename, merge, and delete**

- **Rename** happens in the place's row. The name is selected, so typing replaces it. Done or the check saves it.
- **Merge.** Renaming to another place's name merges the two. The existing place survives with its name and coordinates, and takes the other's coordinates only if it has none. A change of capitalization alone never merges.
- **Delete.** A delete icon sits beside the check. The meals are kept, without a place; the open meal loses the place too.
- **Undo.** Merges and deletes show Undo inside the sheet. An undone delete brings the place back with its id and coordinates, on its meals, except any given another place meanwhile. If a place with its name was created meanwhile, the meals join that one instead, which takes the coordinates if it has none. The open meal follows each undo.

**"Meals here"**

- It sits at the top of the list, before anything is typed.
- It shows only for a saved meal at a saved place, and only while the meal's edits can be saved. A new meal never shows it, since leaving would discard the meal.
- It closes the Meal screen as Back does, then opens Search with that place.

A place removed before Save, such as an empty place whose undo bar closed, is left off the meal rather than failing the save.

## Undo

- **No confirmation.** Deleting a meal, deleting a place, and merging places ask for no confirmation.
- **The bar.** A grey bar reads "Deleted · Undo" or "Merged · Undo" for 4 seconds. It has 16 dp corners and can be swiped away sideways. A second edit closes the first bar.
- **Lifetime.** Undo lives in the app container, so the bar keeps running after the Meal screen closes.
- **What it restores.** A deleted meal's row goes at once. Undo puts the same row back with its id. The photo and any place left empty are removed when the bar closes. If the app is killed while a bar is open, the startup sweep finishes the job.
- **Undoing a merge** recreates the merged-away place under its original id and moves its meals back. If a place with the old name was created meanwhile, the meals move there instead.

## Journal

- **Weeks.** One row per week, newest first. Weeks without meals are left out. There is no page title; the first week's header heads the screen.
- **Week header.** The date range in the serif headline, with the week's total beside it in grey sans: "Oct 26 – Nov 1 · 2,340".
  - A week within one month names the month once: "Nov 2 – 8".
  - The year shows only for dates outside the current year: "Oct 20 – 26, 2025", or "Dec 29, 2025 – Jan 4" across New Year.
- **Filmstrip.** Each week's meals sit in a horizontal strip, Monday to Sunday by time eaten. Meals saved at the same moment keep their saved order. Swipe sideways within a week, and scroll down through weeks.
- **Tiles.**
  - Portrait, 88 × 117 dp (3:4), from the 400 px thumbnails. About four and a half fit across.
  - Tiles sit 2 dp apart, and only the strip's two ends are rounded, so a week reads as one band.
  - Each tile names its weekday in the lower left: white with a soft shadow over a photo, grey on a plain tile. A day with several meals has a tile for each.
  - No prices on tiles. Totals are visible but never the focus.
- **Meals without a photo** are plain grey tiles with the name, up to four lines.
  - A word too wide for the tile first shrinks by up to a fifth.
  - Past that, it breaks with a hyphen ("Extraordi-narily"), using English patterns first, then the phone's locales. A word with no hyphenation point can still break mid-word.
  - Chinese text breaks between characters.
- **A thumbnail that can't be read**, such as one not yet rebuilt after a restore, gives way to the full photo, then to the plain tile.
- **Scroll position.** The current week opens at its newest meal. Earlier weeks open at Monday. A meal that turns up later in a strip, logged meanwhile or brought back by Undo, scrolls into view.
- **Opening a meal.** A tap opens the Meal screen. A second tap while the first opens is ignored.
- **Top bar.** The backup reminder sits at the left (see Settings and backup). The settings icon, three sliders, sits at the right.
- **Empty state.** "No meals yet", with "Log your first meal".

## Spending

The Spending tab answers how much, where, and when, for a week, a month, or a year.

**Periods**

- **The switch.** Week, Month, and Year sit in a full-width segmented pill: a grey track with the selected segment filled in ink. It stays put while the periods swipe beneath it.
- **Swiping** moves to the previous or next period. It reaches back to the period of the earliest meal and forward to the current period.
- **Opening.** The tab opens on the current month at launch. It keeps its selection while switching tabs, but not across a cold start.
- **Switching kind** keeps the date in view: the new period is the one holding the shown period's last day, or today if that comes sooner.
- **"Today" is read again on resume.** If the current period was in view, the new current period comes into view. An older period stays put.
- **Period names.**
  - Week: "Nov 2 – 8", as in the Journal.
  - Month: "November", with the year only outside the current year: "November 2025".
  - Year: "2026".

**Layout, top to bottom**

1. The switch.
2. The period name in the serif headline, with a Share pill beside it (see Share Recap).
3. The total, in the serif display.
4. **The change line.** The change from the previous period as a whole percent with a true minus: "−12% vs Oct", "+4% vs last week", "0% vs 2025". It is grey, never red or green.
5. **Averages.** "312 a day · 156 a meal", in grey sans.
6. Four sections under small grey labels, on the plain background:
   - **Over time.** One bar per day for a week or month, and one per month for a year.
   - **By place.** The top five places by spend. Each has its total, its visits ("4 visits"), and a share bar. The rest, and meals without a place, sum to "Other", with its meals ("6 meals").
   - **By time of day.** Morning, Midday, Evening, and Late, each with its total and a share bar. All four always show.
   - **Priciest.** The three most expensive meals, as tiles.

**Rules**

- **Elapsed days.** For an unfinished period, the change compares the same number of whole days from the start of the previous period. A year counts days from Jan 1, so Feb 29 never shifts it.
  - A period's last day is still unfinished: on Nov 30, November is compared with Oct 1 – 30.
  - When the current period is longer than the previous one, the comparison stops at the previous period's last day.
- **No change line** when the previous period has no meals, or a total of 0.
- **Change-line labels.**
  - Week: "vs last week" for the current week, "vs previous week" for older ones.
  - Month: "vs Oct", or "vs Dec" in January, with no year.
  - Year: "vs 2025".
- **Rounding.**
  - The percent rounds halves away from zero. Anything that rounds to zero shows "0%" with no sign. A change too large for an Int is held at the largest one.
  - The daily average is the total divided by the days elapsed. The per-meal average divides by every meal, including meals at 0, which lower it. Both round to whole dollars, halves up.
- **Time-of-day bands.** Morning 05:00–10:59, midday 11:00–15:59, evening 16:00–21:59, late 22:00–04:59.
- **Visits** are the place's meals in the period. Counts are grouped: "1,043 meals".
- **Share bars** are measured against the period total, so all are on one scale. They are empty when the total is 0. There is no percent text.
- **Ties** in top places and priciest meals go to the most recent.
- **Meals dated in the future**, such as later today or from a camera with a wrong clock, count toward the period they fall in. Elapsed days still end at today. A meal in a period after today's shows in the Journal but not in Spending.

**Bars**

- Grey bars on a hairline baseline. There is no y-axis.
- Axis labels: "M T W T F S S" for a week, "1 8 15 22 29" for a month, and month initials for a year.
- **Tapping.** A tap shows the amount above the bar in small sans and turns the bar ink. A second tap on the same bar opens its meals in Search: that day in a week or month, that month in a year. Tapping another bar moves the amount there, and changing period clears it.
- **Empty and future days.** Days with nothing spent can be tapped and show "0", and a second tap opens nothing. Future days have no bar and no amount, so they never set the scale.
- **Targets.** Each bar is one tap target the full height of the chart, read aloud as "Nov 12, 340" with the action "Show amount", then "Show meals" once its amount shows. The targets cover the chart with no gaps.
- **Large fonts.** The chart measures its labels, so at large font sizes the amount and the axis stay inside it.

**Taps**

- A place row is a "Show meals" button. It opens Search with that place, and with the period as the dates, so the totals match. The dates end at today, or at the period's end once it is over, or at a later meal's day if one is dated after today. Other can't be tapped.
- A bar's second tap opens Search with its dates and no place. A month's bar ends its dates the same way as a place row, so the totals match there too.
- A Priciest tile opens its meal.

**Priciest tiles** use the Journal's tile, three across, with the price in the lower left where the Journal shows the weekday. Meals priced 0 are left out. The section is hidden when no meal costs anything.

**Empty states**

- With no meals at all, the tab shows only "No meals", with no switch.
- An empty period keeps the switch and swiping, and shows its name above "No meals". The Add button is already on the tab, so there is no button.

## Search

- **Layout.** The search field is a grey pill, with the place chip inside it before the text. Below it sit two pill filters, "Dates" and "★ Favorites". Then the total, "42 meals · 12,340", in sans. Then the results, newest first.
- **Before typing,** Search lists every meal, newest first, with the total.
- **Matching.**
  - Ignores case and full-width letters, cleaned as place names are.
  - Each typed word must appear in the name, the note, or the place, so "beef soup" finds "Beef noodle soup". The three are joined with a space, so no word matches across two of them.
  - Chinese matches by substring.
  - Filtering runs in memory, over every meal.
- **Dates.** "Dates" opens Material's date-range dialog, which can't pick future days. Once set, the pill reads "Oct 1 – Nov 4", written as the Journal writes weeks, with an × that clears it. Both days count in full. The two days are saved as one value, so they always change together.
- **Favorites** reads as a switch, on or off.
- **The place chip.**
  - With a chip, the text searches within that place. Its × clears it.
  - A chip whose place is merged away or deleted disappears and filters nothing out. It comes back if the change is undone.
- **Opening Search from elsewhere.** "Meals here" and Spending's place rows and bars open the Search tab with a fresh search: only that place, or Spending's dates, or both.
  - The new search replaces the one showing, and survives tab switches.
  - Back returns to Journal and drops it, as Back drops any tab's state.
- **Result rows.**
  - A 3:4 thumbnail, or a grey tile without a photo.
  - The name, with "Tue, Nov 3 · 林家乾麵" in grey below. The year shows only outside the current year.
  - The price on the right in tabular sans, and a small star for favorites.
  - A meal without a name shows its date and place as its only line.
  - A tap opens the meal. Place names in rows can't be tapped.
- **Touch targets.** The filter pills and the chip are drawn at 36 and 32 dp, but take taps over 48 dp.
- **Keyboard.** The field doesn't take focus when the tab opens. The results sit above the keyboard, so the last ones stay in view.
- **Empty states.** No matches: "No meals found". No meals at all: "Nothing to search yet", with "Log your first meal".

## Share Recap

A recap turns a period in Spending into one image. It leaves the phone only when Share is tapped, and holds only photos, names, dates, and totals.

- **The button.** A small outlined "Share" pill beside the period name, on Week, Month, and Year, for a period with meals.
- **The sheet.** The pill opens a small sheet, headed like the place picker's.
  - It has switches for Meals, Places, Total spent, and Change, each showing the value it would add. A figure with nothing to say, such as Change with nothing to compare, has no switch.
  - In a short window, such as a phone on its side, the rows scroll inside the card and Share stays in view.
- **The choice is remembered** in DataStore, across launches, from the moment a switch changes. All four are on until the first change. If a save fails, it is logged and the sheet keeps the choice.
- **Making it.**
  - The sheet's Share reads "Making…" while the image is drawn, then hands it to Android's share sheet, which shows it as its preview.
  - Closing the sheet while the image is drawn closes it at once and drops the image, so it never reaches the share sheet.
  - A recap that can't be drawn is logged and leaves the sheet open. One whose meals or saved choice can't be read is logged and closes it.
- **The collage** holds every meal in the period, in the order eaten.
  - The column count keeps tiles closest to the photos' 3:4. Rows fill evenly, fuller rows first, and a row with fewer tiles widens them, so there are never gaps.
  - Gaps narrow as tiles multiply.
  - Meals without a photo are the Journal's plain grey tile with the name.
- **The band** follows the app's theme, white in light and black in dark, in the app's greys.
  - The period is set in Fraunces' display cut, as the app writes it, and shrinks if it would run into the year at its right. The year reads "2025 – 26" for a week across New Year, and is left out for a Year recap.
  - "Food Journal" sits below the year.
- **Figures.** "24 meals · 12 places · 3,450". Places counts places with at least one meal, and is left out when there are none.
  - Below, in grey, the comparison is stated plainly: "120 less than last week", "120 more than Oct", "The same as 2025". It shows only when Spending would show its change line.
  - Figures switched off drop out of their line. If all three are off, the comparison moves up into its place. The period and year always show.
- **The image.** A lossless PNG, 3240 × 5760, three times Instagram Stories' size. Each photo is decoded at the pixels its tile covers, from the 400 px thumbnail when the tile is no larger.
- **Sharing.** The image goes through AndroidX's `FileProvider`, over the cache's `recaps/` folder only. Each recap replaces the last.

## Settings and backup

**The Settings screen.** The Journal's settings icon opens a full screen with "← Settings", the title in the serif headline. Three sections follow under grey labels, Appearance, Backup, and About. Rows sit on rounded grey cards, with a semibold title and a grey line below. A row that opens a page ends in a chevron.

**Appearance**

- A System, Light, and Dark switch, using the segmented pill Spending uses. System is the default.
- The choice goes to Android's per-app night mode, `UiModeManager.setApplicationNightMode`, which applies before the app draws, so launch never flashes the wrong theme.
- Android can't report it back, so DataStore keeps the choice for the switch. The app sets it again at launch in case the two drift apart.

**Backup rows**

- **Export.** Its grey line reads "Last backup Oct 2, 2026", always with the year, or "Never backed up".
  - It opens Android's save dialog with the name `food-journal-2026-11-04.zip`.
  - While it runs, it reads "Exporting…", and leaving the screen doesn't stop it.
- **Import.** Its grey line reads "Replaces the meals on this phone".
  - It picks ZIP files, including those labelled "x-zip-compressed" or plain binary.
  - It reads "Reading backup…" while it unpacks and checks the file, then "Restoring…".
- **Busy rows.** Export and Import each wait while the other is busy, and their rows turn grey.

**Before an import replaces anything**

- An open undo bar closes first.
- The confirmation reads "This backup has 412 meals, the last on Oct 30. It replaces the 99 meals on this phone.", with Cancel and Replace.
  - A backup without meals says "This backup has no meals.", and a phone without any, "This phone has no meals yet."
  - The last meal's year shows only outside the current year.
- **Refusals** are one line and OK, each ending "Nothing on this phone changed.":
  - not a Food Journal backup
  - from a newer version of the app
  - damaged
  - couldn't be read, such as when the phone runs out of room unpacking it

**After a restore**

- The Settings screen closes onto the Journal, and the tabs start afresh, since a kept search or period could hold a place id that now means another place. Back waits the moment a restore takes.
- The last backup date becomes the backup's own export date.
- Thumbnails are rebuilt in the background. Tiles show the full photo until then.

**Export failures**

- A missing photo file stops the export before anything is written: "A meal's photo is missing, so the backup wasn't made."
- A file that can't be written, such as on a full drive, is deleted rather than left half-written.
- A meal deleted during an export, whose undo bar closes before the export reaches its photo, fails that export. It can be run again.
- A failed export shows its reason where it was started, on the Journal or in Settings.

**The reminder**

- **When.** It shows while there are meals and the last export is over 30 days old, or, with no export ever, once the first meal is over 30 days old.
- **Where.** It sits at the left of the Journal's top bar: "Last backup Oct 2 · Back up", or "Not backed up yet · Back up". It is grey, with the action in ink.
- **A tap** opens the save dialog at once. The line reads "Exporting…" while it runs, and disappears when the export is done.

**About**

- **Version.** The version, from the app itself.
- **Open-source licenses.** A plain text page, wrapped to the screen, read from `res/raw/open_source_licenses.txt`. It covers what the release build ships:
  - Fraunces' Open Font License
  - the Apache License 2.0 for AndroidX, Coil, Dagger, Guava, Kotlin, kotlinx.coroutines, kotlinx.serialization, and Okio
  - the BSD license of Protocol Buffers, which DataStore carries inside it
- **Keeping it current.** The Fraunces license in `licenses/Fraunces-OFL.txt` is repeated in that file. When a library is added, list what the release build ships and update the file.

## Design

- **Color.**
  - Black, white, and greys only, with full light and dark themes. Material 3 dynamic color is off.
  - "Ink" means black in light and white in dark.
  - The window and splash backgrounds match the theme, so launch has no colored flash. In dark mode the window theme has a dark parent, a safeguard against force-dark.
  - There are no color preferences.
- **Type.**
  - The interface is set in the system sans-serif, Roboto, on the Material 3 type scale, with tabular figures so prices line up.
  - Fraunces, a soft serif variable font, sets only the display styles (big numbers) and headline styles (headings):
    - Display: weight 300, optical size 144, softness 100, wonky 0.
    - Headline: weight 500, optical size 32, softness 100, wonky 0.
    - Both are tracked 2% tight.
  - It appears in the Journal's week ranges, Spending's period name and total, the Meal screen's price, empty-state lines, the Settings title, and the recap.
  - Fraunces has only proportional figures, so it sets only standalone numbers. Numbers that line up stay in sans: lists, tiles, chart labels, the total beside a heading, and the search total.
  - Fraunces has no Chinese glyphs; Chinese text in a Fraunces style falls back to the system font.
- **Numbers.** English grouping on every device: "2,340". No currency prefix.
- **Icons.** Drawn in code: outlined, on a 24-unit grid, with one 1.5 stroke weight. No Material icon library.
- **App icon.** A filmstrip: three white portrait tiles on black, rounded only at the strip's ends, like a week in the Journal. The same drawing is the themed-icon layer.
- **Components**, in `ui/components/`:
  - **Pill buttons.** The main action is a solid ink pill, 56 dp, with a semibold label; disabled, it is plain mid grey. Secondary actions are outlined ink pills.
  - **Segmented pill.** Shared by Spending and Settings.
  - **Grouped card.** A soft grey card (`surfaceContainer`, 16 dp corners) with hairlines inset to the labels.
  - **Meal tile.** Shared by the Journal and Priciest, with its label and size as parameters.
  - **Sheet header.** A centered title and a close button, with no drag handle.
  - **Undo bar** and **empty state.**
- **Empty states.** One short serif line in ink, above an outlined pill when there is something to do. Inside scrolling content they stack from the top.
- **Charts.** Grey bars; the selected bar is ink. No color coding.
- **Quiet interface.** No red dots, badges, streaks, or colored buttons or text. Delete is not red.
- **Retro.** [Retro](https://retro.app/), a weekly photo journal, is the visual reference: the week is the unit, photos lead, and the past is easy to revisit. Its screenshots are kept locally in `UI inspo(retro-screenshots)/`, which Git ignores. Ideas are adapted to Food Journal's monochrome look, never copied: no Retro branding, art, or layouts.

## How it is built

**Stack.** Native Android in Kotlin and Jetpack Compose. All data is in a local Room database. There is no chart, paging, dependency-injection, or location library.

| Layer | Choice | Notes |
| --- | --- | --- |
| UI | Jetpack Compose, Material 3 | Custom monochrome scheme |
| Navigation | Navigation Compose | Type-safe routes |
| Architecture | Screen → ViewModel → DAO | Room and Flow do the rest |
| Dependencies | Manual app container | `AppContainer.kt` |
| Async | Coroutines and Flow | Live updates from the database |
| Database | Room 2.8 (SQLite) | Room 3 moves to `androidx.room3`; moving later is a dependency change, not a data migration |
| Lists | LazyColumn, LazyRow, HorizontalPager | About a thousand meals a year needs no paging |
| Charts | Compose Canvas | |
| Settings | DataStore (Preferences) | Theme, last backup day, recap figures |
| Camera | CameraX | In-app capture |
| Gallery | Android Photo Picker | No storage permission |
| Photo metadata | Framework `ExifInterface` | |
| Images | Coil 3 | Local files only, no network module |
| Backup | kotlinx.serialization (JSON) and ZIP | |
| File save and open | Storage Access Framework | |
| Recap sharing | AndroidX `FileProvider` | |
| Build | Gradle Kotlin DSL, version catalog | `gradle/libs.versions.toml` |

**Versions**

| Library | Version |
| --- | --- |
| Android Gradle Plugin | 9.5.0 |
| Kotlin | 2.4.21 |
| KSP | 2.3.13 |
| Compose BOM | 2026.09.00 (UI 1.12.1, Material 3 1.4.0) |
| Activity Compose | 1.13.0 |
| Lifecycle | 2.11.0 |
| Navigation Compose | 2.10.2 |
| Room | 2.8.5 |
| DataStore | 1.3.0 |
| CameraX | 1.6.2 |
| Coil 3 | 3.6.3 |
| kotlinx.serialization | 1.12.0 |
| JUnit 4 (tests only) | 4.13.2 |

- **Kotlin** is compiled by AGP 9's built-in support. The Kotlin Gradle plugin is declared but not applied, which pins the compiler version.
- **SDK levels.** compileSdk and targetSdk are 37; minSdk is 36, so the app runs on Android 16 and later. Bytecode targets Java 17.

**Code layout**, under `app/src/main/java/com/stanley/foodjournal/`:

- `data/`: Room entities and DAOs.
- `logic/`: pure rules with JVM tests: `Spending.kt`, `Search.kt`, `Recap.kt`, `Weeks.kt`, `Places.kt`, `PlaceNames.kt`, `PlacePicker.kt`, `MealForm.kt`, `TileNames.kt`, `PhotoSizing.kt`, and `Backup.kt`.
- `photos/`, `backup/`, `recap/`, `edits/`, `settings/`: photo files, backup and restore, recap drawing and sharing, undo, and DataStore.
- `ui/`: one folder per screen, plus `components/`, `theme/`, `icons/`, `navigation/`, and `preview/`.

**Photos**

- **Storage.** One class, `PhotoStore`, owns every photo file, in three private folders:
  - `photos/`: 1600 px on the long edge, JPEG quality 85, no metadata
  - `thumbs/`: 400 px tiles
  - `pending/`: taken or picked, not yet saved
- **Processing.** `ImageDecoder` applies the EXIF rotation. Processing catches every error, including running out of memory, and removes half-written files.
- **Names.** Every photo gets a random file name. A replacement is a new file under a new name, so the image loader never shows a stale copy.
- **Pending photos.** A new photo moves from `pending/` to `photos/` when its meal is saved. Discarding a new meal deletes it, as does a capture that lands after the screen closed, or a photo replaced while it was still being resized.
- **The startup sweep** removes files in `photos/` and `thumbs/` that no meal uses. It leaves recent pending files alone, so a Meal screen restored after Android closed the app keeps its photo. Pending files older than seven days are removed.

**The Meal screen's state.** One ViewModel holds the photo, its metadata, and the draft. The camera is a stage of the Meal screen, not a separate destination. The draft is Compose snapshot state, copied to saved state on each change, so fast typing never drops a keystroke. Save reads the latest draft once the photo is ready.

**Background work.** The app-wide scope logs a failure instead of crashing, and other work carries on.

**Spending's state.** The ViewModel groups meals by period once, and builds every page for the chosen kind off the main thread. The pager is keyed by period, so a backfilled older meal doesn't shift the period in view. The ViewModel holds the selection; the pager's page is not saved on its own.

**Search's route.** `SearchRoute` carries an optional place and first and last days. The Meal screen hands its place to the tabs, which open Search once they show again. This replaces the tab's saved search.

**Places in SQL.** Empty places are found with `NOT EXISTS (SELECT 1 FROM meal WHERE meal.placeId = place.id)`. `NOT IN` matches nothing once any meal has no place.

**Backup file.** One ZIP:

- `backup.json`: a format version (currently 1), the time of the export, the places, and the meals, with their ids.
- `photos/`: the stored 1600 px photos, uncompressed, since JPEGs don't shrink further. Camera originals are never kept, and thumbnails are left out.

**Import checks**, before anything changes:

- The format version is read before the records, so a newer backup is called newer even if its records can't be read.
- Every meal has a photo or a name, a price from 0 to 999,999, and a time that reads.
- Ids are unique, and every place a meal names is in the file.
- Place names are present and unique, ignoring case.
- Every photo a meal names is in the file.
- A `backup.json` over 64 MB is taken as damaged.
- **Entries.** Entries outside `backup.json` and `photos/<name>` are skipped. A name with a folder in it is refused. Android's ZIP reader refuses any entry name holding "..", and with it the whole file, which is then reported as damaged.

**Replacing the data**

- **The swap.** The backup unpacks into a folder beside the app's own. The photo folders are swapped, then one database transaction replaces every place and meal. If the transaction fails, the folders swap back.
- **No edit runs during a restore.** It closes an open undo bar and holds the undo lock until the meals are replaced.
- **Thumbnails.** Photos the phone already had keep their thumbnails. The rest are rebuilt in the background.
- **An import cut short** by the app closing leaves nothing missing. At the next launch, set-aside photos return beside the new ones, the sweep removes those no meal uses, and a half-unpacked import is deleted.

**Privacy and permissions**

- The only permission is the camera. The manifest strips INTERNET in case a library adds it; Gradle prints an expected merge warning. AndroidX's internal `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` is the only merged permission, and it is private to the app.
- `allowBackup` is false, and data extraction rules exclude everything from both cloud backup and device-to-device transfer. The exported ZIP is the only backup, so the database and photos never return out of step.
- Stored photos carry no metadata. A recap leaves the phone only through the share sheet.

## Development

**Toolchain**

| Tool | Version |
| --- | --- |
| Android Studio | 2026.2, with JetBrains Runtime 25.0.4 |
| SDK Platform | Android 17, API 37 |
| Build Tools | 37.0.0 |
| Platform Tools | 37.0.1 |
| Gradle | 9.8.2, through the wrapper |
| Test phone | Pixel 8a, Android 17, 1080 × 2400 at 420 dpi |

There is no system JDK; Studio uses its own. For command-line builds on a Mac, add this to `~/.zshrc`:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$PATH:$ANDROID_HOME/platform-tools"
```

Android Studio's `.idea/` files are not committed; Studio regenerates them from Gradle.

**Commands**

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
ANDROID_SERIAL=emulator-5560 ./gradlew :app:connectedDebugAndroidTest
```

**Device tests run only on an emulator.** They install and uninstall the app, which would delete real meals. `connected…AndroidTest` tasks refuse to run unless `ANDROID_SERIAL` is exactly one emulator serial, such as `emulator-5560`. A comma-separated list is refused, since it could reach the phone.

**Signing**

- One personal key signs both debug and release builds, so either installs over the other without losing data.
- Gradle reads it from four properties in `~/.gradle/gradle.properties`, outside the repo: `foodJournal.signing.storeFile`, `.storePassword`, `.keyAlias`, and `.keyPassword`.
- Without them, release builds come out unsigned and can't be installed. A build signed with another key can never replace the installed app.
- Release builds are shrunk and optimized with R8. Daily use and timing the 15-second target happen on release builds.
- Losing the key means the installed app can't be updated without uninstalling it, which deletes every meal. Keep a copy of the key and its password somewhere safe besides the Mac.

**Versioning.** The version name is set in `app/build.gradle.kts` and shown in About. The version code stays 1, so each build installs over the last with its data. The release APK is in `release/`.

**Tests**

- **JVM tests** cover everything in `logic/`, the backup format, the converters, `PhotoStore`, and the meal draft.
- **Device tests** use AndroidX Test and Compose's UI test library. They cover:
  - the database
  - `UndoableEdits`
  - photo processing on real JPEG files
  - backup and restore
  - recap drawing
  - DataStore
  - each screen's ViewModel
  - the recap sheet
- **Fakes.** The Meal and Spending ViewModels take photo processing and recap drawing through small interfaces, `PhotoProcessing` and `RecapDrawing`, so tests drive them with fakes.
- **Test-only code.** Test libraries never ship. Only an empty activity for hosting UI tests is in debug builds.
- **Previews.** `ui/preview/` holds Compose previews of the theme and type, in light and dark.

## Known gaps

**Not yet tested**

- Photo Picker location on a real phone. On the emulator it arrives as 0, 0, so gallery prefill is tested only through the database and unit tests.
- HEIC date and location.
- Photo orientation with auto-rotate off.
- Anything on Android 16, on a device or an emulator.

**Known issues**

- The date pickers on the Meal screen and in Search start weeks on Sunday, following the locale. The app's weeks start on Monday.
- One stray old date, such as a camera's default of 1980, adds thousands of empty periods to swipe through in Spending.
- The backup reminder reads today's date only when the meals or the backup day change. If the app stays open across midnight, a reminder due that day appears once something changes.
