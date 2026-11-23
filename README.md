# ScrollNumb

ScrollNumb is a free Android app for noticing how much you scroll through short videos. Choose a daily limit, see your progress, and use the awareness prompt to take a break or keep scrolling.

Counting runs on the phone through an Android accessibility service that you enable during setup. The app stores counts, settings, streaks, and XP locally. Clearing app storage removes that progress. The offline edition requires no subscription, account, or backend.

## Build from source

Install Android SDK Platform 36 and use JDK 21. Open the project in Android Studio or run the following from the repository root.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

On Windows, use `gradlew.bat`. The debug APK is written to `app/build/outputs/apk/debug/`. Downloading build dependencies requires internet access.

## License

The project source code is available under the MIT license. Third-party components and platform trademarks belong to their respective owners.
