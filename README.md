# Coffeehouse

Coffeehouse brings Sony's "Background Music" listening mode to the WH-1000XM4.

The app applies Android audio effects to simulate the spacious, speaker-like presentation available in newer Sony headphone models.

## Features

- Background Music style audio processing
- Multiple presets
- Custom preset creation
- Automatic startup after reboot
- Persistent settings using DataStore
- Modern Jetpack Compose UI

## Tech Stack

- Kotlin
- Jetpack Compose
- Android SDK 36
- WorkManager
- DataStore
- Coroutines

## Requirements

- Android 12+ (API 31)
- Sony WH-1000XM4 headphones

## Building

### Android Studio

Open the project and run:

```bash
./gradlew assembleDebug
