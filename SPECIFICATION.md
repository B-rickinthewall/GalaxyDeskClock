# Galaxy Desk Clock — reference specification

## Purpose

Turn a Samsung Galaxy S10 on a magnetic charging stand into a private, dedicated desk clock.

## Privacy and connectivity

- Fully usable offline
- No internet permission
- No advertisements
- No analytics or tracking
- No user account
- No weather
- No network APIs
- Time comes from the phone's internal Android system clock
- World clocks use Android's installed time-zone database

## Main display

- Landscape-first, full-screen immersive clock
- Large local time
- Optional date, weekday and seconds
- 12-hour or 24-hour format
- Adjustable text colour, size, brightness and background darkness
- Long press opens settings

## Background photographs

- User selects photographs from Android's system file picker
- Images are copied into private app storage
- Original photographs are not modified
- Photographs can be added, previewed, removed individually or cleared
- New photograph chosen every hour
- Recent photographs should not immediately repeat
- Crop position changes with the hourly scene

## World clocks

- Two to four optional secondary clocks
- Common city list plus custom IANA time-zone ID
- Custom display label
- Digital or analogue style
- UTC offset
- Previous-day / next-day indicator
- Automatic daylight-saving changes
- World-clock positions change with the hourly layout

## Hourly layouts

Six built-in presets are available. At each hour the app changes the major clock position, world-clock arrangement, photograph and crop. Enabled presets can be selected in settings. Immediate repeats are prevented.

## AMOLED protection

- Major layout and background change every hour
- Whole-interface 5–15 pixel micro-shift every two to five minutes
- Conservative daytime brightness
- Lower scheduled nighttime brightness
- Muted text colours instead of maximum-brightness white
- True-black hourly rest period
- Display off when charging stops

## Hourly Display Rest

Default sequence:

- `XX:58:00`: normal scene switches to true black
- `XX:58:00`–`XX:59:55`: only a very small, dim local clock remains
- Small clock changes position every 10–20 seconds
- No date, seconds, world clocks, frames, labels or photograph
- `XX:59:55`: next background and layout begin fading in
- `XX+1:00:00`: new scene is fully visible

The timing is recalculated from the system clock, avoiding timer drift after restarts, time changes or charging interruptions.

## Charging behavior

When charging starts:

- Detect external power
- Wake the screen where Android/Samsung permits it
- Reopen or resume the clock
- Show over the lock screen
- Apply current brightness
- Resume the correct phase for the current time
- Keep the display awake

When charging stops:

- Immediately show true black
- Remove the keep-awake flag
- In standard mode, allow Android's normal timeout to turn off the display
- In immediate mode, use optional Device Administrator permission to lock the display immediately

## Startup and recovery

- Optional startup after reboot while charging
- Recalculate correct scene after app restart, time-zone change, daylight-saving transition, clock change, screen rotation or charging interruption

## Settings backup

- Export configuration to local JSON
- Import configuration from local JSON
- Background photographs remain private app data and are not included in the JSON settings file

## Delivery target

- Signed APK for direct installation
- Full Android Studio source project
- Installation and Samsung setup guide
- Stable signing key for future in-place updates
