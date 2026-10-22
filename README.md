# Kaleido

A fluid, Material 3 photo gallery for Android. Browse by day or album, star favourites,
make quick edits, and share or delete photos.

- Monet (wallpaper) colours on Android 12+, plus light, dark and system themes
- Multi-select with share, star and delete
- Basic editor: rotate, flip, brightness, contrast and saturation (saves a copy)
- Built with Kotlin, Jetpack Compose, Material 3 and Coil

Min Android 8.0 (API 26). Release APKs are built for arm64-v8a and armeabi-v7a.

## Build

    ./gradlew :app:assembleRelease

Without a release keystore, local builds fall back to the debug key.

## License

MIT. See [LICENSE](LICENSE).
