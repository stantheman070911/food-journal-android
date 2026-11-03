# Food Journal — Product Document

Nov 3, 2026 · @Stanley

## Overview

Food Journal is a personal Android app that records what I eat, where, and what I spend. It is a diary, not a diet tracker: no calories, no goals, no scores.

**Goal:** a personal archive of meals that is quick to add to and pleasant to look back on.

**Principles**

- Less is more. Every screen, field, and dependency must earn its place.
- Design is not compromised.
- Logging a meal takes under 15 seconds.
- Only a photo or a name, and the price, are required.
- Photos lead. Numbers support them.
- Data stays on the phone. No account, no sign-in.
- One currency, NTD, stored as whole dollars.

## Scope

Version 1 is logging, the weekly journal, spending analysis, and search.

- Log a meal: photo, name, price, favorite, note, place
- Backfill from the gallery: date, time, and location from photo metadata
- Weekly journal with filmstrip rows
- Spending analysis by week, month, and year
- Search with date and favorite filters
- Backup and restore as one ZIP

**Out of scope:** calories, nutrition, diet goals, social sharing, multiple currencies, maps, cloud sync.

Nothing is planned beyond version 1. Further features are decided after four weeks of use.

## Data model

Two records: Meal and Place. A meal belongs to at most one place.

**Meal**

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| id | Long | Yes | Auto |
| eatenAt | Timestamp | Yes | Set to now; editable |
| photo | File path | Photo or name | One per meal |
| name | Text | Photo or name |  |
| price | Int (NTD) | Yes | 0 allowed |
| isFavorite | Boolean | No | Default false |
| note | Text | No |  |
| placeId | Long | No | Link to Place |

**Place**

| Field | Type | Notes |
| --- | --- | --- |
| id | Long | Auto |
| name | Text | Required |
| latitude, longitude | Double | Where the place was first saved: phone GPS, or the photo's location for a backfilled meal; empty without location |

Visits, total spend, last visit, and time of day are computed from meals, not stored.

A meal's day is the calendar date of eatenAt, so a 01:00 snack counts toward the day it was eaten. Weeks run Monday to Sunday.

## Screens

Three tabs in an icon-only bottom bar: Journal, Spending, Search. A floating Add button sits on every tab. A settings icon at the top of Journal holds backup and restore.

| Screen | Purpose | Key elements |
| --- | --- | --- |
| Journal | Browse meals by week | One row per week, newest first; horizontal strip of photo tiles; header with date range and total; tap a tile to open the meal |
| Spending | Understand food costs | See Spending analysis |
| Search | Find past meals | Text search on name, note, and place; filters for date range and favorites; place chip; results show their total |
| Meal | Add, read, and edit one meal | One screen for all three; favorite and delete at the top |

**Places** have no screen of their own. Tapping a place name opens Search with the place as a chip in the search field. Tapping × on the chip clears it. Places are renamed in the place picker. Renaming a place to an existing name merges the two. A place with no meals left is removed.

**Favorites** are a star on any meal.

**Undo.** Delete and merge ask for no confirmation. A grey bar, such as "Deleted · Undo", stays for a few seconds.

## Spending analysis

The Spending tab answers three questions: how much, where, and when.

**Period.** A Week, Month, Year switch at the top. Swipe sideways to move to the previous or next period. The tab opens on the current month.

**Layout, top to bottom**

1. **Total** for the period, large. Below it, the change from the previous period as a signed percent, such as "−12% vs Oct". Grey, never red or green.
2. **Averages.** Daily average and average per meal, on one line.
3. **Over time.** A bar chart: one bar per day for a week or month, one per month for a year. Tap a bar to see its amount.
4. **By place.** The top five places by spend, each with its total, visit count, and a thin share bar. The rest, and meals without a place, sum to "Other". Tap a place to open its meals in Search.
5. **By time of day.** Morning, midday, evening, and late, each with its total and share bar.
6. **Priciest.** The three most expensive meals of the period, as photo tiles.

**Rules**

- Everything is computed from meals. Nothing extra is stored.
- For an unfinished period, the change compares the same number of days in the previous period.
- If the previous period has no meals, the change line is omitted.
- The daily average divides the total by the days elapsed in the period.
- Meals logged at 0 count as meals and lower the per-meal average.
- Time-of-day bands: morning 05:00–10:59, midday 11:00–15:59, evening 16:00–21:59, late 22:00–04:59.
- A period with no meals shows one line: "No meals".
- No budgets, targets, or warnings. Numbers are shown, never judged.

## Quick logging flow

Three taps and a price: Add, shutter, price, Save. The target is under 15 seconds.

1. Tap Add. The camera opens, with a gallery button beside the shutter. Skip is allowed.
2. The Meal screen opens with the photo on top. Time is filled. Place is filled when exactly one saved place is within 150 m. Otherwise it stays empty.
3. The price field has focus and the number pad is open. Type the price.
4. Save.

Name, note, and favorite sit below the price, for now or later.

**Rules**

- Save is enabled once a photo or name, and a price, exist.
- Recent names and place names are offered while typing.
- The place picker lists saved places nearest first.
- Typing a new place name creates the place at the current location.
- An empty place field saves the meal without a place.
- Location permission is asked on first use only. Without it, places are picked from the saved list.

**Backfill from the gallery**

