# Food Journal

**English** · [繁體中文](README.zh-TW.md)

A quiet Android app for remembering what you ate, where you ate it, and what it cost. Take a photo, type the price, and save. Logging a meal is meant to take under 15 seconds, and looking back is one week at a time.

It's a diary, not a diet tracker. There are no calories, goals, streaks, or scores, and nothing leaves your phone unless you export a backup or share a recap yourself.

> **Version 1.01.01** · Android 16 or later · English interface · Prices in New Taiwan dollars (NTD)

## Features

### Log a meal in three taps

- Tap Add and the camera opens. Take the photo, type the price, and tap Save.
- A meal needs only a photo or a name, plus its price. A name, note, place, and favorite star are there whenever you want them.
- Names you've used before and your saved places come up as you type.
- Save where you ate: a photo taken in the app keeps the phone's location, with a switch on the form to leave it off. A meal logged later keeps the phone's location only if you turn the switch on. The district shows beside it.

### Backfill from the gallery

- Forgot to log lunch? Pick the photo from your gallery, and the meal takes the date and time the photo was taken.
- On Android 17, if the photo picker shares the photo's location, and exactly one of your saved places is within 150 m of it, the meal is placed there. On Android 16, you pick the place yourself.

### Journal

- One row per week, Monday to Sunday, newest first, with the week's total.
- Each week is a filmstrip of photo tiles. Swipe sideways through a week, and scroll down through earlier ones.
- A meal without a photo shows as a plain tile with its name.
- Open a meal, then tap or swipe its photo to step to the next or previous one, across weeks.

### Spending

- See a week, a month, or a year, and swipe back through earlier ones.
- The total, the change from the period before, and the average per day and per meal.
- A bar chart, your top five places, spending by time of day (morning, midday, evening, and late), and the three priciest meals.
- Numbers are shown, never judged: no budgets, targets, or warnings.

### Search

- Search meal names, notes, and places, and filter by dates or favorites.
- Results show how many meals were found and what they cost in total.
- Tap a place in Spending to see its meals in that period, or tap a bar twice to see that day's or month's meals. Tap Meals here in a meal's place picker to see every meal eaten there.

### Places

- Rename a place, merge two by giving one the other's name, or delete one.
- Deleting or merging a place, like deleting a meal, shows an Undo bar for a few seconds.
- A place with a location in Taiwan shows its county and district, found on the phone from a boundary file bundled with the app.

### Backup and restore

- Export everything to one ZIP file, saved wherever you choose: your records as JSON, plus your photos.
- Importing checks the whole file before anything changes, asks before replacing your meals, then replaces them in one step. If an import fails, your meals stay as they were.
- A quiet reminder appears on the Journal once your last backup is over 30 days old.

### Share a recap

- Tap Share in Spending to turn the week, month, or year in view into one image: a collage of every meal in it, with the period and the year.
- Choose what else it shows: the number of meals, the number of places, the total, and how it compares with the period before. Each has its own switch, and your choice is remembered.
- The image is a high-resolution PNG, sized for Instagram Stories, and goes to Android's share sheet, so you can post it, send it to a friend, or save it.
- Meals without a photo appear as plain tiles with their name, as in the Journal.

### Design

- Black, white, and greys only, so the food photos are the only color.
- Light and dark themes, or follow the system setting.

## Privacy

- No account and no sign-in. The app works fully offline.
- The app has no internet permission, so it can't send anything anywhere. It contains no analytics or ads.
- It asks for the camera, and once for your location, the first time you add a meal. Gallery photos come through Android's photo picker, which needs no storage permission.
- A meal's location is saved only while its switch is on, and only in the app's own database, never in the photo. With approximate location, the phone's location isn't used. Without location permission, no meal keeps a location.
- Photos are stored resized, without their original metadata.
- Android's cloud backup and device-to-device transfer are turned off for this app. The backup you export is the only copy of your meals off the phone.

> [!WARNING]
> **Back up before you uninstall.** Uninstalling the app deletes every meal and photo in it. Export a backup first and keep it somewhere safe.

## Install

1. On your Android phone, download [food-journal-1.01.01.apk](release/food-journal-1.01.01.apk).
2. Open it, and allow installs from that source when Android asks.

The app needs Android 16 or later. It was built and tested on a Pixel 8a with Android 17, and hasn't been tested on Android 16 yet. A newer version signed with the same key installs over the old one and keeps your meals.

## Not included

The app has no calories, nutrition, diet goals, social feeds, multiple currencies, maps, or cloud sync. It is kept small on purpose.

## For developers

### Building

You need Android Studio 2026.2 or later, with the Android 17 (API 37) SDK.

```bash
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # JVM tests
./gradlew :app:lintDebug
```

On the command line, set `JAVA_HOME` to the JDK bundled with Android Studio. On a Mac, that is `/Applications/Android Studio.app/Contents/jbr/Contents/Home`.

Device tests install the app, then uninstall it, which would delete the meals on a real phone. They refuse to run unless `ANDROID_SERIAL` names an emulator:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

Release builds are shrunk with R8, and they are signed only when four properties are set in `~/.gradle/gradle.properties`: `foodJournal.signing.storeFile`, `.storePassword`, `.keyAlias`, and `.keyPassword`. Without them, release builds come out unsigned.

> [!CAUTION]
> A build signed with a different key can't update the published APK. Android would make you uninstall first, which deletes your meals. Export a backup before you switch.

### Built with

Kotlin, Jetpack Compose with Material 3, Navigation Compose, Room, DataStore, CameraX, Coil, and kotlinx.serialization. The charts are drawn with Compose Canvas, and big numbers and headings are set in [Fraunces](https://fonts.google.com/specimen/Fraunces).

### Documentation

- [Product document](PRODUCT.md): what the app does, how it looks, and how it is built.

## License

Food Journal is licensed under the [Apache License 2.0](LICENSE). The bundled Fraunces font has its own license, the [SIL Open Font License 1.1](licenses/Fraunces-OFL.txt).
