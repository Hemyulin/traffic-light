# Traffic Light

Traffic Light is a watch-first Wear OS mood check-in app for Samsung Galaxy Watch.

The first screen is intentionally tiny: it asks "What's up?", shows green, yellow,
and red at the same time, saves one tap with a timestamp, then closes.

## Current shape

- Native Android/Wear OS app in `android/`
- Kotlin Activity with no external UI framework dependency yet
- Local timestamped mood storage on the watch
- Wear OS Data Layer sync from watch to Android phone
- Android phone companion app for synced stats
- Inexact reminder notification every few hours
- Boot receiver to restore reminders after restart

## Build

Open `android/` in Android Studio.

- Run `app` on the Wear OS watch.
- Run `mobile` on the Android phone.

From the command line:

```sh
cd android
./gradlew :app:assembleDebug :mobile:assembleDebug
```

Debug APKs:

- Watch: `android/app/build/outputs/apk/debug/app-debug.apk`
- Phone: `android/mobile/build/outputs/apk/debug/mobile-debug.apk`

## Product notes

V1 should stay watch-first:

- The check-in interaction must never require scrolling.
- The three choices should remain one tap each.
- The phone companion can be added later for stats, sync, and longer reflection.
- Reminder frequency should become configurable once the basic loop feels right.
