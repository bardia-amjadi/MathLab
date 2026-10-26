# Building MathLab

## Option A: GitHub Actions (no Android Studio, no installs)

1. Create a free GitHub account and a new repository. A public repository has unlimited free build minutes.
2. Upload the contents of the project folder to the repository. Make sure the hidden `.github` folder is included.
   - Drag-and-drop in the browser can skip hidden folders. If `.github/workflows/build-apk.yml` is missing, choose **Add file → Create new file**, type `.github/workflows/build-apk.yml` as the name, and paste in the workflow contents.
   - Or use git: `git init && git add -A && git commit -m "MathLab" && git remote add origin <url> && git push -u origin main`
3. Open the **Actions** tab. If asked, click to enable workflows.
4. Select **Build APK** on the left, then **Run workflow**. Pushing to `main` also starts it.
5. Wait about 5 to 10 minutes. When the run is green, scroll to **Artifacts** and download **PowerMath-apk**.
6. Unzip it. Copy `app-release.apk` to your phone, allow **Install unknown apps** for your file manager or browser, and open the file.

The release build is signed with the debug key, which is fine for personal use. Use your own signing config before publishing to a store.

### If the run fails

The Android layer has never been compiled, so a first-run error is possible.

1. Open the failed run and the failed step (usually **Build release APK** or **Unit tests**).
2. Copy the lines containing `e:` (Kotlin errors) or `error:`.
3. Fix those files and push again, or send the lines to whoever maintains the code.

## Option B: Android Studio

1. Install Android Studio (Ladybug or newer) and JDK 17+.
2. Open the project folder and let Gradle sync. It downloads Gradle 8.9 and Android SDK 35.
3. Run tests: `./gradlew :core:test :app:testDebugUnitTest`
4. Debug APK: `./gradlew :app:assembleDebug` gives `app/build/outputs/apk/debug/app-debug.apk`
5. Release APK: `./gradlew :app:assembleRelease` gives `app/build/outputs/apk/release/app-release.apk`

## Option C: Command line only (no Android Studio)

1. Install JDK 17 and the Android command-line tools.
2. Accept licenses and install SDK 35: `sdkmanager --licenses` then `sdkmanager "platforms;android-35" "build-tools;35.0.0"`
3. Set `ANDROID_HOME` to the SDK folder.
4. Run `./gradlew :app:assembleRelease`

## Installing on the phone

- Android 8.0 (API 26) or newer is required.
- Enable installing apps from the source you opened the APK with (Settings → Apps → Special access → Install unknown apps).