Gallery photos fill date and time from their metadata. The Photo Picker removes location by default; its newer location option lets me share it, once per app ([source](https://androidauthority.com/new-android-photo-picker-api-coming-3701717)). Backfill uses only the photo's location, for both the place match and any new place. Without it, the place is picked by hand.

## Design direction

The interface is monochrome and quiet, so the food photos are the only color. Structure follows [Retro](https://retro.app/): the week is the unit, photos lead, and the past is easy to revisit.

- **Color.** Black, white, and greys only. Material 3 dynamic color is off. Full light and dark themes.
- **Type.** One sans-serif family. Size and weight set the hierarchy. Prices use tabular figures.
- **Week as the unit.** One row per week, Monday to Sunday, newest first. Header: "Oct 26 – Nov 1 · 2,340".
- **Filmstrip rows.** Each week's meals sit in a horizontal strip of tiles. Swipe sideways within a week; scroll down through weeks.
- **Meals without a photo.** A plain grey tile with the name set inside.
- **Numbers.** One currency, so prices show as "280" with no prefix. Totals are visible but never the focus of the Journal.
- **Charts.** Grey bars. The selected bar is black, or white in the dark theme. No color coding.
- **Icons.** Outlined, one stroke weight. The bottom bar shows icons only.
- **Quiet interface.** Photos fill the screen. No streaks, badges, or warnings.
- **Empty states.** One short line and one button, such as "Log your first meal".
- **Motion.** Light transitions only. Nothing that slows logging.
- **Language.** English only.

## Tech stack

Native Android in Kotlin and Jetpack Compose, with all data in a local Room database and no chart, paging, or dependency-injection library.

| Layer | Choice | Why |
| --- | --- | --- |
| Language | Kotlin | Android's standard language |
| UI | Jetpack Compose, Material 3 | Custom monochrome scheme; dynamic color off |
| Navigation | Navigation Compose | Three tabs and the Meal screen |
| Architecture | Screen → ViewModel → DAO | Few layers; Room and Flow do the rest |
| Dependencies | Manual app container | Too few objects to justify Hilt |
| Async | Kotlin Coroutines and Flow | Live updates from the database |
| Database | Room (SQLite) | Local, typed queries, migrations |
| Lists | LazyColumn and LazyRow | About a thousand meals a year needs no paging |
| Charts | Compose Canvas | A few bars; full control of the look |
| Settings | DataStore | Last backup date |
| Camera | CameraX | In-app capture with no confirm step |
| Gallery | Android Photo Picker | No storage permission needed |
| Images | Coil 3 | Thumbnails from local files; no network module |
| Location | Fused Location Provider | Fast, battery-aware GPS |
| Backup format | kotlinx.serialization (JSON) + ZIP | Data and photos in one versioned file |
| File save | Storage Access Framework | I pick where the backup goes |
| Build | Gradle (Kotlin DSL), version catalog | Standard project setup |

**Targets:** minimum SDK set to my phone's Android version; target the latest stable API.

**Photos:** one per meal, stored in the app's private folder, resized to 1600 px on the long edge, JPEG quality 85. A 400 px thumbnail is kept for tiles.

## Storage, privacy, backup

All data lives on the phone. Nothing leaves it unless I export a backup.

- **No account.** The app works fully offline. It declares no INTERNET permission, so it cannot make network requests.
- **Backup.** Settings → Export creates one ZIP: a JSON file of all records with a format version, plus the original photos. Photos are stored without compression, since JPEGs do not shrink further. Thumbnails are left out and rebuilt on restore. I choose where it goes.
- **Restore.** Settings → Import reads that ZIP. After a confirmation, it unpacks to a temporary folder, is checked, and replaces current data in one step. A failed import leaves current data untouched.
- **Reminder.** A quiet prompt to back up if the last export is older than 30 days.
- **Android Auto Backup** is off for the whole app, so the database and photos never return out of step. My ZIP is the backup.
- **Delete.** Deleting a meal removes its photo once the undo bar closes. A place with no meals left is removed.

## Phases

Each phase implements the named sections in full.

**Phase 1 — Foundation.** Project setup, version catalog, light and dark themes, Room schema for Meal and Place with schema export, navigation for the three tabs and the Meal screen, floating Add button, manual app container. Manifest per Storage, privacy, backup: no INTERNET permission, Auto Backup off. Done when the app opens to the three tabs with their empty states.

**Phase 2 — Meal and logging.** Quick logging flow, the Meal screen, Places, Favorites, Undo, and Photos. This includes the place picker, rename and merge, and gallery backfill with the Photo Picker location option. Flag anything that threatens the 15-second target.

**Phase 3 — Journal.** The Journal screen, with weeks, filmstrip rows, and photo-less tiles as in Design direction. Add the settings icon; its contents come in Phase 6.

**Phase 4 — Spending.** Spending analysis, with charts drawn in Compose Canvas.

**Phase 5 — Search.** The Search screen, place chip, and results total. Place names in Meal and Spending open Search.

**Phase 6 — Backup and restore.** Storage, privacy, backup: export, import, and the 30-day reminder.

## Decisions and next steps

**Decisions**

- Interface: English only.
- Map: none in version 1. GPS is stored, so a map can come later.
- Rating and mood: neither. A favorite star replaces both.
- Spending: its own tab, for analysis. Search totals cover one-off questions.

