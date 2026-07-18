# Galaxy Desk Clock

Private, offline desk-clock app for a Samsung Galaxy S10 on a charging stand.

## Current development version

Version `0.1.0` is the first working source implementation. It already contains:

- Full-screen landscape desk clock using the phone's system time and time zone
- 12/24-hour formats, optional date, weekday and seconds
- Import, store, preview and remove personal background photographs
- Hourly background changes with non-repeating image selection
- Six hourly layout presets with non-repeating selection
- Two to four configurable world clocks
- Digital and analogue world-clock styles
- Automatic time-zone and daylight-saving handling through Android
- UTC-offset and previous/next-day indicators
- True-black Hourly Display Rest beginning at `XX:58:00`
- Small moving local clock during Display Rest
- Five-second fade-in of the next background and layout before the new hour
- Whole-interface pixel shifting every two, three or five minutes
- Day and night brightness limits
- Charging detection
- Best-effort screen wake and app reopening when charging starts
- Black screen when charging stops
- Optional Device Administrator permission for immediate lock on disconnect
- Optional startup after reboot while charging
- Local JSON settings export/import
- No internet permission, ads, analytics, accounts, weather or tracking

## Important device limitation

Android and Samsung can restrict an app from opening itself from the background. The charging receiver, wake lock, lock-screen flags and reboot receiver are implemented, but automatic opening must be tested on the actual S10. Samsung battery-optimization exclusion or a Bixby Routine may still be required.

## Opening the project

1. Install the current stable Android Studio on a Mac or Windows computer.
2. Open the `GalaxyDeskClock` folder.
3. Allow Android Studio to install Android SDK 35 and synchronize Gradle.
4. Connect the Galaxy S10 with USB debugging enabled, or build an APK with **Build > Build APK(s)**.

The project uses:

- Kotlin
- Android framework views and Canvas rendering
- Minimum Android API 28 (Android 9)
- Target/compile API 35
- No third-party runtime libraries

## GitHub build

The included GitHub Actions workflow can create an installable debug APK without a local Android development setup:

1. Put this project in a GitHub repository.
2. Open the repository's **Actions** tab.
3. Run **Build Android APK**.
4. Download the `GalaxyDeskClock-debug-apk` artifact.

## Long-press control

Long-press anywhere on the clock display to open settings.

## Remaining work before a polished 1.0 release

- Test and tune all layouts on the physical Galaxy S10 screen and camera cutout
- Verify Samsung charging-start foreground behavior on the installed One UI version
- Add optional slow background pan/zoom and blur
- Add fully freeform drag-and-drop custom layouts
- Add optional PIN protection for settings
- Add polished first-run permission/setup guidance
- Produce and sign the final release APK
