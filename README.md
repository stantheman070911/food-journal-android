# Food Journal

A personal Android app for recording what I eat, where, and what I spend. It is a diary, not a diet tracker: no calories, no goals, no scores.

The goal is a personal archive of meals that is quick to add to and pleasant to look back on.

> **Status:** Phases 1 to 3 of 6 (foundation; meal and logging; journal) are built. The product is specified in [Food Journal — Product Document](<Food Journal — Product Document.md>); open points and decisions are in [Development Notes](DECISIONS.md).

## Principles

- Less is more. Every screen, field, and dependency must earn its place.
- Logging a meal takes under 15 seconds: Add, shutter, price, Save.
- Only a photo or a name, and the price, are required.
- Photos lead. Numbers support them.
- Data stays on the phone. No account, no sign-in, no internet permission.
- One currency, NTD, stored as whole dollars.

## Version 1

- **Log a meal:** photo, name, price, favorite, note, and place. The place fills itself when exactly one saved place is within 150 m.
- **Backfill from the gallery:** date, time, and location come from the photo's metadata.
- **Journal:** one row per week (Monday to Sunday), newest first, with a horizontal filmstrip of photo tiles.
- **Spending:** totals, averages, a bar chart, top places, time of day, and priciest meals by week, month, or year. Numbers are shown, never judged.
- **Search:** text search on name, note, and place, with date-range and favorite filters.
- **Backup and restore:** one ZIP holding a versioned JSON file of all records plus the original photos.

Out of scope: calories, nutrition, diet goals, social sharing, multiple currencies, maps, and cloud sync. Further features are decided after four weeks of use.

## Design

Monochrome and quiet, so the food photos are the only color. Black, white, and greys only, in full light and dark themes, with Material 3 dynamic color off. One sans-serif family, tabular figures for prices, outlined icons, and an icon-only bottom bar with three tabs: Journal, Spending, Search.

## Tech stack

| Layer | Choice |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 (custom monochrome scheme) |
| Navigation | Navigation Compose |
| Architecture | Screen → ViewModel → DAO, with a manual app container (no Hilt) |
| Async | Kotlin Coroutines and Flow |
| Database | Room (SQLite) |
| Charts | Compose Canvas |
| Settings | DataStore |
| Camera / gallery | CameraX, Android Photo Picker |
| Images | Coil 3 |
| Location | Fused Location Provider |
| Backup | kotlinx.serialization (JSON) + ZIP, saved via Storage Access Framework |
| Build | Gradle (Kotlin DSL), version catalog |

Photos are stored in the app's private folder, resized to 1600 px on the long edge at JPEG quality 85, with a 400 px thumbnail for tiles. Android Auto Backup is off; the exported ZIP is the backup.

## Roadmap

1. Mock up Journal, Meal, Spending, and Search in monochrome.
2. Set up the project: Gradle, Room schema, navigation, app container.
3. Build Meal and Journal first, then Spending, then Search.
4. Add backup and restore.
5. Use it for four weeks, then decide what comes next.

Device tests run only on an emulator, so they can never uninstall the app from a phone: `ANDROID_SERIAL=emulator-5560 ./gradlew :app:connectedDebugAndroidTest`.

See the [product document](<Food Journal — Product Document.md>) for the full data model, screen details, and spending rules.
