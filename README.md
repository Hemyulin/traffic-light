# Traffic Light

Traffic Light is a watch-first Wear OS mood check-in app for Samsung Galaxy Watch.

The first screen is intentionally tiny: it asks "What's up?", shows green, yellow,
and red at the same time, saves one tap with a timestamp, then closes.

## Current shape

- Native Android/Wear OS app in `android/`
- Kotlin Activity with no external UI framework dependency yet
- Local timestamped mood storage on the watch
- Inexact reminder notification every few hours
- Boot receiver to restore reminders after restart

## Build

Open `android/` in Android Studio, choose a Wear OS emulator or connected watch,
then run the `app` configuration.

From the command line:

```sh
cd android
./gradlew :app:assembleDebug
```

## Product notes

V1 should stay watch-first:

- The check-in interaction must never require scrolling.
- The three choices should remain one tap each.
- The phone companion can be added later for stats, sync, and longer reflection.
- Reminder frequency should become configurable once the basic loop feels right.
