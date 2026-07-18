# Installation guide

## Once an APK has been built

1. Copy `GalaxyDeskClock.apk` to the Galaxy S10 using USB, Google Drive or another transfer method.
2. On the phone, open **My Files > Downloads** and tap the APK.
3. When prompted, allow **Install unknown apps** for My Files or the browser used to download it.
4. Install and open **Galaxy Desk Clock**.
5. Long-press the display to open settings.
6. Add background photographs and select world clocks.
7. Open the charging section and optionally enable immediate screen-off permission.
8. Exclude Galaxy Desk Clock from Samsung battery optimization.

## Samsung settings likely required

Exact menu names vary by One UI version:

- Settings > Apps > Galaxy Desk Clock > Battery > Unrestricted
- Settings > Battery and device care > Battery > Background usage limits > Never sleeping apps
- Add Galaxy Desk Clock to **Never sleeping apps**
- Permit display over the lock screen if Samsung presents that option

If charging starts but Samsung does not automatically reopen the app, create a Bixby Routine:

- **If:** Charging status = Charging
- **Then:** Open an app = Galaxy Desk Clock

## Device Administrator permission

This is optional. It is used only for locking the screen immediately when charging stops.

It does not allow the app to access photographs, messages, accounts or network data. The permission can be removed through Android's device-administrator settings.
